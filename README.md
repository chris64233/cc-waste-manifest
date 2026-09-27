# cc-waste-manifest

管理危险废物批次和转运交接记录。

## 主要业务规则

- **联单创建**：每张联单有唯一联单号（重复创建返回 409），记录产生方、指定承运方、指定处置方和多条废物明细；每条明细包含废物类别、包装数量和申报重量。重量使用 `BigDecimal`（精度 19、小数 3 位）且必须为正，申报总重由明细累加得出。
- **交接顺序**：严格按 产生方交运 → 承运方接货 → 处置方收货 推进。每一步由预期角色提交唯一事件号、称重值和发生时间；前一步未完成、角色不符或发生时间早于上一事件（时间倒序）都会被拒绝（422）。
- **重量差异处置**：处置方收货称重与申报总重的偏差在允许比例内（默认 5%，可用 `manifest.weight-tolerance-ratio` 配置）时联单直接完成；超过比例则进入重量争议状态，争议期间禁止任何交接操作。
- **争议解决**：产生方和处置方须分别提交争议确认事件，两方最新确认的修正重量一致时联单以该重量完成；意见不一致时保持争议。同一方可用新事件号重新确认，以最新一次为准。
- **幂等与不可变**：交接与争议确认事件落库后不可修改（事件号全局唯一）。相同事件号重放相同内容返回当前结果（幂等）；相同事件号但内容不同返回 409 冲突。
- **并发与事务**：联单状态、当前保管方、重量与事件在同一事务内更新，联单行采用悲观写锁串行化并发交接，配合事件号唯一约束兜底，并发重复或矛盾操作不会跳过状态或产生两个有效结果；任何失败整体回滚，不留部分记录。

## 差错更正与监管复核

已完成联单不能直接编辑，所有修改通过更正申请驱动，生效后一次性生成新的联单版本。

- **更正申请**：仅 `COMPLETED` 状态的联单可发起。申请需给出更正号、申请方角色（产生方/承运方/处置方，监管方不能作为申请人）、理由、证据引用，以及一组字段变更（`PACKAGE_COUNT` 包装数量 / `WEIGHT` 申报重量 / `WASTE_CATEGORY` 废物类别，均按明细序号定位）。原始值由服务端从基线版本快照取得并保存，不采信调用方传入的旧值；新值与原始值相同、明细序号不存在、数值非法都会被拒绝（422）。申请时自动计算对原争议结论的影响（`disputeImpact`）：原在容差内但按修正口径本应进入争议（`WOULD_HAVE_ENTERED_DISPUTE`）、原进入争议但按修正口径本可避免（`DISPUTE_WOULD_HAVE_BEEN_AVOIDED`）、废物类别变化（`WASTE_CATEGORY_CHANGED`）或无影响（`NONE`）。
- **版本模型**：联单完成时固化版本 1 快照（含明细、申报总重、实收重量、最终重量）。更正生效时以基线版本为基础套用变更，一次性生成新版本并置为唯一 `EFFECTIVE`，旧版本置为 `SUPERSEDED`；所有版本内容不可修改，联单原始行与交接事件保持不可修改。
- **确认流程**：产生方、承运方、处置方按各自涉及字段确认——包装数量需三方确认，重量与废物类别需产生方和处置方确认；涉及废物类别变化时还必须监管方复核（`REGULATOR`）。每个角色只能决定一次，决定事件（`APPROVED`/`REJECTED`/`WITHDRAWN`）落库不可修改。任何必要方拒绝即终止（`REJECTED`）；仅申请方可撤回（`WITHDRAWN`）；所有必要决定齐备（全部 `APPROVED`）才一次性生效（`EFFECTIVE`）。
- **单活动更正**：同一联单同时只能有一笔确认中（`PENDING`）的更正，由数据库唯一约束（活动守卫列）兜底，联单行锁串行化并发创建。
- **不得落地**：确认期间出现监管冻结（`POST /freeze`，冻结时点所有确认中更正一并终止为 `FROZEN`）、参与方撤回/拒绝，或基线版本被其他更正取代（`SUPERSEDED`）时，基于旧版本的更正不得落地；落地前在版本行锁下再次校验冻结状态与基线版本号。
- **幂等**：更正号与决定事件号均全局唯一。同号同内容重放返回当前结果；同号异内容返回 409 冲突。
- **查询**：`GET /record` 一次返回当前有效版本、全部版本、相邻版本差异、更正审批时间线（含每项决定事件）与交接事件时间线；也可单独查询版本列表、任意两版本差异和单笔更正详情。

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/manifests` | 创建联单（含废物明细） |
| GET | `/api/manifests/{manifestNo}` | 联单详情（状态、当前保管方、重量、明细、当前版本号、冻结标记） |
| GET | `/api/manifests/{manifestNo}/timeline` | 完整审计时间线（全部交接/争议事件） |
| POST | `/api/manifests/{manifestNo}/handover` | 交接事件（`role` 为 GENERATOR/TRANSPORTER/DISPOSER） |
| POST | `/api/manifests/{manifestNo}/dispute-confirmations` | 争议确认（仅 GENERATOR/DISPOSER） |
| POST | `/api/manifests/{manifestNo}/corrections` | 发起差错更正（仅已完成联单） |
| GET | `/api/manifests/{manifestNo}/corrections/{correctionNo}` | 更正详情（变更项、决定事件、影响评估） |
| POST | `/api/manifests/{manifestNo}/corrections/{correctionNo}/decisions` | 提交更正决定（三方确认 / 监管复核 / 撤回） |
| POST | `/api/manifests/{manifestNo}/freeze` | 监管冻结（终止所有确认中更正，禁止新更正） |
| GET | `/api/manifests/{manifestNo}/versions` | 全部联单版本（含当前有效版本） |
| GET | `/api/manifests/{manifestNo}/versions/diff?from=1&to=2` | 两版本间字段差异 |
| GET | `/api/manifests/{manifestNo}/record` | 完整档案：当前有效版本 + 版本差异 + 审批时间线 + 交接时间线 |

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test
