# cc-waste-manifest

管理危险废物批次和转运交接记录，并支持联单完成后的差错更正与监管复核。

## 主要业务规则

- **联单创建**：每张联单有唯一联单号（重复创建返回 409），记录产生方、指定承运方、指定处置方和多条废物明细；每条明细包含废物类别、包装数量和申报重量。重量使用 `BigDecimal`（精度 19、小数 3 位）且必须为正，申报总重由明细累加得出。创建时生成第 1 版版本快照，原始版本此后不可修改。
- **交接顺序**：严格按 产生方交运 → 承运方接货 → 处置方收货 推进。每一步由预期角色提交唯一事件号、称重值和发生时间；前一步未完成、角色不符或发生时间早于上一事件（时间倒序）都会被拒绝（422）。
- **重量差异处置**：处置方收货称重与申报总重的偏差在允许比例内（默认 5%，可用 `manifest.weight-tolerance-ratio` 配置）时联单直接完成；超过比例则进入重量争议状态，争议期间禁止任何交接操作。
- **争议解决**：产生方和处置方须分别提交争议确认事件，两方最新确认的修正重量一致时联单以该重量完成；意见不一致时保持争议。同一方可用新事件号重新确认，以最新一次为准。
- **幂等与不可变**：交接与争议确认事件落库后不可修改（事件号全局唯一）。相同事件号重放相同内容返回当前结果（幂等）；相同事件号但内容不同返回 409 冲突。
- **并发与事务**：联单状态、当前保管方、重量与事件在同一事务内更新，联单行采用悲观写锁串行化并发交接，配合事件号唯一约束兜底，并发重复或矛盾操作不会跳过状态或产生两个有效结果；任何失败整体回滚，不留部分记录。

## 差错更正与监管复核

- **版本化联单**：已完成联单不能直接编辑。联单内容以版本快照保存（v1 为原始版本），每次更正落地一次性生成新的有效版本；原版本、交接事件与争议确认事件保持不可变。联单详情始终返回当前有效版本。
- **更正申请**：仅 `COMPLETED` 状态的联单可发起更正。申请需给出唯一更正号、理由、证据以及一组字段级修改（按明细序号定位，支持包装数量 `PACKAGE_COUNT`、重量 `WEIGHT`、废物类别 `WASTE_CATEGORY`），服务端保存每个字段的原始值与新值。同一明细同一字段重复修改、新值与原值相同、明细序号越界、数值格式非法都会被拒绝（422）。
- **争议影响评估**：创建更正时按更正后的申报总重重新比对实收重量与容差，计算对原争议结论的影响（`CONCLUSION_UNCHANGED` / `DISPUTE_WOULD_NOT_HAVE_OCCURRED` / `DISPUTE_WOULD_HAVE_OCCURRED` / `DISPUTE_WOULD_REMAIN`），随更正单持久化。
- **多方确认**：各参与方按涉及字段确认——包装数量需产生方 + 承运方，重量需产生方 + 处置方，废物类别需产生方 + 处置方并追加监管复核（`REGULATOR`）。同一方以最新决定为准；所有必要角色均批准后，在同一事务内原子生成新版本并生效。任何一方 `REJECT` 则更正驳回，任何一方 `WITHDRAW`（撤回）则更正失效，均不得再落地。
- **单一活动更正**：同一联单同时只能有一笔 `PENDING` 更正（冲突返回 409）；更正终结（生效/驳回/失效）后才可发起下一笔，后一笔基于新的当前版本。
- **监管冻结**：`POST /freeze` 将联单置为冻结态并使所有待确认更正立即失效；冻结期间禁止交接、争议确认、发起或确认更正（422）；`POST /unfreeze` 解除。确认期间若出现监管冻结、参与方撤回或基准版本已被其他版本取代，基于旧版本的更正不得落地（决定提交时重新校验并自动失效）。
- **更正幂等**：更正号与决定事件号全局唯一。同号同内容重放返回当前结果；同号异内容返回 409 冲突。决定事件落库后不可修改。
- **查询**：`GET /revisions` 一次返回当前有效版本号、全部版本（含相邻版本差异 `diffFromPrevious`）以及所有更正单及其完整审批时间线（决定事件按落库顺序）。

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/manifests` | 创建联单（含废物明细，生成 v1 版本） |
| GET | `/api/manifests/{manifestNo}` | 联单详情（当前有效版本、状态、重量、明细、冻结标志） |
| GET | `/api/manifests/{manifestNo}/timeline` | 完整审计时间线（全部交接/争议事件） |
| GET | `/api/manifests/{manifestNo}/revisions` | 当前有效版本 + 版本差异 + 更正审批时间线 |
| POST | `/api/manifests/{manifestNo}/handover` | 交接事件（`role` 为 GENERATOR/TRANSPORTER/DISPOSER） |
| POST | `/api/manifests/{manifestNo}/dispute-confirmations` | 争议确认（仅 GENERATOR/DISPOSER） |
| POST | `/api/manifests/{manifestNo}/freeze` | 监管冻结（使待确认更正失效） |
| POST | `/api/manifests/{manifestNo}/unfreeze` | 解除监管冻结 |
| POST | `/api/manifests/{manifestNo}/corrections` | 发起差错更正（更正号幂等） |
| GET | `/api/manifests/{manifestNo}/corrections/{correctionNo}` | 更正详情（字段修改、争议影响、审批时间线） |
| POST | `/api/manifests/{manifestNo}/corrections/{correctionNo}/decisions` | 提交确认决定（`role` 含 REGULATOR；`decision` 为 APPROVE/REJECT/WITHDRAW，决定事件号幂等） |

### 更正申请示例

```json
POST /api/manifests/M-001/corrections
{
  "correctionNo": "CR-001",
  "reason": "申报重量填写错误",
  "evidence": "称重单 WS-1001",
  "changes": [
    {"itemIndex": 0, "field": "WEIGHT", "newValue": "140"},
    {"itemIndex": 1, "field": "PACKAGE_COUNT", "newValue": "8"}
  ]
}
```

### 确认决定示例

```json
POST /api/manifests/M-001/corrections/CR-001/decisions
{
  "decisionNo": "CD-001",
  "role": "GENERATOR",
  "decision": "APPROVE",
  "occurredAt": "2026-09-27T08:00:00Z"
}
```

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
