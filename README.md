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

## 运输途中异常处置（泄漏 / 遗失 / 改道）

承运方在货物保管期间可对运输中的当前有效联单登记异常，并以确认驱动的处置方案完成包装拆分；任何终止路径都不得留下半条运输链。

- **异常登记**：仅联单处于承运方保管期间（`IN_TRANSIT` / `RECEIVED_BY_TRANSPORTER`）可由承运方登记，需给出全局唯一事件号、异常类型（`LEAK` 泄漏 / `LOSS` 遗失 / `DIVERSION` 改道）、发生时间、地点、证据引用，以及按明细序号定位的受影响包装及数量（不得超过该明细当前包装总数）。异常只能引用登记时的当前有效版本（`baseVersionNo`）。同号同内容重放幂等，同号异内容返回 409。一张联单同时只能有一笔未处置完成（`OPEN`）的异常（数据库唯一守卫兜底）；存在确认中的更正或监管已冻结时拒绝登记（422）。
- **冻结交接**：泄漏或遗失登记即冻结受影响包装；异常 `OPEN` 期间（方案一次性生效前）联单不能继续正常交接（422），受影响包装不得流向处置方收货。
- **处置方案确认**：承运方提出方案（唯一方案号）。改道**必须**指定新处置方（不得与原处置方相同）和预计到达时间；泄漏/遗失不得指定这两项。方案按各方责任确认：产生方、承运方、原处置方必须确认，改道还需新处置方确认；申报涉及废物类别变化（`categoryChanged`）或数量变化（`quantityChanged`）时还必须监管方（`REGULATOR`）决定。每个责任方只能决定一次；任一责任方 `REJECTED` 即终止，仅承运方可 `WITHDRAWN`，决定事件号全局唯一、同号同内容幂等、异内容 409。一笔异常同时只能有一个确认中的方案。
- **一次性拆分生效**：全部必要确认齐备时，在同一事务内先对当前有效版本完成全部校验，再一次性生成包装拆分记录：每个受影响明细记录拆分前总数、受影响数量、未受影响数量。未受影响部分继续沿原联单流转（方案生效后即可恢复原链交接）；受影响部分在泄漏/遗失时形成与原联单相连的**损失记录**，在改道时形成与原联单相连的**新运输段**（承载受影响包装运往新处置方，拥有独立的 承运方发运→新处置方收货 交接链，事件号同样全局唯一、不可变）。
- **不得留下半条运输链**：确认期间任一确认失效（拒绝/撤回）、联单版本已变化（方案基于的版本不再是当前有效版本）、或监管冻结后来生效（`POST /freeze` 同时终止所有确认中方案为 `FROZEN`），方案终止（`REJECTED`/`WITHDRAWN`/`SUPERSEDED`/`FROZEN`），异常保持 `OPEN`、包装保持冻结，绝不产生任何拆分行、运输段或损失记录；落地前在联单行锁下再次校验冻结标记与版本号。
- **与差错更正并发**：异常处置与联单差错更正基于同一版本并发时，只能有一个操作成功——存在 `OPEN` 异常（包装去向尚未落定）时不得发起更正；存在确认中更正时不得登记异常；确认中方案发现联单版本已被更正推进则终止为 `SUPERSEDED`，后来操作必须读取新版本重新计算，不能覆盖已经确认的包装去向或争议结论。联单行悲观写锁 + 事件/方案号唯一约束兜底并发。
- **查询**：`GET /incidents`、`GET /incidents/{eventNo}`（含证据、受影响包装、方案与各方决定）、`GET /incidents/{eventNo}/plans/{planNo}`（含拆分前后数量与去向）、`GET /segments`（新运输段及其交接链）、`GET /segments/{segmentNo}/timeline`、`GET /losses`（损失记录及包装）；`GET /incident-record` 在完整联单档案之上汇总异常证据、各方（含监管）决定、包装拆分前后关系、新运输段/损失记录与完整交接链。

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
| POST | `/api/manifests/{manifestNo}/incidents` | 登记运输异常（泄漏/遗失/改道，冻结受影响包装） |
| GET | `/api/manifests/{manifestNo}/incidents` | 异常列表（证据、受影响包装、方案与决定） |
| GET | `/api/manifests/{manifestNo}/incidents/{eventNo}` | 单笔异常详情 |
| POST | `/api/manifests/{manifestNo}/incidents/{eventNo}/plans` | 提出处置方案（改道须含新处置方与预计到达时间） |
| GET | `/api/manifests/{manifestNo}/incidents/{eventNo}/plans/{planNo}` | 方案详情（各方决定、包装拆分前后关系、去向） |
| POST | `/api/manifests/{manifestNo}/incidents/{eventNo}/plans/{planNo}/decisions` | 方案确认（四方责任确认 / 监管决定 / 承运方撤回） |
| GET | `/api/manifests/{manifestNo}/segments` | 改道产生的新运输段 |
| POST | `/api/manifests/{manifestNo}/segments/{segmentNo}/handover` | 新运输段交接（承运方发运 / 新处置方收货） |
| GET | `/api/manifests/{manifestNo}/segments/{segmentNo}/timeline` | 新运输段交接链 |
| GET | `/api/manifests/{manifestNo}/losses` | 泄漏/遗失损失记录 |
| GET | `/api/manifests/{manifestNo}/incident-record` | 异常处置完整档案（含完整联单档案、证据、决定、拆分、监管处理与交接链） |

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
