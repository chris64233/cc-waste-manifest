# cc-waste-manifest

管理危险废物批次和转运交接记录。

## 业务规则

### 联单与废物明细

- 创建联单时记录唯一联单号、产生方、指定承运方、指定处置方以及多条废物明细。
- 每条明细包含废物类别、包装数量（正整数）和申报重量。
- 所有重量使用 `BigDecimal`，固定 3 位小数精度且必须为正数；超过精度或非正数一律拒绝（HTTP 400）。
- 联单号有数据库唯一约束，重复创建返回 409。

### 交接顺序与状态机

交接必须严格按照下列顺序提交，前一步未完成、提交角色与预期角色不符、或事件发生时间未严格晚于上一事件，均返回 409：

1. `GENERATOR_HANDOVER` 产生方交运（状态仍为 `CREATED`，保管方：产生方）
2. `CARRIER_PICKUP` 承运方接货（状态 `IN_TRANSIT`，保管方：承运方）
3. `DISPOSER_RECEIPT` 处置方收货
   - 处置方称重与申报总重的差异 ≤ `申报总重 × 允许比例`（默认 5%，可按联单覆盖）：联单 `COMPLETED`，保管方：处置方。
   - 差异超过允许比例：联单进入 `DISPUTED`，阻止完成。

### 重量争议

- 争议中，产生方提交 `GENERATOR_CONFIRM`、处置方提交 `DISPOSER_CONFIRM`，各自给出修正重量。
- 以两方**各自最新一次**确认重量为准：只有两者完全相等时联单才 `COMPLETED`，并记录该重量为最终重量。
- 两方意见不一致，或任一方尚未确认时，联单保持 `DISPUTED`，不会跳过状态。

### 事件不可变、幂等与并发

- 交接与争议确认事件一经写入不可修改；`event_no` 全局唯一（数据库唯一约束）。
- 相同事件号携带完全相同内容（联单、类型、角色、重量、发生时间）重放时幂等返回，响应中 `replayed=true`，不会生成第二条事件。
- 相同事件号但内容不同返回 409。
- 同一联单上的事件通过悲观写锁（`PESSIMISTIC_WRITE`）串行化，叠加乐观锁版本与唯一约束：并发重复交接只会推进一次状态，相互矛盾的争议确认不可能产生两个有效结果或提前完成。
- 联单状态、当前保管方、各环节重量与事件写入在同一个数据库事务内完成，任何一步失败整体回滚，不留部分记录。

### 查询

- `GET /api/manifests/{manifestNo}` 返回联单详情（状态、当前保管方、申报总重、各环节称重、废物明细等）。
- `GET /api/manifests/{manifestNo}/timeline` 返回按序号排列的完整审计时间线。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/manifests` | 创建联单 |
| GET | `/api/manifests/{manifestNo}` | 联单详情 |
| GET | `/api/manifests/{manifestNo}/timeline` | 审计时间线 |
| POST | `/api/manifests/{manifestNo}/events` | 提交交接 / 争议确认事件 |

创建联单请求示例：

```json
{
  "manifestNo": "M-2026-0001",
  "generatorParty": "GEN-001",
  "carrierParty": "CAR-001",
  "disposerParty": "DIS-001",
  "weightToleranceRatio": 0.05,
  "items": [
    {"category": "HW08", "packageCount": 3, "declaredWeight": "60.000"},
    {"category": "HW49", "packageCount": 2, "declaredWeight": "40.000"}
  ]
}
```

提交事件请求示例：

```json
{
  "eventNo": "EVT-0001",
  "eventType": "CARRIER_PICKUP",
  "actorParty": "CAR-001",
  "weightValue": "100.200",
  "occurredAt": "2026-09-24T02:00:00Z"
}
```

事件类型：`GENERATOR_HANDOVER`、`CARRIER_PICKUP`、`DISPOSER_RECEIPT`、`GENERATOR_CONFIRM`、`DISPOSER_CONFIRM`。
错误响应：400 请求不合法、404 联单不存在、409 状态/角色/时序冲突或幂等内容冲突。

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
