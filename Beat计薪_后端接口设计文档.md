# Beat计薪 HarmonyOS App 后端接口设计文档

| 项目 | 内容 |
|---|---|
| 文档版本 | V1.1（定稿，与需求文档 V1.1 对应） |
| 编写日期 | 2026-11-21 |
| 后端技术 | Java Spring Boot + MySQL（Redis 第一版非必需） |
| 接口前缀 | `/api/v1` |
| 对应需求 | 《Beat计薪 HarmonyOS App 软件需求规格说明书》（接口的"关联需求"列与之编号一致） |
| Apifox | 同目录 `beat-openapi.json`（OpenAPI 3.0.3），Apifox → 项目设置 → 导入数据 → OpenAPI/Swagger |

**修订记录**：V0.1 初稿（2026-11-19）；V0.2（2026-11-20）移除 `DeviceInfo.pushToken`（后台提醒走本地代理提醒，不需要云端推送），新增第 4.6 节说明提醒与接口的关系；V0.3（2026-11-20）按官方核实结果调整：登录请求去掉 `idToken`、用户信息去掉头像；`API-CFG-01` 节假日接口改为二批（本期使用系统 HolidayManager）；Token 本地存储定为 Asset Store Kit。V0.5（2026-11-21）依据第三轮核实：账号唯一键改为 **UnionID**；授权码一次性、失败不可重试；`UserInfo` 恢复可空的 `avatarUrl`；节假日改用 App 内置 JSON，`API-CFG-01` 二批优先；注销冷静期 7 天且 15 个工作日内删除。V0.6（2026-11-21）10.4 定稿：他端可结束/取消会话但不可远程暂停/继续，新增错误码 `50006`。V1.0（2026-11-21）定稿：所有【待确认】项按推荐方案定稿，文中不再有【待确认】，仅保留联调时验证的【待实测】项。V1.1（2026-11-23）补充 PRV-009：新增 `API-SYNC-04~06`（云同步同意查询、开启/撤回、清除云端数据）与错误码 `20009`，`LoginResult` 增加 `syncConsented`，业务数据接口统一校验云同步同意。

## 0. 阅读指引：阶段标记

| 标记 | 含义 |
|---|---|
| **【本期】** | 第一版（V1.0）开发 |
| **【后续·二批】** | 已设计、已预留编号，本期**不开发** |
| 远期功能（目标独立记账、跨天拆分、多套计薪方案等） | 仅在需求文档记录，**本文档不设计接口**，数据模型已预留扩展空间 |

接口总数：本期 32 个，二批预留 5 个。所有接口的标题行与 Apifox 中的 summary 都带阶段前缀。

本版已无【待确认】项（均已定稿，见需求文档第 10 章）；**【待实测】** = 联调时需验证的外部细节，见第 10 章。

---

## 1. 通用约定

### 1.1 基础

- 协议：**仅 HTTPS**；请求/响应 `Content-Type: application/json; charset=utf-8`（上传接口除外）。
- 完整 URL：`https://{host}/api/v1{path}`。
- 字符串 ID：业务记录 ID 均为 UUID；用户 ID 以字符串返回（避免整数精度问题）。

### 1.2 请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization: Bearer <accessToken>` | 除登录/续期外必填 | Beat计薪自有 Access Token |
| `Idempotency-Key: <UUID>` | 标注"幂等"的 POST 必填 | 见 1.6；`/sync/push` 同时在 body 中有 `requestId`，二者应一致 |
| `X-App-Version: 1.0.0` | 建议 | 便于排查与灰度 |
| `Accept-Language: zh-CN` | 可选 | 错误信息语言 |

### 1.3 统一响应

所有接口返回 HTTP 状态码 + 统一包装：

```json
{ "code": 0, "message": "success", "data": { }, "requestId": "7f9c2ba4e88f", "timestamp": 1795075800000 }
```

- `code = 0` 表示成功；非 0 为业务错误码（第 7 章）。
- 成功时 HTTP 状态码为 200/201；失败时为 4xx/5xx，响应体同样为上述结构，`data` 通常为 `null`（个别错误携带上下文，如 `50001` 携带 `currentSession`）。
- 无返回内容的成功接口 `data` 为 `null`。

### 1.4 时间、金额、时长

| 类型 | 约定 |
|---|---|
| 时间点 | **毫秒级 Unix 时间戳（UTC）**，字段名 `xxxAt`，类型 int64 |
| 归属日期 | `workDate` 等为 `YYYY-MM-DD` 字符串，由客户端按设备时区得出（跨天记录归属开始日期） |
| 月份 | `YYYY-MM` |
| 金额/费率 | **整数"分"**（费率为"分/小时"），字段名 `xxxCents` |
| 时长 | 毫秒，字段名 `xxxMs` |
| 舍入 | 片段金额 `round_half_up(durationMs × rateCentsPerHour ÷ 3,600,000)`；记录总额为片段之和（已定稿） |

### 1.5 分页、排序、筛选、时间范围

- 分页：`page`（从 1 起，默认 1）、`size`（默认 20，最大 100）。返回 `{ items, page, size, total, hasNext }`。
- 排序：`sort=字段,方向`（方向 `asc|desc`），仅允许各接口声明的字段，非法字段返回 `10001`。
- 筛选：见各接口参数；标签可重复传（`tag=a&tag=b`，OR 关系）。
- 时间范围：按**归属日期** `dateFrom/dateTo`（闭区间，`YYYY-MM-DD`）；月度类用 `monthFrom/monthTo`。范围过大（>366 天）返回 `10001`。
- 默认不返回软删除数据（`includeDeleted=false`）。
- 增量数据请使用同步接口（游标），不要用分页轮询。

### 1.6 幂等机制

| 层级 | 机制 | 说明 |
|---|---|---|
| 请求级 | `Idempotency-Key` / `requestId` | 服务端在 `beat_sync_receipt` 记录（用户ID + 键 + 请求摘要 + 响应），保留 ≥ 7 天。同键同内容重复提交 → 直接返回首次响应；同键**不同内容** → `422 / 10004` |
| 变更级 | `changeId`（同步） | 同一变更重复提交逐条返回 `DUPLICATE` |
| 资源级 | 客户端生成的 UUID：`sessionId`、`migrationId`、`record.id` | 重复创建返回已有资源，不产生重复记录 |
| 批次级 | `batchNo`（迁移） | 重复批号返回上次结果 |

### 1.7 版本号与乐观并发

- 每个可同步实体都有 `version`（服务端单调递增整数，新建为 1）。
- 客户端 Push 时带 `baseVersion`（客户端上次已知的服务端版本，新建为 0）。`baseVersion == 服务端当前 version` → 直接接受；否则视为冲突，按第 3 章规则处理。
- 计时会话可选携带 `expectedVersion`，不一致返回 `409 / 50002`。

### 1.8 软删除与同步游标

- 删除一律为**软删除**（`deleted=true` 墓碑 + 产生 `op=DELETE` 的同步变更），保证删除能同步到其他设备。墓碑保留 90 天，超期清理后若客户端游标过旧，Pull 返回 `fullSyncRequired=true`。
- 游标 `cursor` 来源于 `beat_sync_change` 的单调递增序号，对客户端是**不透明字符串**。客户端**必须在变更成功写入本地库后**才保存新游标。

### 1.9 接口认证与数据权限

1. 除 `/auth/huawei/login`、`/auth/token/refresh` 外，所有接口必须携带有效 Access Token。
2. 服务端**只从认证上下文取 userId**；请求体/参数中的 userId 一律忽略（本文档接口也不设计 userId 字段）。
3. 所有查询与写入都带 `user_id` 条件；访问他人资源统一返回 `404 / 30002`（避免泄露资源存在性），不返回 403。
4. 设备会话被撤销后，该设备所有 Token 立即失效（`20007`）。
5. **云同步同意校验**：账号管理类接口（登录、续期、退出、用户信息、注销）与 `/sync/consent`、`/sync/data` 之外，所有业务数据接口（同步、迁移、计时、设置/记录/目标/排班/台账/通知查询、设备管理）在账号未同意云同步、或同意的隐私政策版本不是当前版本时返回 `403 / 20009`（需求 PRV-010）。
6. 账号处于 `PENDING_DELETION` 时，除"登录（撤销注销）"外的业务接口返回 `403 / 20008`。

---

## 2. 认证与令牌

### 2.1 两套身份，不要混用

| | 华为账号授权 | Beat计薪 Token |
|---|---|---|
| 作用 | 证明"你是谁"（身份认证） | 访问 Beat计薪 业务接口 |
| 由谁签发 | 华为 | Beat计薪后端 |
| 客户端用途 | 仅用于提交给 `/auth/huawei/login` | 每次请求放 `Authorization` 头 |
| 是否可直接当业务 Token | **不可以** | — |

### 2.2 Token 规则

| 项 | Access Token | Refresh Token |
|---|---|---|
| 用途 | 业务接口鉴权 | 换取新的 Access Token |
| 有效期（已定稿） | 30 分钟 | 30 天（滚动，每次续期重新计时） |
| 服务端存储 | 无状态签名（JWT）；含 userId、sessionId、deviceId；吊销依赖 sessionId 校验 | 仅存**安全哈希**（`sys_user_session`），不存明文 |
| 轮换 | — | 每次续期签发新 Refresh Token，旧的立即作废；旧 Token 重放 → 吊销该会话（`20005`） |
| 客户端存放 | **Asset Store Kit**（不得明文放 Preferences） | 同左 |

### 2.3 两层静默登录（独立设计，不混为一个接口）

```
启动
 ├─ 有 Refresh Token？ ──是──> POST /auth/token/refresh ──成功──> 登录态恢复（第一层，Beat Token 续期）
 │                                       └─失败(20004/20005)
 └─ 否/失败 ──> 尝试 Account Kit 华为账号静默授权（第二层，客户端能力，不保证成功）
                  ├─成功──> POST /auth/huawei/login {loginType: SILENT}
                  └─失败/未授权/环境不满足──> 回退交互式授权 ──> POST /auth/huawei/login {loginType: INTERACTIVE}
                                              用户不授权 ──> 保持游客模式，本地数据照常可用
```

- **第一层**对应后端接口 `API-AUTH-02`；**第二层**是客户端调用 Account Kit，后端无独立接口，仅在 `API-AUTH-01` 中用 `loginType` 区分以便统计。
- 不承诺每次启动都能静默登录成功。

### 2.4 登录后的客户端分支

`LoginResult.hasCloudData` 与本机游客数据决定下一步：

| 本机游客数据 | `hasCloudData` | 动作 |
|---|---|---|
| 无 | 是/否 | 直接 `GET /sync/pull`（cursor 为空）全量拉取 |
| 有 | 是/否 | 走迁移：`/migrations` → `batches` → `commit` → Pull |

**云同步同意是登录之后的第二道门**：`LoginResult.syncConsented=false` 时，客户端不调用任何业务数据接口，保持本地模式；用户在“设置 > 账号与云同步”开启时，先弹出单独同意弹窗（PRV-004），调用 `PUT /sync/consent`，成功后再按上表走迁移或 Pull。

---

## 3. 同步机制

### 3.1 设计取舍：为什么业务数据"只读 + 同步写"

| 方案 | 优点 | 缺点 |
|---|---|---|
| A. 每个实体提供 REST 增删改（直连写） | 直观，Postman 好调试 | 与本地优先冲突：离线修改需另做队列；每个实体各一套并发/冲突/幂等逻辑；容易出现两条写路径版本不一致；接口数量多 |
| **B. 写入统一走 `/sync/push`（推荐，已采用）** | 一套幂等、版本、冲突、墓碑规则；天然支持离线队列与批量；接口少 | 单个资源不能"裸调"CRUD；需要读取接口做查看与对账 |

因此：**新增、修改、删除、批量操作 = 客户端本地操作 + Push 变更**；`/work-records`、`/goals` 等只提供**只读查询**。计时结束例外：`/timer/sessions/{id}/stop` 同事务落库记录（需要与互斥状态一起原子完成）。若后续有 Web 管理端需要直连写，再在二批评估。

### 3.2 同步实体与载荷

| `entityType` | `entityId` | 载荷 schema | 云端表 | 冲突策略 |
|---|---|---|---|---|
| `WORK_RECORD` | UUID | `WorkRecord`（含 `segments[]`，整体同步） | `beat_work_record` + `beat_work_segment` | 冲突副本 |
| `SETTINGS` | 固定 `settings` | `Settings` | `beat_user_settings` | 字段级合并 |
| `GOAL` | UUID | `Goal` | `beat_goal` | 实体级最后修改优先 |
| `SCHEDULE` | 日期 `YYYY-MM-DD` | `Schedule` | `beat_schedule` | 同上（以日期为自然键，避免同日多条） |
| `PAYROLL` | UUID | `Payroll` | `beat_payroll` | 同上 |
| `NOTIFICATION` | UUID | `Notification` | `beat_notification` | 同上；目标达成通知以 `goalId` 去重 |

> 计时会话**不走同步**（实时状态，以服务端为权威）。设备本地设置（深色模式、悬浮窗位置、振动开关）不同步。

### 3.3 冲突处理规则（服务端行为，已定稿，见需求 10.2/10.9）

| 场景 | 服务端处理 | `resolution` | 客户端动作 |
|---|---|---|---|
| 工时记录：`baseVersion` ≠ 服务端版本 | 保留服务端版本；客户端内容另存为**冲突副本**（新 UUID，`conflictOf` 指向原记录） | `CONFLICT_COPY_CREATED` | 用 `serverPayload` 覆盖本地原记录，插入 `conflictCopy` 并标记"冲突" |
| 工时记录：一端删除，一端修改 | **保留修改**，记录被恢复（墓碑撤销），版本+1 | `RESTORED_BY_UPDATE` | 覆盖本地 |
| 设置：版本不一致 | 按 `fieldUpdatedAt` 逐字段取较新值合并 | `FIELD_MERGED` | 用 `serverPayload` 覆盖本地 |
| 目标/排班/台账/通知：版本不一致 | 比较 `clientUpdatedAt` 与服务端 `updatedAt`，较新者生效 | `SERVER_KEPT` 或 `NONE` | `SERVER_KEPT` 时覆盖本地 |
| 同一 `changeId` 重复 | 不重复处理 | — （`status=DUPLICATE`） | 出队 |

### 3.4 Push 流程（客户端）

1. 取队列中最早的 ≤100 条变更，生成 `requestId`（= `Idempotency-Key`）。
2. 调用 `POST /sync/push`。网络失败/超时 → **用同一 `requestId` 重试**（幂等）。
3. 逐条处理结果：`ACCEPTED/DUPLICATE` 出队并更新本地 `version`；`CONFLICT` 按 3.3 更新本地；`REJECTED` 标记失败不无限重试。
4. 队列清空后执行 Pull。

### 3.5 Pull 流程（客户端）

1. 读取本地 `cursor`（首次为空）→ `GET /sync/pull`。
2. 在**一个本地事务**中应用 `changes`（`UPSERT` 覆盖/新增，`DELETE` 标记删除）。若本地对同一实体存在未上传的修改，则跳过覆盖，等待下一轮 Push 的结果处理。
3. 事务提交成功后才保存 `nextCursor`；`hasMore=true` 则继续。
4. `fullSyncRequired=true`（或 `40002`）→ 清空 `cursor` 全量重拉。

### 3.6 迁移与同步的关系

迁移批次写入的数据同样会产生 `beat_sync_change`，因此其他设备可 Pull 到；迁移的去重规则见需求文档 5.8；`COMPLETED` 后客户端从 `syncCursor` 起 Pull 即可获得合并后的完整数据。

### 3.7 云同步同意、撤回与清除（PRV-009 ~ 013）

| 动作 | 接口 | 服务端效果 | 客户端效果 |
|---|---|---|---|
| 查询状态 | `GET /sync/consent` | 返回是否有效同意、已同意版本、当前版本 | 决定显示“已开启/未开启”，政策升级后提示重新同意 |
| 开启 | `PUT /sync/consent {consented:true, policyVersion}` | 记录同意时间与版本；业务接口放行 | 按需走迁移或全量 Pull，之后开始同步；计时恢复使用互斥接口 |
| 关闭（撤回同意） | `PUT /sync/consent {consented:false}` | 记录撤回时间；云端数据保留；业务接口返回 `20009` | 停止同步，回到本地模式，本机数据不动；运行中的会话按本地计时处理 |
| 关闭并清除 | `DELETE /sync/data {confirm:true}` | 撤回同意 + 删除云端业务数据（含计时会话）；设备详情字段清空；账号、会话、设备标识保留 | 同上，并提示“云端数据已清除” |
| 他端被动感知 | 任意业务接口 | 返回 `20009` | 转本地模式，**不删除本机数据**，设置页提示“云同步已在其他设备关闭” |

规则要点：
- 同意是**账号级**且**带政策版本**，升级版本后需重新同意；撤回与清除不影响本机数据和账号。
- 未同意之前，服务端只在会话里保存 `deviceId` 与 `appVersion`（PRV-013），设备详情在同意后经 `PUT /devices/current` 提交。
- 清除接口也会释放计时互斥；之后用户若在本机继续计时，只是本地计时，不受互斥约束。

---

## 4. 计时互斥

### 4.1 状态机

```
        start                pause               resume
(无) ────────> RUNNING ──────────> PAUSED ──────────> RUNNING
                 │  \                │  \
                 │   \ stop          │   \ stop
                 │    v              │    v
                 │   STOPPED         │   STOPPED
                 │ cancel            │ cancel
                 v                   v
              CANCELED            CANCELED
```

- **活动会话** = `RUNNING` 或 `PAUSED`（暂停仍占用互斥，已确认，需求 10.6）。
- 终态：`STOPPED`（已生成记录）、`CANCELED`。

### 4.2 服务端并发保证

在 `beat_timer_session` 增加生成列并建唯一索引，使"同一账号同一时间至多一个活动会话"由数据库保证（MySQL 无部分索引，用 NULL 不参与唯一性的特性）：

```sql
ALTER TABLE beat_timer_session
  ADD COLUMN active_flag TINYINT GENERATED ALWAYS AS (IF(status IN ('RUNNING','PAUSED'), 1, NULL)) STORED,
  ADD UNIQUE KEY uk_user_active (user_id, active_flag);
```

开始计时：`INSERT` 触发唯一键冲突 → 捕获后查询占用会话 → 返回 `409 / 50001`（并发安全，无需分布式锁，**不依赖 Redis**）。

### 4.3 权限与归属

- 同账号的任意设备可以 `GET current` 看到会话（含开始设备名）。
- **暂停/继续**仅限会话所属设备（`startDeviceId`），他端返回 `403 / 50006`。
- **结束/取消**允许同账号任一设备（解决"原设备故障/没电"的卡死会话）；服务端记录 `lastOperatorDeviceId`。
- 系统层面无法跨设备操控，也没有云端指令推送：原设备只能在联网、回前台、重启时通过 `GET current`、补报返回 `50002` 或 Pull 感知；原设备上已登记的代理提醒无法被他端取消（需求 TMR-025）。
- 结束时间由结束方选择，服务端校验：不早于会话最后一次已知操作时间，不晚于服务端时间 + 5 分钟，总跨度不超过 48 小时。

### 4.4 离线与重连

| 场景 | 处理 |
|---|---|
| 登录态开始计时 | 必须联网调用 `POST /timer/sessions`；失败则不能开始（弱网提示重试） |
| 已有会话断网 | 本地继续计时、暂停、继续、结束（均记录 `occurredAt`），联网后补报 |
| 补报时会话已被他端结束/取消 | `409 / 50002`，客户端提示：将本地结果保存为独立记录（走同步）或丢弃 |
| 他端结束了本设备的会话 | 本设备补报/校验时收到 `409 / 50002`：提示"该计时已在其他设备结束"；本地在他端结束时间之后的片段，用户可选择保存为独立记录（走同步）或丢弃 |
| 计时提醒触发后用户点击通知 | 客户端复用 `GET /timer/sessions/current` 校验会话是否仍在本设备/是否已被他端结束 |
| 补报时间戳不合理 | `422 / 50004`（不得晚于服务端时间 + 5 分钟；不得早于会话开始；分段需连续） |
| 游客态计时后登录 | 登录后未结束会话以"补报开始"申请，冲突同上（需求 6.3） |

> 本方案**不承诺**离线新建会话的全局互斥；这是离线能力与严格互斥的固有取舍。

### 4.5 结束时的服务端校验（`API-TMR-05`）

时间顺序、分段与会话时间戳一致（容差 1 秒）、时长之和一致、金额落在合理区间（全按最低费率～全按最高费率），详见需求 5.6。服务端**不强制精确重算**金额，因为当日已累计工时可能包含尚未同步的其他设备记录。

### 4.6 与计时提醒的关系

计时提醒（"满 N 分钟提醒一次"）使用系统**代理提醒**，在设备本地登记和触发，**服务端不参与，也不需要推送令牌**，因此没有对应接口。HarmonyOS 5.1.1(19) 及之前版本上代理提醒权限不生效，客户端在登记时捕获错误码 `1700002` 后降级为前台提醒，服务端同样不感知。服务端只在以下场景间接相关：用户点击提醒通知后，客户端调用 `API-TMR-01` 校验会话状态；多设备下提醒可能误响的已知限制见需求 TMR-025。

---

## 5. 接口总览

| 编号 | 阶段 | 方法与路径 | 说明 | 幂等 | 关联需求 |
|---|---|---|---|---|---|
| API-AUTH-01 | **本期** | `POST /api/v1/auth/huawei/login` | 华为账号登录 | — | AUTH-001~004, AUTH-006, AUTH-012, AUTH-013 |
| API-AUTH-02 | **本期** | `POST /api/v1/auth/token/refresh` | Refresh Token 续期（静默登录·第一层） | — | AUTH-005, AUTH-007 |
| API-AUTH-03 | **本期** | `POST /api/v1/auth/logout` | 退出当前设备登录 | — | AUTH-008 |
| API-AUTH-04 | **本期** | `POST /api/v1/auth/logout-all` | 退出全部设备 | — | AUTH-009 |
| API-AUTH-05 | **本期** | `GET /api/v1/users/me` | 查询当前用户信息 | — | AUTH-010 |
| API-AUTH-06 | **本期** | `DELETE /api/v1/users/me` | 注销账号 | — | AUTH-011 |
| API-DEV-01 | **本期** | `PUT /api/v1/devices/current` | 注册/更新当前设备 | — | SYNC-012 |
| API-DEV-02 | **本期** | `GET /api/v1/devices` | 查询已登录设备 | — | SYNC-012 |
| API-DEV-03 | **本期** | `DELETE /api/v1/devices/{deviceId}` | 移除设备/撤销其会话 | — | SYNC-012 |
| API-MIG-01 | **本期** | `POST /api/v1/migrations` | 创建游客数据迁移任务 | 是 | MIG-003, MIG-004, MIG-008 |
| API-MIG-02 | **本期** | `POST /api/v1/migrations/{migrationId}/batches` | 分批提交游客数据 | 是 | MIG-005~008 |
| API-MIG-03 | **本期** | `GET /api/v1/migrations/{migrationId}` | 查询迁移进度 | — | MIG-007, MIG-011 |
| API-MIG-04 | **本期** | `POST /api/v1/migrations/{migrationId}/commit` | 完成迁移确认（校验） | 是 | MIG-009, MIG-010 |
| API-SYNC-01 | **本期** | `POST /api/v1/sync/push` | Push：上传本地变更 | 是 | SYNC-001~003, 006~009, 011 |
| API-SYNC-02 | **本期** | `GET /api/v1/sync/pull` | Pull：获取云端增量变更 | — | SYNC-004, 005, 006, 011 |
| API-SYNC-03 | **本期** | `GET /api/v1/sync/status` | 查询同步状态/对账 | — | SYNC-013 |
| API-SYNC-04 | **本期** | `GET /api/v1/sync/consent` | 查询云同步同意状态 | — | PRV-010 |
| API-SYNC-05 | **本期** | `PUT /api/v1/sync/consent` | 开启/撤回云同步同意 | — | PRV-009~011 |
| API-SYNC-06 | **本期** | `DELETE /api/v1/sync/data` | 关闭云同步并清除云端业务数据 | — | PRV-012 |
| API-TMR-01 | **本期** | `GET /api/v1/timer/sessions/current` | 查询当前活动计时会话 | — | TMR-013, 016, 017 |
| API-TMR-02 | **本期** | `POST /api/v1/timer/sessions` | 开始计时（申请运行权限） | 是 | TMR-013, 014, 018 |
| API-TMR-03 | **本期** | `POST /api/v1/timer/sessions/{sessionId}/pause` | 暂停计时 | 是 | TMR-001, 002, 015, 016 |
| API-TMR-04 | **本期** | `POST /api/v1/timer/sessions/{sessionId}/resume` | 继续计时 | 是 | TMR-001, 002, 014, 015 |
| API-TMR-05 | **本期** | `POST /api/v1/timer/sessions/{sessionId}/stop` | 结束计时并生成工时记录 | 是 | TMR-001, 008, 019 |
| API-TMR-06 | **本期** | `POST /api/v1/timer/sessions/{sessionId}/cancel` | 取消计时（不生成记录） | 是 | TMR-001 |
| API-DAT-01 | **本期** | `GET /api/v1/settings` | 查询云端设置 | — | SET-001, SET-014 |
| API-DAT-02 | **本期** | `GET /api/v1/work-records` | 分页查询工时记录 | — | REC-001~005, STA-006 |
| API-DAT-03 | **本期** | `GET /api/v1/work-records/{recordId}` | 查询单条工时记录（含分段） | — | CAL-002, REC-002 |
| API-DAT-04 | **本期** | `GET /api/v1/goals` | 查询存钱目标 | — | GOL-001~004 |
| API-DAT-05 | **本期** | `GET /api/v1/schedules` | 按日期范围查询排班 | — | CAL-003 |
| API-DAT-06 | **本期** | `GET /api/v1/payrolls` | 查询薪资台账 | — | LDG-001~003 |
| API-DAT-07 | **本期** | `GET /api/v1/notifications` | 查询通知 | — | NTF-001~003 |
| API-CFG-01 | **后续·二批** | `GET /api/v1/config/holidays` | 节假日与调休兜底数据 | — | CFG-003 |
| API-STA-01 | **后续·二批** | `GET /api/v1/stats/summary` | 云端对账统计 | — | STA-007 |
| API-BAK-01 | **后续·二批** | `GET /api/v1/backup/export` | 导出云端完整备份（JSON） | — | EXP-001（云端扩展） |
| API-CFG-02 | **后续·二批** | `GET /api/v1/config/city-benchmarks` | 工作性价比城市参考数据 | — | CFG-002, TOOL-028 |
| API-FBK-01 | **后续·二批** | `POST /api/v1/feedback/attachments` | 上传反馈截图 | — | EXP-009 |


---

## 6. 接口明细

> 每个接口：阶段、编号、关联需求、请求/响应字段、示例。字段表中"必填"指请求体/参数必填。响应示例为完整包装结构。


#### 6.1　认证与账号

### API-AUTH-01　【本期】华为账号登录

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/auth/huawei/login` |
| 认证 | 无需 Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | AUTH-001~004, AUTH-006, AUTH-012, AUTH-013 |

客户端完成 Account Kit **标准授权**后，把授权结果提交给后端。后端向华为服务端验证身份，取得稳定身份标识后：已注册→登录；未注册→自动创建账号（`sys_user` + `sys_user_identity`）。随后签发 Beat计薪 自己的 Access/Refresh Token，**不得**把华为凭证当业务 Token 使用。

**静默登录·第二层**也调用本接口：客户端先尝试 Account Kit 静默授权，成功则以 `loginType=SILENT` 提交；失败则回退交互式授权。后端处理逻辑一致。

**已核实**：基础华为账号登录无需申请额外 scope；后端用授权码换取 Access Token 与 UnionID/OpenID。身份映射以 **UnionID 作为账号唯一键**，同时保存 OpenID 作辅助（OpenID 随系统大版本、包名、签名变化，不能当唯一键）；华为返回的 Access Token 仅用于本次换取身份信息，**不落库**。

**数据最小化（需求 PRV-013）**：云同步同意之前，服务端只在会话中保存 `device.deviceId` 与 `device.appVersion`，**忽略**设备名称、型号、系统版本等其他设备字段；这些字段在用户同意云同步后由 `PUT /devices/current` 提交。

**授权码一次性**：换取失败（含超时）时授权码同样作废，后端返回 `20006`，客户端必须重新授权，**不得拿同一个码重试**；因此本接口不依赖 `Idempotency-Key` 重放，后端调用华为服务端应设置较短超时。

后端换取接口的地址、请求参数与鉴权方式**以华为官方文档为准【待核实】**（工程里为配置项 `beat.huawei.token-url`，未写死）；`client_secret` 仅存服务端配置，不入代码库。UnionID 在默认授权范围下是否一定返回【待实测】。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `loginType` | string | 是 | 本次授权方式：交互式 / 华为账号静默授权（仅用于审计统计，两者后端处理一致） 枚举：INTERACTIVE / SILENT |
| `authorizationCode` | string | 是 | 华为账号标准登录授权后客户端获得的 Authorization Code；一次性、用后即焚、有效期极短；换取失败即作废，不得重试，需重新授权 |
| `device` | DeviceInfo | 是 | 设备信息 |

请求示例：

```json
{
  "loginType": "INTERACTIVE",
  "authorizationCode": "<授权后获得的 Authorization Code>",
  "device": {
    "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "deviceName": "我的手机",
    "deviceModel": "示例机型",
    "osVersion": "HarmonyOS 5.0",
    "appVersion": "1.0.0",
    "deviceType": "PHONE"
  }
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `accessToken` | string | 是 | Beat计薪自有 Access Token |
| `refreshToken` | string | 是 | Beat计薪自有 Refresh Token（仅此处与续期接口返回明文） |
| `tokenType` | string | 否 |  |
| `expiresIn` | integer | 是 | Access Token 有效期（秒） |
| `refreshExpiresIn` | integer | 是 | Refresh Token 有效期（秒） |
| `user` | UserInfo | 是 | 用户信息（不含手机号） |
| `isNewUser` | boolean | 是 | 是否本次新建账号 |
| `hasCloudData` | boolean | 是 | 云端是否已有业务数据（用于客户端决定迁移/拉取路径） |
| `syncConsented` | boolean | 否 | 是否已同意云同步（未同意时客户端保持本地模式，不得调用业务数据接口） |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "rt_9f3a...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "refreshExpiresIn": 2592000,
    "user": {
      "userId": "10086",
      "nickname": null,
      "avatarUrl": null,
      "status": "ACTIVE",
      "createdAt": 1795050000000,
      "deletionScheduledAt": null
    },
    "isNewUser": false,
    "hasCloudData": true,
    "syncConsented": true,
    "serverTime": 1795084200000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 参数错误 |
| 401 | 20006 华为授权校验失败 |
| 403 | 20008 账号已冻结 |
| 429 | 60001 请求过于频繁 |

---

### API-AUTH-02　【本期】Refresh Token 续期（静默登录·第一层）

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/auth/token/refresh` |
| 认证 | 无需 Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | AUTH-005, AUTH-007 |

Access Token 过期或应用启动时调用。**每次续期都轮换 Refresh Token**：返回新 Refresh Token，旧 Token 立即作废；若旧 Token 被再次使用，视为泄露，服务端吊销整个设备会话并返回 `20005`。服务端仅保存 Refresh Token 的安全哈希。

返回 `20004/20005` 时客户端应清除本地 Token，进入第二层（华为账号静默授权）或交互式登录；业务数据保持本地可用。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `refreshToken` | string | 是 |  |
| `deviceId` | string | 是 |  |

请求示例：

```json
{
  "refreshToken": "rt_9f3a...",
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11"
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `accessToken` | string | 是 |  |
| `refreshToken` | string | 是 | 轮换后的新 Refresh Token，旧Token立即作废 |
| `tokenType` | string | 否 |  |
| `expiresIn` | integer | 是 |  |
| `refreshExpiresIn` | integer | 是 |  |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "rt_c71d...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "refreshExpiresIn": 2592000,
    "serverTime": 1795084200000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 401 | 20004 Refresh Token 无效或过期 / 20005 重放检测，会话已吊销 |
| 403 | 20008 账号已注销 |

---

### API-AUTH-03　【本期】退出当前设备登录

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/auth/logout` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | AUTH-008 |

吊销**当前设备**会话（Access/Refresh Token 均失效）。客户端应先尽力同步再调用，成功后清除本地 Token。幂等：重复调用返回成功。

**响应 `data`**

无（`data` 为 `null`）。

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": null,
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 401 | 20001/20002/20003 |

---

### API-AUTH-04　【本期】退出全部设备

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/auth/logout-all` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | AUTH-009 |

吊销该账号**所有设备**的会话。其他设备下次请求将收到 `20007`。

**响应 `data`**

无（`data` 为 `null`）。

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": null,
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 401 | 20001/20002/20003 |

---

### API-AUTH-05　【本期】查询当前用户信息

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/users/me` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | AUTH-010 |

userId 取自认证上下文。不返回手机号等未收集信息。

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 否 | 用户ID（字符串形式，避免大整数精度问题） |
| `nickname` | string | 否 | 昵称（需授权请求携带 profile scope 才可能有值；本期功能不依赖，默认为空）（可空） |
| `avatarUrl` | string | 否 | 头像地址（同上；有效期官方未说明，仅用于展示，不依赖其长期有效）（可空） |
| `status` | string | 否 | 枚举：ACTIVE / PENDING_DELETION |
| `createdAt` | int64 | 否 |  |
| `deletionScheduledAt` | int64 | 否 | 计划物理删除时间（注销冷静期内返回）（可空） |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "10086",
    "nickname": null,
    "avatarUrl": null,
    "status": "ACTIVE",
    "createdAt": 1795050000000,
    "deletionScheduledAt": null
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 401 | 20001/20002/20003 |

---

### API-AUTH-06　【本期】注销账号

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `DELETE /api/v1/users/me` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | AUTH-011 |

二次确认后调用。立即吊销全部会话，账号进入 `PENDING_DELETION`，**7 个自然日冷静期**后物理删除全部云端业务数据，且**不晚于注销请求后 15 个工作日**（平台硬性上限）；冷静期内重新登录可撤销注销（登录接口返回的 `user.status` 为 `PENDING_DELETION`，客户端引导用户确认恢复）。

本接口不影响设备本地的游客数据。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `confirm` | boolean | 是 | 必须为 true |
| `reason` | string | 否 | 注销原因，可空（可空） |

请求示例：

```json
{
  "confirm": true,
  "reason": "不再使用"
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 否 | 用户ID（字符串形式，避免大整数精度问题） |
| `nickname` | string | 否 | 昵称（需授权请求携带 profile scope 才可能有值；本期功能不依赖，默认为空）（可空） |
| `avatarUrl` | string | 否 | 头像地址（同上；有效期官方未说明，仅用于展示，不依赖其长期有效）（可空） |
| `status` | string | 否 | 枚举：ACTIVE / PENDING_DELETION |
| `createdAt` | int64 | 否 |  |
| `deletionScheduledAt` | int64 | 否 | 计划物理删除时间（注销冷静期内返回）（可空） |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "10086",
    "nickname": null,
    "avatarUrl": null,
    "status": "PENDING_DELETION",
    "createdAt": 1795050000000,
    "deletionScheduledAt": 1795689000000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 confirm 必须为 true |
| 401 | 20001/20002/20003 |

---

#### 6.2　设备管理

### API-DEV-01　【本期】注册/更新当前设备

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `PUT /api/v1/devices/current` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | SYNC-012 |

需已同意云同步（否则 `20009`）。登录后及应用版本/设备名变化时调用，按 `deviceId` upsert，此时才保存设备名称、型号、系统版本等详情（需求 PRV-013）。幂等。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 | 客户端生成并持久化的设备唯一ID（UUID） |
| `deviceName` | string | 否 |  |
| `deviceModel` | string | 否 |  |
| `osVersion` | string | 否 | 系统版本 |
| `appVersion` | string | 是 |  |
| `deviceType` | string | 否 | 枚举：PHONE / TABLET / OTHER |

请求示例：

```json
{
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
  "deviceName": "我的手机",
  "deviceModel": "示例机型",
  "osVersion": "HarmonyOS 5.0",
  "appVersion": "1.0.0",
  "deviceType": "PHONE"
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 否 |  |
| `deviceName` | string | 否 |  |
| `deviceModel` | string | 否 |  |
| `deviceType` | string | 否 |  |
| `appVersion` | string | 否 |  |
| `lastActiveAt` | int64 | 否 |  |
| `lastSyncAt` | int64 | 否 |  |
| `current` | boolean | 否 | 是否当前设备 |
| `status` | string | 否 | 枚举：ACTIVE / REVOKED |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "deviceName": "我的手机",
    "deviceModel": "示例机型",
    "deviceType": "PHONE",
    "appVersion": "1.0.0",
    "lastActiveAt": 1795084200000,
    "lastSyncAt": 1795084200000,
    "current": true,
    "status": "ACTIVE"
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 |
| 401 | 20001/20002/20003 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-DEV-02　【本期】查询已登录设备

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/devices` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | SYNC-012 |

返回当前账号 `ACTIVE` 状态的设备列表，当前设备 `current=true`。

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `[]deviceId` | string | 否 |  |
| `[]deviceName` | string | 否 |  |
| `[]deviceModel` | string | 否 |  |
| `[]deviceType` | string | 否 |  |
| `[]appVersion` | string | 否 |  |
| `[]lastActiveAt` | int64 | 否 |  |
| `[]lastSyncAt` | int64 | 否 |  |
| `[]current` | boolean | 否 | 是否当前设备 |
| `[]status` | string | 否 | 枚举：ACTIVE / REVOKED |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
      "deviceName": "我的手机",
      "deviceModel": "示例机型",
      "deviceType": "PHONE",
      "appVersion": "1.0.0",
      "lastActiveAt": 1795084200000,
      "lastSyncAt": 1795084200000,
      "current": true,
      "status": "ACTIVE"
    }
  ],
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 401 | 20001/20002/20003 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-DEV-03　【本期】移除设备/撤销其会话

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `DELETE /api/v1/devices/{deviceId}` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | SYNC-012 |

吊销指定设备的会话；被移除设备下次请求返回 `20007`。不能用于移除当前设备（请用退出登录）。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `deviceId` | path | string | 是 | 设备ID |

**响应 `data`**

无（`data` 为 `null`）。

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": null,
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 不能移除当前设备 |
| 404 | 30002 设备不存在 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

#### 6.3　游客数据迁移

### API-MIG-01　【本期】创建游客数据迁移任务

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/migrations` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | MIG-003, MIG-004, MIG-008 |

登录成功且本地存在游客数据时调用。`migrationId` 由客户端生成；重复创建返回同一任务（幂等）。任务有效期默认 7 天。同一账号同一时间只允许一个未完成迁移任务。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `migrationId` | uuid | 是 | 客户端生成的迁移任务唯一ID，重复创建返回同一任务（幂等） |
| `deviceId` | string | 是 |  |
| `appVersion` | string | 否 |  |
| `schemaVersion` | integer | 否 | 本地数据结构版本 |
| `expectedCounts` | EntityCounts | 是 | 游客数据各类数量 |

请求示例：

```json
{
  "migrationId": "0b9f1e2a-4c3d-4e5f-a6b7-8c9d0e1f2a3b",
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
  "appVersion": "1.0.0",
  "schemaVersion": 1,
  "expectedCounts": {
    "settings": 1,
    "workRecords": 120,
    "segments": 180,
    "goals": 2,
    "schedules": 30,
    "payrolls": 6,
    "notifications": 3
  }
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `migrationId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：CREATED / UPLOADING / COMPLETED / FAILED |
| `expectedCounts` | EntityCounts | 否 | 各类数据数量 |
| `receivedCounts` | EntityCounts | 否 | 已收到（含去重/冲突的）数量 |
| `acceptedCount` | integer | 否 | 新写入条数 |
| `dedupedCount` | integer | 否 | 判定为重复而跳过的条数 |
| `conflictCopyCount` | integer | 否 | 生成冲突副本条数 |
| `lastBatchNo` | integer | 否 | 已成功处理的最大连续批号 |
| `failureReason` | string | 否 | （可空） |
| `createdAt` | int64 | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `expiresAt` | int64 | 否 | 任务过期时间 |

响应示例（HTTP 201）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "migrationId": "0b9f1e2a-4c3d-4e5f-a6b7-8c9d0e1f2a3b",
    "status": "CREATED",
    "expectedCounts": {
      "settings": 1,
      "workRecords": 120,
      "segments": 180,
      "goals": 2,
      "schedules": 30,
      "payrolls": 6,
      "notifications": 3
    },
    "receivedCounts": {
      "settings": 0,
      "workRecords": 0,
      "segments": 0,
      "goals": 0,
      "schedules": 0,
      "payrolls": 0,
      "notifications": 0
    },
    "acceptedCount": 0,
    "dedupedCount": 0,
    "conflictCopyCount": 0,
    "lastBatchNo": 0,
    "failureReason": null,
    "createdAt": 1795084200000,
    "updatedAt": 1795084203000,
    "expiresAt": 1795689000000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 409 | 40003 已存在未完成的其他迁移任务 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-MIG-02　【本期】分批提交游客数据

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/migrations/{migrationId}/batches` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | MIG-005~008 |

按批提交，建议每批 ≤200 条。服务端：①按**去重规则**判定（需求 5.8：id → 指纹/自然键）；②相同跳过（`deduplicated`）；③冲突保留双方（`conflictCopies`）；④新数据写入并产生同步变更日志，使其他设备可 Pull 到。**不得覆盖云端已有数据**。

幂等：同一 `batchNo` 重复提交直接返回上次结果；批号必须连续，跳号返回 `40003`。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `migrationId` | path | uuid | 是 | 迁移任务ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `batchNo` | integer | 是 | 批号，从1递增；重复批号幂等返回上次结果 |
| `items` | array<MigrationItem> | 是 | 本批条目，建议≤200 |

请求示例：

```json
{
  "batchNo": 1,
  "items": [
    {
      "entityType": "WORK_RECORD",
      "entityId": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21",
      "clientUpdatedAt": 1795084200000,
      "payload": {"id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21", "workDate": "2026-11-19", "startAt": 1795050000000, "endAt": 1795084200000, "durationMs": 30600000, "totalEarnCents": 26250, "shift": "MORNING", "tags": ["项目A"], "note": "赶工", "crossDay": false, "source": "TIMER", "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}}, "segments": [{"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]}, {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}]}
    }
  ]
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `batchNo` | integer | 是 |  |
| `accepted` | integer | 是 |  |
| `deduplicated` | integer | 是 |  |
| `conflictCopies` | integer | 是 |  |
| `rejected` | array<object> | 否 | 被拒绝条目及原因 |
| `rejected[].entityId` | string | 否 |  |
| `rejected[].reason` | string | 否 |  |
| `task` | MigrationTask | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "batchNo": 1,
    "accepted": 96,
    "deduplicated": 4,
    "conflictCopies": 0,
    "rejected": [],
    "task": {
      "migrationId": "0b9f1e2a-4c3d-4e5f-a6b7-8c9d0e1f2a3b",
      "status": "UPLOADING",
      "expectedCounts": {"settings": 1, "workRecords": 120, "segments": 180, "goals": 2, "schedules": 30, "payrolls": 6, "notifications": 3},
      "receivedCounts": {"settings": 1, "workRecords": 100, "segments": 150, "goals": 2, "schedules": 30, "payrolls": 6, "notifications": 3},
      "acceptedCount": 96,
      "dedupedCount": 4,
      "conflictCopyCount": 0,
      "lastBatchNo": 1,
      "failureReason": null,
      "createdAt": 1795084200000,
      "updatedAt": 1795084203000,
      "expiresAt": 1795689000000
    }
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 404 | 40005 迁移任务不存在 |
| 409 | 40003 任务状态/批号非法 |
| 422 | 10001 数据校验失败（逐条在rejected中返回） |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-MIG-03　【本期】查询迁移进度

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/migrations/{migrationId}` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | MIG-007, MIG-011 |

应用重启后用于恢复：返回 `lastBatchNo`，客户端从 `lastBatchNo+1` 继续。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `migrationId` | path | uuid | 是 | 迁移任务ID |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `migrationId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：CREATED / UPLOADING / COMPLETED / FAILED |
| `expectedCounts` | EntityCounts | 否 | 各类数据数量 |
| `receivedCounts` | EntityCounts | 否 | 已收到（含去重/冲突的）数量 |
| `acceptedCount` | integer | 否 | 新写入条数 |
| `dedupedCount` | integer | 否 | 判定为重复而跳过的条数 |
| `conflictCopyCount` | integer | 否 | 生成冲突副本条数 |
| `lastBatchNo` | integer | 否 | 已成功处理的最大连续批号 |
| `failureReason` | string | 否 | （可空） |
| `createdAt` | int64 | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `expiresAt` | int64 | 否 | 任务过期时间 |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "migrationId": "0b9f1e2a-4c3d-4e5f-a6b7-8c9d0e1f2a3b",
    "status": "UPLOADING",
    "expectedCounts": {
      "settings": 1,
      "workRecords": 120,
      "segments": 180,
      "goals": 2,
      "schedules": 30,
      "payrolls": 6,
      "notifications": 3
    },
    "receivedCounts": {
      "settings": 1,
      "workRecords": 100,
      "segments": 150,
      "goals": 2,
      "schedules": 30,
      "payrolls": 6,
      "notifications": 3
    },
    "acceptedCount": 96,
    "dedupedCount": 4,
    "conflictCopyCount": 0,
    "lastBatchNo": 1,
    "failureReason": null,
    "createdAt": 1795084200000,
    "updatedAt": 1795084203000,
    "expiresAt": 1795689000000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 404 | 40005 迁移任务不存在 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-MIG-04　【本期】完成迁移确认（校验）

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/migrations/{migrationId}/commit` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | MIG-009, MIG-010 |

全部批次提交后调用。服务端核对已接收数量与 `expectedCounts`，并校验记录总时长/总金额；通过 → `COMPLETED`，返回 `syncCursor` 与服务端各类数量；不一致 → `FAILED` 并在 `mismatches` 说明，客户端可重传缺失批次后再次 commit。

**客户端只有在收到 `COMPLETED` 且随后 Pull 并写入本地成功后，才可清理游客数据副本**（是否保留本地备份见需求 10.11）。重复 commit 幂等。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `migrationId` | path | uuid | 是 | 迁移任务ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `expectedCounts` | EntityCounts | 是 | 各类数据数量 |
| `totals` | object | 否 | 关键字段校验值：游客记录总时长/总金额 |
| `totals.totalDurationMs` | int64 | 否 |  |
| `totals.totalEarnCents` | int64 | 否 |  |

请求示例：

```json
{
  "expectedCounts": {
    "settings": 1,
    "workRecords": 120,
    "segments": 180,
    "goals": 2,
    "schedules": 30,
    "payrolls": 6,
    "notifications": 3
  },
  "totals": {
    "totalDurationMs": 3456000000,
    "totalEarnCents": 3200000
  }
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | string | 是 | 枚举：COMPLETED / FAILED |
| `task` | MigrationTask | 是 |  |
| `serverCounts` | EntityCounts | 否 | 迁移后服务端该账号各类数量（含云端原有） |
| `mismatches` | array<string> | 否 | 校验不一致项说明（FAILED时） |
| `syncCursor` | string | 否 | 可用于随后Pull的游标 |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "COMPLETED",
    "task": {
      "migrationId": "0b9f1e2a-4c3d-4e5f-a6b7-8c9d0e1f2a3b",
      "status": "COMPLETED",
      "expectedCounts": {"settings": 1, "workRecords": 120, "segments": 180, "goals": 2, "schedules": 30, "payrolls": 6, "notifications": 3},
      "receivedCounts": {"settings": 1, "workRecords": 100, "segments": 150, "goals": 2, "schedules": 30, "payrolls": 6, "notifications": 3},
      "acceptedCount": 96,
      "dedupedCount": 4,
      "conflictCopyCount": 0,
      "lastBatchNo": 3,
      "failureReason": null,
      "createdAt": 1795084200000,
      "updatedAt": 1795084203000,
      "expiresAt": 1795689000000
    },
    "serverCounts": {
      "settings": 1,
      "workRecords": 120,
      "segments": 180,
      "goals": 2,
      "schedules": 30,
      "payrolls": 6,
      "notifications": 3
    },
    "mismatches": [],
    "syncCursor": "10234"
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 404 | 40005 |
| 409 | 40003 状态非法 |
| 422 | 40004 迁移校验不通过 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

#### 6.4　数据同步

### API-SYNC-01　【本期】Push：上传本地变更

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/sync/push` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | SYNC-001~003, 006~009, 011 |

提交本地新增/修改/删除。服务端逐条处理，**单条失败不影响其他条**，整体返回 200，逐条结果在 `results` 中：

| status | 含义 | 客户端处理 |
|---|---|---|
| `ACCEPTED` | 已写入，返回 `newVersion` | 出队，更新本地版本号 |
| `DUPLICATE` | `changeId` 已处理过 | 视同成功，出队 |
| `CONFLICT` | `baseVersion` 与服务端不一致 | 按 `resolution` 处理（见"同步机制"） |
| `REJECTED` | 校验失败（`errorCode`） | 标记失败并提示，不无限重试 |

幂等：`requestId` 重复整体返回上次响应；`changeId` 重复逐条返回 `DUPLICATE`。每次最多 100 条。工时记录写入前执行服务端校验（需求 5.6）。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `requestId` | uuid | 是 | 请求唯一ID（幂等） |
| `deviceId` | string | 是 |  |
| `changes` | array<SyncChange> | 是 | 变更列表，单次≤100 |

请求示例：

```json
{
  "requestId": "6d1f0a52-3c84-4b19-9e07-2a5c8d1b4f60",
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
  "changes": [
    {
      "changeId": "11111111-2222-4333-8444-555555555555",
      "entityType": "WORK_RECORD",
      "entityId": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21",
      "op": "UPSERT",
      "baseVersion": 2,
      "clientUpdatedAt": 1795084200000,
      "payload": {"id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21", "workDate": "2026-11-19", "startAt": 1795050000000, "endAt": 1795084200000, "durationMs": 30600000, "totalEarnCents": 26250, "shift": "MORNING", "tags": ["项目A"], "note": "赶工", "crossDay": false, "source": "TIMER", "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}}, "segments": [{"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]}, {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}]}
    },
    {
      "changeId": "66666666-7777-4888-9999-000000000000",
      "entityType": "GOAL",
      "entityId": "2f6a1b84-90c3-4e7d-a5b2-3c8d7e1f0a96",
      "op": "DELETE",
      "baseVersion": 2,
      "clientUpdatedAt": 1795084200000
    }
  ]
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `requestId` | uuid | 是 |  |
| `results` | array<SyncPushItemResult> | 是 |  |
| `serverCursor` | string | 否 | 提交后服务端最新游标（仅供参考，客户端仍以Pull结果推进本地游标） |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "requestId": "6d1f0a52-3c84-4b19-9e07-2a5c8d1b4f60",
    "serverCursor": "10240",
    "serverTime": 1795084200800,
    "results": [
      {"changeId": "11111111-2222-4333-8444-555555555555", "entityType": "WORK_RECORD", "entityId": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21", "status": "CONFLICT", "newVersion": 3, "resolution": "CONFLICT_COPY_CREATED", "serverPayload": {"id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21", "workDate": "2026-11-19", "startAt": 1795050000000, "endAt": 1795084200000, "durationMs": 30600000, "totalEarnCents": 26250, "shift": "MORNING", "tags": ["项目A"], "note": "赶工", "crossDay": false, "source": "TIMER", "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}}, "segments": [{"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]}, {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}], "version": 3, "updatedAt": 1795084205000, "deleted": false}, "conflictCopy": {"id": "b5c6d7e8-f9a0-4b1c-8d2e-3f4a5b6c7d8e", "workDate": "2026-11-19", "startAt": 1795050000000, "endAt": 1795084200000, "durationMs": 30600000, "totalEarnCents": 26250, "shift": "MORNING", "tags": ["项目A"], "note": "赶工", "crossDay": false, "source": "TIMER", "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}}, "segments": [{"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]}, {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}], "version": 1, "updatedAt": 1795084205000, "deleted": false, "conflictOf": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21"}},
      {"changeId": "66666666-7777-4888-9999-000000000000", "entityType": "GOAL", "entityId": "2f6a1b84-90c3-4e7d-a5b2-3c8d7e1f0a96", "status": "ACCEPTED", "newVersion": 3, "resolution": "NONE"}
    ]
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 参数错误 / 10003 缺少幂等键 |
| 413 | 10005 单次变更数超限 |
| 422 | 10004 同一请求ID内容不一致 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-SYNC-02　【本期】Pull：获取云端增量变更

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/sync/pull` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | SYNC-004, 005, 006, 011 |

携带 `cursor` 获取其后产生的变更（包含他设备的修改、删除墓碑，以及本设备自己产生的变更——客户端按 `originDeviceId`/版本号自行忽略已应用项）。首次或换机 `cursor` 为空，从头拉取。

客户端必须**先事务写入本地库，成功后再保存 `nextCursor`**。`hasMore=true` 时继续以 `nextCursor` 请求。若 `fullSyncRequired=true`（游标已过期，墓碑已被清理），清空本地游标重新全量拉取。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `cursor` | query | string | 否 | 上次保存的游标；首次不传 |
| `limit` | query | integer | 否 | 每页条数，默认200，最大500 |
| `deviceId` | query | string | 是 | 当前设备ID |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `changes` | array<SyncServerChange> | 是 |  |
| `nextCursor` | string | 是 | 本页最后一条的游标；客户端写入本地成功后再保存 |
| `hasMore` | boolean | 是 | 是否还有下一页 |
| `fullSyncRequired` | boolean | 是 | 游标过旧/无效，需清空游标全量重拉 |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "changes": [
      {"seq": "10235", "entityType": "WORK_RECORD", "entityId": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21", "op": "UPSERT", "version": 3, "updatedAt": 1795084200000, "originDeviceId": "other-device-id", "payload": {"id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21", "workDate": "2026-11-19", "startAt": 1795050000000, "endAt": 1795084200000, "durationMs": 30600000, "totalEarnCents": 26250, "shift": "MORNING", "tags": ["项目A"], "note": "赶工", "crossDay": false, "source": "TIMER", "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}}, "segments": [{"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]}, {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}], "version": 3, "updatedAt": 1795084205000, "deleted": false}},
      {"seq": "10236", "entityType": "GOAL", "entityId": "2f6a1b84-90c3-4e7d-a5b2-3c8d7e1f0a96", "op": "DELETE", "version": 3, "updatedAt": 1795084200000, "originDeviceId": "other-device-id"}
    ],
    "nextCursor": "10236",
    "hasMore": false,
    "fullSyncRequired": false,
    "serverTime": 1795084200900
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 |
| 409 | 40002 游标无效（同时 fullSyncRequired=true 视同信号） |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-SYNC-03　【本期】查询同步状态/对账

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/sync/status` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | SYNC-013 |

返回服务端最新游标、云端各类数据数量、是否存在活动计时会话，用于状态展示与对账。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `deviceId` | query | string | 是 | 当前设备ID |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `serverCursor` | string | 是 | 服务端最新游标 |
| `lastPushAt` | int64 | 否 |  |
| `lastPullAt` | int64 | 否 |  |
| `entityCounts` | EntityCounts | 否 | 云端各类数据数量（用于对账） |
| `hasActiveTimer` | boolean | 否 | 账号是否存在活动计时会话 |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "serverCursor": "10240",
    "lastPushAt": 1795084200000,
    "lastPullAt": 1795084200000,
    "entityCounts": {
      "settings": 1,
      "workRecords": 120,
      "segments": 180,
      "goals": 2,
      "schedules": 30,
      "payrolls": 6,
      "notifications": 3
    },
    "hasActiveTimer": true
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-SYNC-04　【本期】查询云同步同意状态

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/sync/consent` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | PRV-010 |

返回当前账号的云同步同意状态，**不受同意校验限制**。登录后若 `LoginResult.syncConsented=false`、隐私政策版本升级后，或设置页进入“账号与云同步”时调用。

同意是**账号级**的，并带隐私政策版本：服务端当前版本升级后，旧版本的同意视为无效（`consented=false`），需重新展示新政策并重新同意。

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `consented` | boolean | 是 | 当前是否有效同意（已同意且版本为当前版本） |
| `consentedAt` | int64 | 否 | 同意时间（可空） |
| `consentedPolicyVersion` | string | 否 | 已同意的隐私政策版本（可空） |
| `currentPolicyVersion` | string | 是 | 服务端当前隐私政策版本 |
| `revokedAt` | int64 | 否 | 最近一次撤回时间（可空） |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "consented": true,
    "consentedAt": 1795084200000,
    "consentedPolicyVersion": "1.0",
    "currentPolicyVersion": "1.0",
    "revokedAt": null
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 401 | 20001/20002/20003 |

---

### API-SYNC-05　【本期】开启/撤回云同步同意

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `PUT /api/v1/sync/consent` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | PRV-009~011 |

**不受同意校验限制。** 客户端在用户于“单独同意”弹窗（需求 PRV-004）中点击同意后调用。

| 请求 | 服务端行为 |
|---|---|
| `consented=true` | 记录同意时间与 `policyVersion`；`policyVersion` 必须等于服务端当前版本，否则 `400 / 10001`（提示客户端重新展示最新政策） |
| `consented=false`（撤回） | 清除有效同意并记录 `revokedAt`；**云端已有数据保留**；此后全部业务数据接口返回 `403 / 20009`，客户端回到本地模式（计时纯本地，不再调用互斥接口） |

撤回后用户可再次开启（重新走同意弹窗），云端数据仍在；也可选择 `DELETE /sync/data` 清除。幂等：重复提交同样内容返回当前状态。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `consented` | boolean | 是 | true=同意开启云同步；false=撤回同意 |
| `policyVersion` | string | 否 | 同意时展示的隐私政策版本；consented=true 时必填，须等于服务端当前版本 |

请求示例：

```json
{
  "consented": true,
  "policyVersion": "1.0"
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `consented` | boolean | 是 | 当前是否有效同意（已同意且版本为当前版本） |
| `consentedAt` | int64 | 否 | 同意时间（可空） |
| `consentedPolicyVersion` | string | 否 | 已同意的隐私政策版本（可空） |
| `currentPolicyVersion` | string | 是 | 服务端当前隐私政策版本 |
| `revokedAt` | int64 | 否 | 最近一次撤回时间（可空） |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "consented": true,
    "consentedAt": 1795084200000,
    "consentedPolicyVersion": "1.0",
    "currentPolicyVersion": "1.0",
    "revokedAt": null
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 policyVersion 缺失或不是当前版本 |
| 401 | 20001/20002/20003 |

---

### API-SYNC-06　【本期】关闭云同步并清除云端业务数据

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `DELETE /api/v1/sync/data` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | PRV-012 |

**不受同意校验限制**（用户撤回同意后仍必须能删除）。一次完成“撤回同意 + 删除云端业务数据”，需用户二次确认。

**删除**：设置、工时记录与分段、目标、排班、薪资台账、通知、计时会话（含活动会话，互斥随之释放）、同步变更日志、迁移任务、业务幂等记录；设备详情字段（名称、型号、系统版本、设备类型）一并清空。
**保留**：账号本身、登录会话、设备标识与应用版本（会话管理所需）。服务器访问日志按法律法规要求留存。

**其他设备**：下次调用业务接口时收到 `20009`，转为本地模式，**本机数据不受影响**。本机数据不会因此被重新上传，除非用户再次开启云同步。

物理删除即时完成（不是墓碑），远快于平台要求的 15 个工作日。幂等：重复调用返回成功，各类数量为 0。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `confirm` | boolean | 是 | 必须为 true |

请求示例：

```json
{
  "confirm": true
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deleted` | EntityCounts | 是 | 各类被删除的数据数量 |
| `clearedAt` | int64 | 是 | 清除完成时间 |
| `consented` | boolean | 是 | 清除后的同意状态，恒为 false |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "deleted": {
      "settings": 1,
      "workRecords": 120,
      "segments": 180,
      "goals": 2,
      "schedules": 30,
      "payrolls": 6,
      "notifications": 3
    },
    "clearedAt": 1795084200000,
    "consented": false
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 confirm 必须为 true |
| 401 | 20001/20002/20003 |

---

#### 6.5　计时会话

### API-TMR-01　【本期】查询当前活动计时会话

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/timer/sessions/current` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | TMR-013, 016, 017 |

返回账号当前 `RUNNING`/`PAUSED` 的会话；没有则 `data` 为 `null`。应用启动、回到前台、联网恢复时调用，用于发现他设备会话与状态校验。

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：RUNNING / PAUSED / STOPPED / CANCELED |
| `workDate` | string | 否 |  |
| `startedAt` | int64 | 是 |  |
| `segments` | array<TimerSegment> | 是 |  |
| `accumulatedMs` | int64 | 否 | 截至 serverTime 的已累计工作时长（毫秒） |
| `startDeviceId` | string | 否 |  |
| `startDeviceName` | string | 否 |  |
| `lastOperatorDeviceId` | string | 否 |  |
| `ruleSnapshot` | PayRule | 否 | 计薪规则（设置与记录快照共用） |
| `version` | integer | 否 |  |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "e8a1c6d2-3b95-4f07-8d14-7a2b5c9e0f33",
    "status": "RUNNING",
    "workDate": "2026-11-19",
    "startedAt": 1795050000000,
    "segments": [
      {"segmentNo": 1, "startAt": 1795050000000, "endAt": null}
    ],
    "accumulatedMs": 3600000,
    "startDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "startDeviceName": "我的手机",
    "lastOperatorDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "ruleSnapshot": {
      "payMode": "STATUTORY",
      "baseRateCents": 3000,
      "overtimeThresholdMinutes": 480,
      "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}
    },
    "version": 1,
    "serverTime": 1795053600000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-TMR-02　【本期】开始计时（申请运行权限）

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/timer/sessions` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | TMR-013, 014, 018 |

登录状态下开始计时必须联网调用。服务端在事务内检查是否已有活动会话；没有则创建，**唯一性由数据库约束保证**（见"计时互斥"）。`sessionId` 由客户端生成，重复提交幂等返回同一会话。

已有活动会话 → `409 / 50001 TIMER_ALREADY_RUNNING`，`data.currentSession` 返回占用方信息（含设备名），客户端提示用户处理。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | uuid | 是 | 客户端生成，重复提交幂等 |
| `deviceId` | string | 是 |  |
| `startedAt` | int64 | 是 | 客户端开始时间（毫秒） |
| `workDate` | string | 是 |  |
| `ruleSnapshot` | PayRule | 是 | 计薪规则（设置与记录快照共用） |

请求示例：

```json
{
  "sessionId": "e8a1c6d2-3b95-4f07-8d14-7a2b5c9e0f33",
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
  "startedAt": 1795050000000,
  "workDate": "2026-11-19",
  "ruleSnapshot": {
    "payMode": "STATUTORY",
    "baseRateCents": 3000,
    "overtimeThresholdMinutes": 480,
    "customRates": {
      "overtime": {"inputType": "NONE"},
      "restDay": {"inputType": "NONE"},
      "holiday": {"inputType": "NONE"}
    }
  }
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：RUNNING / PAUSED / STOPPED / CANCELED |
| `workDate` | string | 否 |  |
| `startedAt` | int64 | 是 |  |
| `segments` | array<TimerSegment> | 是 |  |
| `accumulatedMs` | int64 | 否 | 截至 serverTime 的已累计工作时长（毫秒） |
| `startDeviceId` | string | 否 |  |
| `startDeviceName` | string | 否 |  |
| `lastOperatorDeviceId` | string | 否 |  |
| `ruleSnapshot` | PayRule | 否 | 计薪规则（设置与记录快照共用） |
| `version` | integer | 否 |  |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 201）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "e8a1c6d2-3b95-4f07-8d14-7a2b5c9e0f33",
    "status": "RUNNING",
    "workDate": "2026-11-19",
    "startedAt": 1795050000000,
    "segments": [
      {"segmentNo": 1, "startAt": 1795050000000, "endAt": null}
    ],
    "accumulatedMs": 0,
    "startDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "startDeviceName": "我的手机",
    "lastOperatorDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "ruleSnapshot": {
      "payMode": "STATUTORY",
      "baseRateCents": 3000,
      "overtimeThresholdMinutes": 480,
      "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}
    },
    "version": 1,
    "serverTime": 1795050000000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 409 | 50001 TIMER_ALREADY_RUNNING（data.currentSession） |
| 422 | 50004 时间戳不合法 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

冲突响应示例（HTTP 409）：

```json
{
  "code": 50001,
  "message": "已有正在运行的计时会话（设备：我的平板）",
  "data": {
    "currentSession": {
      "sessionId": "e8a1c6d2-3b95-4f07-8d14-7a2b5c9e0f33",
      "status": "RUNNING",
      "workDate": "2026-11-19",
      "startedAt": 1795050000000,
      "segments": [{"segmentNo": 1, "startAt": 1795050000000, "endAt": null}],
      "accumulatedMs": 3600000,
      "startDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
      "startDeviceName": "我的手机",
      "lastOperatorDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
      "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}},
      "version": 1,
      "serverTime": 1795053600000
    }
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
---

### API-TMR-03　【本期】暂停计时

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/timer/sessions/{sessionId}/pause` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | TMR-001, 002, 015, 016 |

状态机 `RUNNING → PAUSED`，关闭当前分段。离线暂停后可联网补报（`occurredAt` 为实际暂停时间）。重复提交幂等。

**仅会话所属设备**（`startDeviceId`）可暂停；他端请求返回 `403 / 50006`（他端只能结束或取消，见需求 10.4）。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `sessionId` | path | uuid | 是 | 会话ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 |  |
| `occurredAt` | int64 | 是 | 操作实际发生的客户端时间（毫秒）；离线补报时与请求时间不同 |
| `expectedVersion` | integer | 否 | 可选：会话版本号，不一致返回 409 TIMER_STATE_INVALID |

请求示例：

```json
{
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
  "occurredAt": 1795060800000,
  "expectedVersion": 1
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：RUNNING / PAUSED / STOPPED / CANCELED |
| `workDate` | string | 否 |  |
| `startedAt` | int64 | 是 |  |
| `segments` | array<TimerSegment> | 是 |  |
| `accumulatedMs` | int64 | 否 | 截至 serverTime 的已累计工作时长（毫秒） |
| `startDeviceId` | string | 否 |  |
| `startDeviceName` | string | 否 |  |
| `lastOperatorDeviceId` | string | 否 |  |
| `ruleSnapshot` | PayRule | 否 | 计薪规则（设置与记录快照共用） |
| `version` | integer | 否 |  |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "e8a1c6d2-3b95-4f07-8d14-7a2b5c9e0f33",
    "status": "PAUSED",
    "workDate": "2026-11-19",
    "startedAt": 1795050000000,
    "segments": [
      {"segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000}
    ],
    "accumulatedMs": 10800000,
    "startDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "startDeviceName": "我的手机",
    "lastOperatorDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "ruleSnapshot": {
      "payMode": "STATUTORY",
      "baseRateCents": 3000,
      "overtimeThresholdMinutes": 480,
      "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}
    },
    "version": 2,
    "serverTime": 1795060800000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 50006 仅会话所属设备可暂停/继续 / 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |
| 404 | 50003 会话不存在 |
| 409 | 50002 状态非法/会话已被他端结束 |
| 422 | 50004 时间戳不合法 |

---

### API-TMR-04　【本期】继续计时

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/timer/sessions/{sessionId}/resume` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | TMR-001, 002, 014, 015 |

状态机 `PAUSED → RUNNING`，开启新分段。**建议联网执行**（需求 10.5）；本机会话仍有效时离线继续，联网补报并校验。

**仅会话所属设备**可继续；他端返回 `403 / 50006`。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `sessionId` | path | uuid | 是 | 会话ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 |  |
| `occurredAt` | int64 | 是 | 操作实际发生的客户端时间（毫秒）；离线补报时与请求时间不同 |
| `expectedVersion` | integer | 否 | 可选：会话版本号，不一致返回 409 TIMER_STATE_INVALID |

请求示例：

```json
{
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
  "occurredAt": 1795064400000,
  "expectedVersion": 2
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：RUNNING / PAUSED / STOPPED / CANCELED |
| `workDate` | string | 否 |  |
| `startedAt` | int64 | 是 |  |
| `segments` | array<TimerSegment> | 是 |  |
| `accumulatedMs` | int64 | 否 | 截至 serverTime 的已累计工作时长（毫秒） |
| `startDeviceId` | string | 否 |  |
| `startDeviceName` | string | 否 |  |
| `lastOperatorDeviceId` | string | 否 |  |
| `ruleSnapshot` | PayRule | 否 | 计薪规则（设置与记录快照共用） |
| `version` | integer | 否 |  |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "e8a1c6d2-3b95-4f07-8d14-7a2b5c9e0f33",
    "status": "RUNNING",
    "workDate": "2026-11-19",
    "startedAt": 1795050000000,
    "segments": [
      {"segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000},
      {"segmentNo": 2, "startAt": 1795064400000, "endAt": null}
    ],
    "accumulatedMs": 10800000,
    "startDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "startDeviceName": "我的手机",
    "lastOperatorDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "ruleSnapshot": {
      "payMode": "STATUTORY",
      "baseRateCents": 3000,
      "overtimeThresholdMinutes": 480,
      "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}
    },
    "version": 3,
    "serverTime": 1795064400000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 50006 仅会话所属设备可暂停/继续 / 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |
| 404 | 50003 |
| 409 | 50002 |
| 422 | 50004 |

---

### API-TMR-05　【本期】结束计时并生成工时记录

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/timer/sessions/{sessionId}/stop` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | TMR-001, 008, 019 |

状态机 `RUNNING|PAUSED → STOPPED`。请求携带客户端生成的完整 `record`（`record.id` 即 recordId）。服务端校验：时间顺序、与会话分段一致性、时长一致性、金额区间（需求 5.6），通过后**同一事务内**写入 `beat_work_record`/`beat_work_segment` 并产生同步变更，返回落库记录与 `syncCursor`；客户端无需再 Push 这条记录。

**同账号任一设备都可调用**（含设备 A 故障时由设备 B 远程结束，需求 10.4）：服务端按 `deviceId ≠ startDeviceId` 识别为他端结束并记录 `lastOperatorDeviceId`。结束方设备用会话中的时间戳与计薪规则快照在本地计价后提交 `record`；`occurredAt` 为结束方选择的结束时间（不得早于会话最后一次已知操作时间，不得晚于服务端时间 + 5 分钟）。

重复提交（同 `recordId`）幂等返回已落库结果。校验失败返回 `422`（`50004/50005`），会话保持原状态，客户端可修正后重试。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `sessionId` | path | uuid | 是 | 会话ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 |  |
| `occurredAt` | int64 | 是 |  |
| `expectedVersion` | integer | 否 |  |
| `record` | WorkRecord | 是 | 客户端生成的最终工时记录（id 即 recordId）；服务端校验后落库 |

请求示例：

```json
{
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
  "occurredAt": 1795084200000,
  "expectedVersion": 3,
  "record": {
    "id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21",
    "workDate": "2026-11-19",
    "startAt": 1795050000000,
    "endAt": 1795084200000,
    "durationMs": 30600000,
    "totalEarnCents": 26250,
    "shift": "MORNING",
    "tags": [
      "项目A"
    ],
    "note": "赶工",
    "crossDay": false,
    "source": "TIMER",
    "ruleSnapshot": {
      "payMode": "STATUTORY",
      "baseRateCents": 3000,
      "overtimeThresholdMinutes": 480,
      "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}
    },
    "segments": [
      {"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]},
      {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}
    ]
  }
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `session` | TimerSession | 是 | 计时会话（beat_timer_session） |
| `record` | WorkRecord | 是 | 服务端落库后的记录（含version） |
| `syncCursor` | string | 否 | 记录对应的同步游标；客户端无需再 Push 同一记录 |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "session": {
      "sessionId": "e8a1c6d2-3b95-4f07-8d14-7a2b5c9e0f33",
      "status": "STOPPED",
      "workDate": "2026-11-19",
      "startedAt": 1795050000000,
      "segments": [{"segmentNo": 1, "startAt": 1795050000000, "endAt": null}],
      "accumulatedMs": 3600000,
      "startDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
      "startDeviceName": "我的手机",
      "lastOperatorDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
      "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}},
      "version": 4,
      "serverTime": 1795084200000
    },
    "record": {
      "id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21",
      "workDate": "2026-11-19",
      "startAt": 1795050000000,
      "endAt": 1795084200000,
      "durationMs": 30600000,
      "totalEarnCents": 26250,
      "shift": "MORNING",
      "tags": ["项目A"],
      "note": "赶工",
      "crossDay": false,
      "source": "TIMER",
      "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}},
      "segments": [{"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]}, {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}],
      "version": 3,
      "updatedAt": 1795084205000,
      "deleted": false
    },
    "syncCursor": "10241"
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 404 | 50003 |
| 409 | 50002 |
| 422 | 50004 时间戳不合法 / 50005 金额超出合理区间 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-TMR-06　【本期】取消计时（不生成记录）

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `POST /api/v1/timer/sessions/{sessionId}/cancel` |
| 认证 | Bearer Access Token |
| 幂等 | 是，需携带 `Idempotency-Key` |
| 关联需求 | TMR-001 |

状态机 `RUNNING|PAUSED → CANCELED`，释放互斥占用，不生成工时记录。同账号任一设备都可调用（他端取消需求端二次确认）。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `sessionId` | path | uuid | 是 | 会话ID |

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 |  |
| `occurredAt` | int64 | 是 | 操作实际发生的客户端时间（毫秒）；离线补报时与请求时间不同 |
| `expectedVersion` | integer | 否 | 可选：会话版本号，不一致返回 409 TIMER_STATE_INVALID |

请求示例：

```json
{
  "deviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
  "occurredAt": 1795060800000
}
```
**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：RUNNING / PAUSED / STOPPED / CANCELED |
| `workDate` | string | 否 |  |
| `startedAt` | int64 | 是 |  |
| `segments` | array<TimerSegment> | 是 |  |
| `accumulatedMs` | int64 | 否 | 截至 serverTime 的已累计工作时长（毫秒） |
| `startDeviceId` | string | 否 |  |
| `startDeviceName` | string | 否 |  |
| `lastOperatorDeviceId` | string | 否 |  |
| `ruleSnapshot` | PayRule | 否 | 计薪规则（设置与记录快照共用） |
| `version` | integer | 否 |  |
| `serverTime` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "e8a1c6d2-3b95-4f07-8d14-7a2b5c9e0f33",
    "status": "CANCELED",
    "workDate": "2026-11-19",
    "startedAt": 1795050000000,
    "segments": [
      {"segmentNo": 1, "startAt": 1795050000000, "endAt": null}
    ],
    "accumulatedMs": 3600000,
    "startDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "startDeviceName": "我的手机",
    "lastOperatorDeviceId": "d3b0c1a2-7f4e-4c55-9a10-2f6e8b1c0a11",
    "ruleSnapshot": {
      "payMode": "STATUTORY",
      "baseRateCents": 3000,
      "overtimeThresholdMinutes": 480,
      "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}
    },
    "version": 2,
    "serverTime": 1795060800000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 404 | 50003 |
| 409 | 50002 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

#### 6.6　业务数据查询

### API-DAT-01　【本期】查询云端设置

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/settings` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | SET-001, SET-014 |

**只读**。业务数据的写入（新增/修改/删除/批量）统一经 `/sync/push` 与 `/timer/sessions/{id}/stop`，避免两条写路径导致版本与冲突规则不一致（设计取舍见文档 3.1）。本接口用于云端数据查看、对账、恢复与未来扩展端。

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `version` | integer | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `payRule` | PayRule | 是 | 计薪规则（设置与记录快照共用） |
| `switches` | object | 否 | 功能开关 |
| `switches.calendarEnabled` | boolean | 否 |  |
| `switches.monthlySummaryEnabled` | boolean | 否 |  |
| `switches.rewardPopupEnabled` | boolean | 否 |  |
| `switches.crossDayDetect` | boolean | 否 |  |
| `switches.animationEnabled` | boolean | 否 |  |
| `switches.welcomeEnabled` | boolean | 否 |  |
| `timerReminderMinutes` | integer | 否 | 计时提醒时长（分钟），0关闭 |
| `motivationSource` | string | 否 | 激励词来源（ONLINE为二批） 枚举：LOCAL / CUSTOM / MIXED / ONLINE |
| `customMotivations` | array<string> | 否 | 自定义激励词 |
| `theme` | object | 否 |  |
| `theme.themeColor` | string | 否 |  |
| `theme.timeColor` | string | 否 |  |
| `theme.amountColor` | string | 否 |  |
| `onboarded` | boolean | 否 |  |
| `toolsOrder` | array<integer> | 否 | 工具排序（工具序号） |
| `toolsHidden` | array<integer> | 否 | 隐藏的工具序号 |
| `fieldUpdatedAt` | object | 否 | 字段级最后修改时间，用于字段级合并（键为字段路径） |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "version": 5,
    "updatedAt": 1795084200000,
    "payRule": {
      "payMode": "CUSTOM",
      "baseRateCents": 3000,
      "overtimeThresholdMinutes": 480,
      "customRates": {"overtime": {"raw": "1.5X", "inputType": "MULTIPLIER", "value": "1.5"}, "restDay": {"raw": "2*", "inputType": "MULTIPLIER", "value": "2"}, "holiday": {"raw": "90", "inputType": "UNIT_PRICE", "value": "90"}}
    },
    "switches": {
      "calendarEnabled": true,
      "monthlySummaryEnabled": true,
      "rewardPopupEnabled": true,
      "crossDayDetect": true,
      "animationEnabled": true,
      "welcomeEnabled": false
    },
    "timerReminderMinutes": 120,
    "motivationSource": "MIXED",
    "customMotivations": [
      "今天也要加油"
    ],
    "theme": {
      "themeColor": "#5B6CFF",
      "timeColor": "#FFFFFF",
      "amountColor": "#4ADE80"
    },
    "onboarded": true,
    "toolsOrder": [
      24,
      25,
      26,
      28,
      29
    ],
    "toolsHidden": [],
    "fieldUpdatedAt": {
      "payRule.baseRateCents": 1795084200000
    }
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-DAT-02　【本期】分页查询工时记录

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/work-records` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | REC-001~005, STA-006 |

**只读**。业务数据的写入（新增/修改/删除/批量）统一经 `/sync/push` 与 `/timer/sessions/{id}/stop`，避免两条写路径导致版本与冲突规则不一致（设计取舍见文档 3.1）。本接口用于云端数据查看、对账、恢复与未来扩展端。

支持时间范围、标签、班次、关键字筛选与排序。默认排除已删除记录。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `page` | query | integer | 否 | 页码，从1开始，默认1 |
| `size` | query | integer | 否 | 每页条数，默认20，最大100 |
| `dateFrom` | query | string | 否 | 起始归属日期（含）YYYY-MM-DD |
| `dateTo` | query | string | 否 | 结束归属日期（含） |
| `tag` | query | string | 否 | 标签（精确匹配，可重复传多个，OR 关系） |
| `shift` | query | string | 否 | 班次；枚举：MORNING / MIDDLE / NIGHT / REST |
| `q` | query | string | 否 | 关键字：匹配备注、标签、日期 |
| `includeDeleted` | query | boolean | 否 | 是否包含墓碑，默认false |
| `sort` | query | string | 否 | 排序：字段,方向；字段支持 workDate/startAt/totalEarnCents/updatedAt，默认 workDate,desc |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `items` | array<WorkRecord> | 是 |  |
| `page` | integer | 是 |  |
| `size` | integer | 是 |  |
| `total` | int64 | 是 |  |
| `hasNext` | boolean | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {"id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21", "workDate": "2026-11-19", "startAt": 1795050000000, "endAt": 1795084200000, "durationMs": 30600000, "totalEarnCents": 26250, "shift": "MORNING", "tags": ["项目A"], "note": "赶工", "crossDay": false, "source": "TIMER", "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}}, "segments": [{"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]}, {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}], "version": 3, "updatedAt": 1795084205000, "deleted": false}
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "hasNext": false
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-DAT-03　【本期】查询单条工时记录（含分段）

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/work-records/{recordId}` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | CAL-002, REC-002 |

**只读**。业务数据的写入（新增/修改/删除/批量）统一经 `/sync/push` 与 `/timer/sessions/{id}/stop`，避免两条写路径导致版本与冲突规则不一致（设计取舍见文档 3.1）。本接口用于云端数据查看、对账、恢复与未来扩展端。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `recordId` | path | uuid | 是 | 记录ID |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | uuid | 是 | 记录ID（客户端生成UUID） |
| `version` | integer | 否 | 服务端版本号，客户端提交时无需填写 |
| `updatedAt` | int64 | 否 | 最后修改时间（毫秒） |
| `deleted` | boolean | 否 | 软删除标记（墓碑） |
| `workDate` | string | 是 | 归属日期 YYYY-MM-DD（客户端按设备时区得出；跨天记录归属开始日期） |
| `startAt` | int64 | 是 | 记录开始时间（毫秒） |
| `endAt` | int64 | 是 | 记录结束时间（毫秒） |
| `durationMs` | int64 | 是 | 总工时（毫秒），不含暂停 |
| `totalEarnCents` | integer | 是 | 总金额（分）= 各分段金额之和 |
| `shift` | string | 否 | 班次，可空 枚举：MORNING / MIDDLE / NIGHT / REST（可空） |
| `tags` | array<string> | 否 | 标签 |
| `note` | string | 否 | 备注（可空） |
| `crossDay` | boolean | 否 | 是否跨天 |
| `source` | string | 否 | 来源 枚举：TIMER / COPY / YESTERDAY / MANUAL / IMPORT / MIGRATION |
| `conflictOf` | uuid | 否 | 若为冲突副本，指向原记录ID |
| `ruleSnapshot` | PayRule | 否 | 记录生成时的计薪规则快照 |
| `segments` | array<WorkSegment> | 是 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21",
    "workDate": "2026-11-19",
    "startAt": 1795050000000,
    "endAt": 1795084200000,
    "durationMs": 30600000,
    "totalEarnCents": 26250,
    "shift": "MORNING",
    "tags": [
      "项目A"
    ],
    "note": "赶工",
    "crossDay": false,
    "source": "TIMER",
    "ruleSnapshot": {
      "payMode": "STATUTORY",
      "baseRateCents": 3000,
      "overtimeThresholdMinutes": 480,
      "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}
    },
    "segments": [
      {"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]},
      {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}
    ],
    "version": 3,
    "updatedAt": 1795084205000,
    "deleted": false
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 404 | 30002 记录不存在或不属于当前用户 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-DAT-04　【本期】查询存钱目标

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/goals` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | GOL-001~004 |

**只读**。业务数据的写入（新增/修改/删除/批量）统一经 `/sync/push` 与 `/timer/sessions/{id}/stop`，避免两条写路径导致版本与冲突规则不一致（设计取舍见文档 3.1）。本接口用于云端数据查看、对账、恢复与未来扩展端。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `page` | query | integer | 否 | 页码，从1开始，默认1 |
| `size` | query | integer | 否 | 每页条数，默认20，最大100 |
| `includeDeleted` | query | boolean | 否 | 是否包含墓碑 |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `items` | array<Goal> | 是 |  |
| `page` | integer | 是 |  |
| `size` | integer | 是 |  |
| `total` | int64 | 是 |  |
| `hasNext` | boolean | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {"id": "2f6a1b84-90c3-4e7d-a5b2-3c8d7e1f0a96", "version": 2, "updatedAt": 1795084200000, "deleted": false, "name": "买新手机", "targetAmountCents": 600000, "targetDate": "2027-03-01", "rewardText": "去吃顿好的", "achievedAt": null}
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "hasNext": false
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-DAT-05　【本期】按日期范围查询排班

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/schedules` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | CAL-003 |

**只读**。业务数据的写入（新增/修改/删除/批量）统一经 `/sync/push` 与 `/timer/sessions/{id}/stop`，避免两条写路径导致版本与冲突规则不一致（设计取舍见文档 3.1）。本接口用于云端数据查看、对账、恢复与未来扩展端。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `dateFrom` | query | string | 是 | 起始日期 |
| `dateTo` | query | string | 是 | 结束日期（范围不超过366天） |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `[]id` | string | 是 | 同步实体ID=日期 YYYY-MM-DD |
| `[]version` | integer | 否 |  |
| `[]updatedAt` | int64 | 否 |  |
| `[]deleted` | boolean | 否 |  |
| `[]date` | string | 是 | 日期 YYYY-MM-DD |
| `[]shift` | string | 是 | 班次 枚举：MORNING / MIDDLE / NIGHT / REST |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "2026-11-19",
      "version": 1,
      "updatedAt": 1795084200000,
      "deleted": false,
      "date": "2026-11-19",
      "shift": "MORNING"
    }
  ],
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 400 | 10001 日期范围非法或过大 |
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-DAT-06　【本期】查询薪资台账

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/payrolls` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | LDG-001~003 |

**只读**。业务数据的写入（新增/修改/删除/批量）统一经 `/sync/push` 与 `/timer/sessions/{id}/stop`，避免两条写路径导致版本与冲突规则不一致（设计取舍见文档 3.1）。本接口用于云端数据查看、对账、恢复与未来扩展端。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `page` | query | integer | 否 | 页码，从1开始，默认1 |
| `size` | query | integer | 否 | 每页条数，默认20，最大100 |
| `monthFrom` | query | string | 否 | 起始工资月份 YYYY-MM |
| `monthTo` | query | string | 否 | 结束工资月份 |
| `received` | query | boolean | 否 | 是否到账 |
| `sort` | query | string | 否 | 排序，默认 payMonth,desc |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `items` | array<Payroll> | 是 |  |
| `page` | integer | 是 |  |
| `size` | integer | 是 |  |
| `total` | int64 | 是 |  |
| `hasNext` | boolean | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {"id": "c4d2e8f1-6a30-4b79-8e15-9f0a2b3c4d58", "version": 1, "updatedAt": 1795084200000, "deleted": false, "payMonth": "2026-10", "payDate": "2026-11-10", "grossCents": 520000, "netCents": 468000, "received": true, "note": ""}
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "hasNext": false
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

### API-DAT-07　【本期】查询通知

| 项 | 内容 |
|---|---|
| 阶段 | **本期** |
| 请求 | `GET /api/v1/notifications` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | NTF-001~003 |

**只读**。业务数据的写入（新增/修改/删除/批量）统一经 `/sync/push` 与 `/timer/sessions/{id}/stop`，避免两条写路径导致版本与冲突规则不一致（设计取舍见文档 3.1）。本接口用于云端数据查看、对账、恢复与未来扩展端。

已读状态的修改经同步 Push 提交。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `page` | query | integer | 否 | 页码，从1开始，默认1 |
| `size` | query | integer | 否 | 每页条数，默认20，最大100 |
| `read` | query | boolean | 否 | 按已读状态筛选 |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `items` | array<Notification> | 是 |  |
| `page` | integer | 是 |  |
| `size` | integer | 是 |  |
| `total` | int64 | 是 |  |
| `hasNext` | boolean | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {"id": "a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d", "version": 1, "updatedAt": 1795084200000, "deleted": false, "type": "GOAL_ACHIEVED", "title": "目标达成", "content": "你已达成目标「买新手机」", "goalId": "2f6a1b84-90c3-4e7d-a5b2-3c8d7e1f0a96", "read": false, "createdAt": 1795084200000}
    ],
    "page": 1,
    "size": 20,
    "total": 1,
    "hasNext": false
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 403 | 20009 未同意云同步或隐私政策版本已更新（先调用 PUT /sync/consent） |

---

#### 6.7　后续迭代（二批）

### API-CFG-01　【后续·二批】节假日与调休兜底数据

| 项 | 内容 |
|---|---|
| 阶段 | **后续·二批**（本期不开发，仅预留） |
| 请求 | `GET /api/v1/config/holidays` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | CFG-003 |

**二批，本期不开发，建议二批优先。** 本期节假日与调休使用 App 内置自有 JSON（结构与本接口 `days` 完全一致，可无缝切换）；不使用系统 HolidayManager（其返回条目无节假日/调休类型字段）。启用本接口后数据更新不再依赖 App 发版（数据源可参考公开的 holiday-cn 数据，据核实资料为 MIT 许可，仅使用其数据，需人工复核）。

按年份返回法定节假日与调休补班日，用于日类型判定（需求 5.2）。客户端以 `dataVersion` 缓存，缓存版本未变化时返回 304。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `year` | query | integer | 是 | 年份 |
| `knownVersion` | query | string | 否 | 客户端已缓存的 dataVersion，未变化时返回 304 |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `year` | integer | 是 |  |
| `dataVersion` | string | 是 | 数据版本，客户端缓存键 |
| `source` | string | 否 | 数据来源说明，如：国务院办公厅放假通知 |
| `updatedAt` | int64 | 否 |  |
| `days` | array<HolidayDay> | 是 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "year": 2026,
    "dataVersion": "2026-v3",
    "source": "国务院办公厅放假通知（人工录入）",
    "updatedAt": 1795050000000,
    "days": [
      {"date": "2026-02-17", "type": "HOLIDAY", "name": "春节"},
      {"date": "2026-02-14", "type": "MAKEUP_WORKDAY", "name": "春节调休"}
    ]
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
**可能的错误**

| HTTP | 错误码/说明 |
|---|---|
| 404 | 30002 该年份暂无数据 |

---

### API-STA-01　【后续·二批】云端对账统计

| 项 | 内容 |
|---|---|
| 阶段 | **后续·二批**（本期不开发，仅预留） |
| 请求 | `GET /api/v1/stats/summary` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | STA-007 |

服务端按条件汇总，用于迁移/同步对账。**二批，本期不开发**。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `dateFrom` | query | string | 是 | 起始日期 |
| `dateTo` | query | string | 是 | 结束日期 |
| `tag` | query | string | 否 | 标签 |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `totalDurationMs` | int64 | 否 |  |
| `totalEarnCents` | int64 | 否 |  |
| `workDays` | integer | 否 |  |
| `recordCount` | integer | 否 |  |
| `overtimeMs` | int64 | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalDurationMs": 345600000,
    "totalEarnCents": 320000,
    "workDays": 20,
    "recordCount": 24,
    "overtimeMs": 14400000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
---

### API-BAK-01　【后续·二批】导出云端完整备份（JSON）

| 项 | 内容 |
|---|---|
| 阶段 | **后续·二批**（本期不开发，仅预留） |
| 请求 | `GET /api/v1/backup/export` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | EXP-001（云端扩展） |

**二批，本期不开发**。导出结构对齐本地 JSON 备份。本期的 JSON 备份导入导出完全在客户端完成，导入后经同步上云，因此不需要备份/恢复接口。

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schemaVersion` | integer | 否 |  |
| `exportedAt` | int64 | 否 |  |
| `settings` | Settings | 否 | 用户设置（单例，beat_user_settings）；深色模式/悬浮窗位置等设备本地设置不在此列 |
| `workRecords` | array<WorkRecord> | 否 |  |
| `goals` | array<Goal> | 否 |  |
| `schedules` | array<Schedule> | 否 |  |
| `payrolls` | array<Payroll> | 否 |  |
| `notifications` | array<Notification> | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "schemaVersion": 1,
    "exportedAt": 1795084200000,
    "settings": {
      "version": 5,
      "updatedAt": 1795084200000,
      "payRule": {"payMode": "CUSTOM", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"raw": "1.5X", "inputType": "MULTIPLIER", "value": "1.5"}, "restDay": {"raw": "2*", "inputType": "MULTIPLIER", "value": "2"}, "holiday": {"raw": "90", "inputType": "UNIT_PRICE", "value": "90"}}},
      "switches": {"calendarEnabled": true, "monthlySummaryEnabled": true, "rewardPopupEnabled": true, "crossDayDetect": true, "animationEnabled": true, "welcomeEnabled": false},
      "timerReminderMinutes": 120,
      "motivationSource": "MIXED",
      "customMotivations": ["今天也要加油"],
      "theme": {"themeColor": "#5B6CFF", "timeColor": "#FFFFFF", "amountColor": "#4ADE80"},
      "onboarded": true,
      "toolsOrder": [24, 25, 26, 28, 29],
      "toolsHidden": [],
      "fieldUpdatedAt": {"payRule.baseRateCents": 1795084200000}
    },
    "workRecords": [
      {"id": "9c1e2f40-5b7a-4d3e-8a62-1d0f4c7b9e21", "workDate": "2026-11-19", "startAt": 1795050000000, "endAt": 1795084200000, "durationMs": 30600000, "totalEarnCents": 26250, "shift": "MORNING", "tags": ["项目A"], "note": "赶工", "crossDay": false, "source": "TIMER", "ruleSnapshot": {"payMode": "STATUTORY", "baseRateCents": 3000, "overtimeThresholdMinutes": 480, "customRates": {"overtime": {"inputType": "NONE"}, "restDay": {"inputType": "NONE"}, "holiday": {"inputType": "NONE"}}}, "segments": [{"id": "5a7d0c33-1e42-4b8f-b0a9-6c2e9d4f1a05", "segmentNo": 1, "startAt": 1795050000000, "endAt": 1795060800000, "durationMs": 10800000, "earnCents": 9000, "parts": [{"rateType": "NORMAL", "durationMs": 10800000, "rateCentsPerHour": 3000, "amountCents": 9000}]}, {"id": "7b3c9e10-2d58-4a6f-9c41-0e8f1a2d3b64", "segmentNo": 2, "startAt": 1795064400000, "endAt": 1795084200000, "durationMs": 19800000, "earnCents": 17250, "parts": [{"rateType": "NORMAL", "durationMs": 18000000, "rateCentsPerHour": 3000, "amountCents": 15000}, {"rateType": "OVERTIME", "durationMs": 1800000, "rateCentsPerHour": 4500, "amountCents": 2250}]}], "version": 3, "updatedAt": 1795084205000, "deleted": false}
    ],
    "goals": [
      {"id": "2f6a1b84-90c3-4e7d-a5b2-3c8d7e1f0a96", "version": 2, "updatedAt": 1795084200000, "deleted": false, "name": "买新手机", "targetAmountCents": 600000, "targetDate": "2027-03-01", "rewardText": "去吃顿好的", "achievedAt": null}
    ],
    "schedules": [
      {"id": "2026-11-19", "version": 1, "updatedAt": 1795084200000, "deleted": false, "date": "2026-11-19", "shift": "MORNING"}
    ],
    "payrolls": [
      {"id": "c4d2e8f1-6a30-4b79-8e15-9f0a2b3c4d58", "version": 1, "updatedAt": 1795084200000, "deleted": false, "payMonth": "2026-10", "payDate": "2026-11-10", "grossCents": 520000, "netCents": 468000, "received": true, "note": ""}
    ],
    "notifications": [
      {"id": "a1b2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d", "version": 1, "updatedAt": 1795084200000, "deleted": false, "type": "GOAL_ACHIEVED", "title": "目标达成", "content": "你已达成目标「买新手机」", "goalId": "2f6a1b84-90c3-4e7d-a5b2-3c8d7e1f0a96", "read": false, "createdAt": 1795084200000}
    ]
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
---

### API-CFG-02　【后续·二批】工作性价比城市参考数据

| 项 | 内容 |
|---|---|
| 阶段 | **后续·二批**（本期不开发，仅预留） |
| 请求 | `GET /api/v1/config/city-benchmarks` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | CFG-002, TOOL-028 |

**二批，本期不开发**（本期城市数据随 App 内置）。

**请求参数**

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `knownVersion` | query | string | 否 | 客户端已缓存版本 |

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dataVersion` | string | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `cities` | array<CityBenchmark> | 否 |  |

响应示例（HTTP 200）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "dataVersion": "2027-v1",
    "updatedAt": 1795084200000,
    "cities": [
      {"cityCode": "310000", "cityName": "上海", "avgMonthlySalaryCents": 1200000, "livingCostCents": 500000, "commuteCostCents": 40000}
    ]
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
---

### API-FBK-01　【后续·二批】上传反馈截图

| 项 | 内容 |
|---|---|
| 阶段 | **后续·二批**（本期不开发，仅预留） |
| 请求 | `POST /api/v1/feedback/attachments` |
| 认证 | Bearer Access Token |
| 幂等 | 天然幂等/无需 |
| 关联需求 | EXP-009 |

**二批，本期不开发**。`multipart/form-data`，字段 `file`；限制类型与大小另行确定。

请求体：`multipart/form-data`，字段 `file`（二进制）。

**响应 `data`**

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `attachmentId` | string | 否 |  |
| `size` | int64 | 否 |  |
| `expiresAt` | int64 | 否 |  |

响应示例（HTTP 201）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "attachmentId": "att_01",
    "size": 123456,
    "expiresAt": 1795170600000
  },
  "requestId": "7f9c2ba4e88f",
  "timestamp": 1795075800000
}
```
---

#### 6.8　数据模型（Schema）

> 接口字段表中引用的公共对象在此统一定义，Apifox 导入后位于「数据模型」。

##### RateInput

自定义费率输入（规则见需求 5.3）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `raw` | string | 否 | 用户原始输入，如 1.5X / 37.5；留空或0表示该档按基础时薪 |
| `inputType` | string | 是 | 解析结果类型 枚举：NONE / MULTIPLIER / UNIT_PRICE |
| `value` | string | 否 | 解析后的数值（十进制字符串，避免浮点误差） |

##### PayRule

计薪规则（设置与记录快照共用）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `payMode` | string | 是 | 计薪模式：法定/自定义 枚举：STATUTORY / CUSTOM |
| `baseRateCents` | integer | 是 | 基础时薪，单位：分/小时 |
| `overtimeThresholdMinutes` | integer | 是 | 每日加班阈值（分钟），默认480 |
| `customRates` | object | 否 | 自定义模式下三档费率；法定模式忽略 |
| `customRates.overtime` | RateInput | 否 | 自定义费率输入（规则见需求 5.3） |
| `customRates.restDay` | RateInput | 否 | 自定义费率输入（规则见需求 5.3） |
| `customRates.holiday` | RateInput | 否 | 自定义费率输入（规则见需求 5.3） |

##### PricingPart

分段内的计价片段（跨加班阈值时一个分段拆为多个片段）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `rateType` | string | 是 | 计价类型 枚举：NORMAL / OVERTIME / REST_DAY / HOLIDAY |
| `durationMs` | int64 | 是 | 该计价片段时长（毫秒） |
| `rateCentsPerHour` | integer | 是 | 该片段费率（分/小时） |
| `amountCents` | integer | 是 | 该片段金额（分）= round_half_up(durationMs×rate÷3600000) |

##### WorkSegment

工时分段（beat_work_segment）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | uuid | 是 | 分段ID |
| `segmentNo` | integer | 是 | 分段序号，从1开始 |
| `startAt` | int64 | 是 | 开始时间（毫秒时间戳，UTC） |
| `endAt` | int64 | 是 | 结束时间（毫秒时间戳，UTC） |
| `durationMs` | int64 | 是 | 时长（毫秒） |
| `earnCents` | integer | 是 | 分段金额（分） |
| `parts` | array<PricingPart> | 否 | 计价片段 |

##### WorkRecord

工时记录（beat_work_record + beat_work_segment）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | uuid | 是 | 记录ID（客户端生成UUID） |
| `version` | integer | 否 | 服务端版本号，客户端提交时无需填写 |
| `updatedAt` | int64 | 否 | 最后修改时间（毫秒） |
| `deleted` | boolean | 否 | 软删除标记（墓碑） |
| `workDate` | string | 是 | 归属日期 YYYY-MM-DD（客户端按设备时区得出；跨天记录归属开始日期） |
| `startAt` | int64 | 是 | 记录开始时间（毫秒） |
| `endAt` | int64 | 是 | 记录结束时间（毫秒） |
| `durationMs` | int64 | 是 | 总工时（毫秒），不含暂停 |
| `totalEarnCents` | integer | 是 | 总金额（分）= 各分段金额之和 |
| `shift` | string | 否 | 班次，可空 枚举：MORNING / MIDDLE / NIGHT / REST（可空） |
| `tags` | array<string> | 否 | 标签 |
| `note` | string | 否 | 备注（可空） |
| `crossDay` | boolean | 否 | 是否跨天 |
| `source` | string | 否 | 来源 枚举：TIMER / COPY / YESTERDAY / MANUAL / IMPORT / MIGRATION |
| `conflictOf` | uuid | 否 | 若为冲突副本，指向原记录ID |
| `ruleSnapshot` | PayRule | 否 | 记录生成时的计薪规则快照 |
| `segments` | array<WorkSegment> | 是 |  |

##### Settings

用户设置（单例，beat_user_settings）；深色模式/悬浮窗位置等设备本地设置不在此列

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `version` | integer | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `payRule` | PayRule | 是 | 计薪规则（设置与记录快照共用） |
| `switches` | object | 否 | 功能开关 |
| `switches.calendarEnabled` | boolean | 否 |  |
| `switches.monthlySummaryEnabled` | boolean | 否 |  |
| `switches.rewardPopupEnabled` | boolean | 否 |  |
| `switches.crossDayDetect` | boolean | 否 |  |
| `switches.animationEnabled` | boolean | 否 |  |
| `switches.welcomeEnabled` | boolean | 否 |  |
| `timerReminderMinutes` | integer | 否 | 计时提醒时长（分钟），0关闭 |
| `motivationSource` | string | 否 | 激励词来源（ONLINE为二批） 枚举：LOCAL / CUSTOM / MIXED / ONLINE |
| `customMotivations` | array<string> | 否 | 自定义激励词 |
| `theme` | object | 否 |  |
| `theme.themeColor` | string | 否 |  |
| `theme.timeColor` | string | 否 |  |
| `theme.amountColor` | string | 否 |  |
| `onboarded` | boolean | 否 |  |
| `toolsOrder` | array<integer> | 否 | 工具排序（工具序号） |
| `toolsHidden` | array<integer> | 否 | 隐藏的工具序号 |
| `fieldUpdatedAt` | object | 否 | 字段级最后修改时间，用于字段级合并（键为字段路径） |

##### Goal

存钱目标（beat_goal）；进度由客户端计算

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | uuid | 是 |  |
| `version` | integer | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `deleted` | boolean | 否 |  |
| `name` | string | 是 | 目标名称 |
| `targetAmountCents` | int64 | 是 | 目标金额（分） |
| `targetDate` | string | 否 | 目标日期 YYYY-MM-DD，可空（可空） |
| `rewardText` | string | 否 | 达成激励语，可空（可空） |
| `achievedAt` | int64 | 否 | 首次达成时间（毫秒），未达成为空（可空） |

##### Schedule

排班（beat_schedule），以日期为自然键

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 同步实体ID=日期 YYYY-MM-DD |
| `version` | integer | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `deleted` | boolean | 否 |  |
| `date` | string | 是 | 日期 YYYY-MM-DD |
| `shift` | string | 是 | 班次 枚举：MORNING / MIDDLE / NIGHT / REST |

##### Payroll

薪资台账（beat_payroll）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | uuid | 是 |  |
| `version` | integer | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `deleted` | boolean | 否 |  |
| `payMonth` | string | 是 | 工资月份 YYYY-MM |
| `payDate` | string | 否 | 发薪日 YYYY-MM-DD（可空） |
| `grossCents` | int64 | 是 | 应发（分） |
| `netCents` | int64 | 是 | 实发（分） |
| `received` | boolean | 是 | 是否已到账 |
| `note` | string | 否 | （可空） |

##### Notification

通知（beat_notification）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | uuid | 是 |  |
| `version` | integer | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `deleted` | boolean | 否 |  |
| `type` | string | 是 | 通知类型 枚举：GOAL_ACHIEVED |
| `title` | string | 是 |  |
| `content` | string | 否 |  |
| `goalId` | uuid | 否 | 关联目标 |
| `read` | boolean | 否 | 是否已读 |
| `createdAt` | int64 | 否 |  |

##### DeviceInfo

设备信息

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 | 客户端生成并持久化的设备唯一ID（UUID） |
| `deviceName` | string | 否 |  |
| `deviceModel` | string | 否 |  |
| `osVersion` | string | 否 | 系统版本 |
| `appVersion` | string | 是 |  |
| `deviceType` | string | 否 | 枚举：PHONE / TABLET / OTHER |

##### Device

已登录设备（sys_user_device）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 否 |  |
| `deviceName` | string | 否 |  |
| `deviceModel` | string | 否 |  |
| `deviceType` | string | 否 |  |
| `appVersion` | string | 否 |  |
| `lastActiveAt` | int64 | 否 |  |
| `lastSyncAt` | int64 | 否 |  |
| `current` | boolean | 否 | 是否当前设备 |
| `status` | string | 否 | 枚举：ACTIVE / REVOKED |

##### UserInfo

用户信息（不含手机号）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 否 | 用户ID（字符串形式，避免大整数精度问题） |
| `nickname` | string | 否 | 昵称（需授权请求携带 profile scope 才可能有值；本期功能不依赖，默认为空）（可空） |
| `avatarUrl` | string | 否 | 头像地址（同上；有效期官方未说明，仅用于展示，不依赖其长期有效）（可空） |
| `status` | string | 否 | 枚举：ACTIVE / PENDING_DELETION |
| `createdAt` | int64 | 否 |  |
| `deletionScheduledAt` | int64 | 否 | 计划物理删除时间（注销冷静期内返回）（可空） |

##### HuaweiLoginRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `loginType` | string | 是 | 本次授权方式：交互式 / 华为账号静默授权（仅用于审计统计，两者后端处理一致） 枚举：INTERACTIVE / SILENT |
| `authorizationCode` | string | 是 | 华为账号标准登录授权后客户端获得的 Authorization Code；一次性、用后即焚、有效期极短；换取失败即作废，不得重试，需重新授权 |
| `device` | DeviceInfo | 是 | 设备信息 |

##### LoginResult



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `accessToken` | string | 是 | Beat计薪自有 Access Token |
| `refreshToken` | string | 是 | Beat计薪自有 Refresh Token（仅此处与续期接口返回明文） |
| `tokenType` | string | 否 |  |
| `expiresIn` | integer | 是 | Access Token 有效期（秒） |
| `refreshExpiresIn` | integer | 是 | Refresh Token 有效期（秒） |
| `user` | UserInfo | 是 | 用户信息（不含手机号） |
| `isNewUser` | boolean | 是 | 是否本次新建账号 |
| `hasCloudData` | boolean | 是 | 云端是否已有业务数据（用于客户端决定迁移/拉取路径） |
| `syncConsented` | boolean | 否 | 是否已同意云同步（未同意时客户端保持本地模式，不得调用业务数据接口） |
| `serverTime` | int64 | 否 |  |

##### RefreshRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `refreshToken` | string | 是 |  |
| `deviceId` | string | 是 |  |

##### TokenPair



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `accessToken` | string | 是 |  |
| `refreshToken` | string | 是 | 轮换后的新 Refresh Token，旧Token立即作废 |
| `tokenType` | string | 否 |  |
| `expiresIn` | integer | 是 |  |
| `refreshExpiresIn` | integer | 是 |  |
| `serverTime` | int64 | 否 |  |

##### DeleteAccountRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `confirm` | boolean | 是 | 必须为 true |
| `reason` | string | 否 | 注销原因，可空（可空） |

##### EntityCounts

各类数据数量

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `settings` | integer | 否 |  |
| `workRecords` | integer | 否 |  |
| `segments` | integer | 否 |  |
| `goals` | integer | 否 |  |
| `schedules` | integer | 否 |  |
| `payrolls` | integer | 否 |  |
| `notifications` | integer | 否 |  |

##### MigrationCreateRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `migrationId` | uuid | 是 | 客户端生成的迁移任务唯一ID，重复创建返回同一任务（幂等） |
| `deviceId` | string | 是 |  |
| `appVersion` | string | 否 |  |
| `schemaVersion` | integer | 否 | 本地数据结构版本 |
| `expectedCounts` | EntityCounts | 是 | 游客数据各类数量 |

##### MigrationTask



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `migrationId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：CREATED / UPLOADING / COMPLETED / FAILED |
| `expectedCounts` | EntityCounts | 否 | 各类数据数量 |
| `receivedCounts` | EntityCounts | 否 | 已收到（含去重/冲突的）数量 |
| `acceptedCount` | integer | 否 | 新写入条数 |
| `dedupedCount` | integer | 否 | 判定为重复而跳过的条数 |
| `conflictCopyCount` | integer | 否 | 生成冲突副本条数 |
| `lastBatchNo` | integer | 否 | 已成功处理的最大连续批号 |
| `failureReason` | string | 否 | （可空） |
| `createdAt` | int64 | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `expiresAt` | int64 | 否 | 任务过期时间 |

##### MigrationItem



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `entityType` | string | 是 | 枚举：SETTINGS / WORK_RECORD / GOAL / SCHEDULE / PAYROLL / NOTIFICATION |
| `entityId` | string | 是 | 实体ID（UUID；排班为日期；设置为 settings） |
| `clientUpdatedAt` | int64 | 否 |  |
| `payload` | object | 是 | 实体内容，结构见同步实体定义 |

##### MigrationBatchRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `batchNo` | integer | 是 | 批号，从1递增；重复批号幂等返回上次结果 |
| `items` | array<MigrationItem> | 是 | 本批条目，建议≤200 |

##### MigrationBatchResult



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `batchNo` | integer | 是 |  |
| `accepted` | integer | 是 |  |
| `deduplicated` | integer | 是 |  |
| `conflictCopies` | integer | 是 |  |
| `rejected` | array<object> | 否 | 被拒绝条目及原因 |
| `rejected[].entityId` | string | 否 |  |
| `rejected[].reason` | string | 否 |  |
| `task` | MigrationTask | 否 |  |

##### MigrationCommitRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `expectedCounts` | EntityCounts | 是 | 各类数据数量 |
| `totals` | object | 否 | 关键字段校验值：游客记录总时长/总金额 |
| `totals.totalDurationMs` | int64 | 否 |  |
| `totals.totalEarnCents` | int64 | 否 |  |

##### MigrationCommitResult



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | string | 是 | 枚举：COMPLETED / FAILED |
| `task` | MigrationTask | 是 |  |
| `serverCounts` | EntityCounts | 否 | 迁移后服务端该账号各类数量（含云端原有） |
| `mismatches` | array<string> | 否 | 校验不一致项说明（FAILED时） |
| `syncCursor` | string | 否 | 可用于随后Pull的游标 |

##### SyncChange



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `changeId` | uuid | 是 | 变更唯一ID（幂等单位） |
| `entityType` | string | 是 | 枚举：SETTINGS / WORK_RECORD / GOAL / SCHEDULE / PAYROLL / NOTIFICATION |
| `entityId` | string | 是 | 业务记录ID |
| `op` | string | 是 | 枚举：UPSERT / DELETE |
| `baseVersion` | integer | 是 | 客户端基于的服务端版本号；新建为0 |
| `clientUpdatedAt` | int64 | 否 |  |
| `payload` | object(oneOf: 见同步实体) | 否 | DELETE 时可空；UPSERT 时为实体内容 |

##### SyncPushRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `requestId` | uuid | 是 | 请求唯一ID（幂等） |
| `deviceId` | string | 是 |  |
| `changes` | array<SyncChange> | 是 | 变更列表，单次≤100 |

##### SyncPushItemResult



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `changeId` | uuid | 是 |  |
| `entityType` | string | 否 | 枚举：SETTINGS / WORK_RECORD / GOAL / SCHEDULE / PAYROLL / NOTIFICATION |
| `entityId` | string | 否 |  |
| `status` | string | 是 | 枚举：ACCEPTED / DUPLICATE / CONFLICT / REJECTED |
| `newVersion` | integer | 否 | ACCEPTED/DUPLICATE/CONFLICT 时返回服务端当前版本 |
| `resolution` | string | 否 | 冲突处理结果 枚举：NONE / CONFLICT_COPY_CREATED / FIELD_MERGED / SERVER_KEPT / RESTORED_BY_UPDATE |
| `serverPayload` | object | 否 | CONFLICT 时返回服务端权威内容，客户端据此覆盖本地 |
| `conflictCopy` | object | 否 | 生成的冲突副本（工时记录）完整内容 |
| `errorCode` | integer | 否 | REJECTED 时的业务错误码 |
| `errorMessage` | string | 否 |  |

##### SyncPushResult



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `requestId` | uuid | 是 |  |
| `results` | array<SyncPushItemResult> | 是 |  |
| `serverCursor` | string | 否 | 提交后服务端最新游标（仅供参考，客户端仍以Pull结果推进本地游标） |
| `serverTime` | int64 | 否 |  |

##### SyncServerChange



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `seq` | string | 否 | 变更序号（不透明字符串，按字典无关、仅以 nextCursor 为准） |
| `entityType` | string | 是 | 枚举：SETTINGS / WORK_RECORD / GOAL / SCHEDULE / PAYROLL / NOTIFICATION |
| `entityId` | string | 是 |  |
| `op` | string | 是 | 枚举：UPSERT / DELETE |
| `version` | integer | 是 |  |
| `updatedAt` | int64 | 否 |  |
| `originDeviceId` | string | 否 | 产生该变更的设备 |
| `payload` | object | 否 | UPSERT 时为完整实体；DELETE 为空 |

##### SyncPullResult



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `changes` | array<SyncServerChange> | 是 |  |
| `nextCursor` | string | 是 | 本页最后一条的游标；客户端写入本地成功后再保存 |
| `hasMore` | boolean | 是 | 是否还有下一页 |
| `fullSyncRequired` | boolean | 是 | 游标过旧/无效，需清空游标全量重拉 |
| `serverTime` | int64 | 否 |  |

##### SyncConsentRequest

开启/撤回云同步同意

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `consented` | boolean | 是 | true=同意开启云同步；false=撤回同意 |
| `policyVersion` | string | 否 | 同意时展示的隐私政策版本；consented=true 时必填，须等于服务端当前版本 |

##### SyncConsentVO

云同步同意状态

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `consented` | boolean | 是 | 当前是否有效同意（已同意且版本为当前版本） |
| `consentedAt` | int64 | 否 | 同意时间（可空） |
| `consentedPolicyVersion` | string | 否 | 已同意的隐私政策版本（可空） |
| `currentPolicyVersion` | string | 是 | 服务端当前隐私政策版本 |
| `revokedAt` | int64 | 否 | 最近一次撤回时间（可空） |

##### ClearCloudDataRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `confirm` | boolean | 是 | 必须为 true |

##### ClearCloudDataResult

清除云端业务数据结果

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deleted` | EntityCounts | 是 | 各类被删除的数据数量 |
| `clearedAt` | int64 | 是 | 清除完成时间 |
| `consented` | boolean | 是 | 清除后的同意状态，恒为 false |

##### SyncStatus



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `serverCursor` | string | 是 | 服务端最新游标 |
| `lastPushAt` | int64 | 否 |  |
| `lastPullAt` | int64 | 否 |  |
| `entityCounts` | EntityCounts | 否 | 云端各类数据数量（用于对账） |
| `hasActiveTimer` | boolean | 否 | 账号是否存在活动计时会话 |

##### TimerSegment



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `segmentNo` | integer | 是 |  |
| `startAt` | int64 | 是 |  |
| `endAt` | int64 | 否 | 进行中的分段为空（可空） |

##### TimerSession

计时会话（beat_timer_session）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | uuid | 是 |  |
| `status` | string | 是 | 枚举：RUNNING / PAUSED / STOPPED / CANCELED |
| `workDate` | string | 否 |  |
| `startedAt` | int64 | 是 |  |
| `segments` | array<TimerSegment> | 是 |  |
| `accumulatedMs` | int64 | 否 | 截至 serverTime 的已累计工作时长（毫秒） |
| `startDeviceId` | string | 否 |  |
| `startDeviceName` | string | 否 |  |
| `lastOperatorDeviceId` | string | 否 |  |
| `ruleSnapshot` | PayRule | 否 | 计薪规则（设置与记录快照共用） |
| `version` | integer | 否 |  |
| `serverTime` | int64 | 否 |  |

##### TimerStartRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `sessionId` | uuid | 是 | 客户端生成，重复提交幂等 |
| `deviceId` | string | 是 |  |
| `startedAt` | int64 | 是 | 客户端开始时间（毫秒） |
| `workDate` | string | 是 |  |
| `ruleSnapshot` | PayRule | 是 | 计薪规则（设置与记录快照共用） |

##### TimerActionRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 |  |
| `occurredAt` | int64 | 是 | 操作实际发生的客户端时间（毫秒）；离线补报时与请求时间不同 |
| `expectedVersion` | integer | 否 | 可选：会话版本号，不一致返回 409 TIMER_STATE_INVALID |

##### TimerStopRequest



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 |  |
| `occurredAt` | int64 | 是 |  |
| `expectedVersion` | integer | 否 |  |
| `record` | WorkRecord | 是 | 客户端生成的最终工时记录（id 即 recordId）；服务端校验后落库 |

##### TimerStopResult



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `session` | TimerSession | 是 | 计时会话（beat_timer_session） |
| `record` | WorkRecord | 是 | 服务端落库后的记录（含version） |
| `syncCursor` | string | 否 | 记录对应的同步游标；客户端无需再 Push 同一记录 |

##### TimerConflict



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `currentSession` | TimerSession | 否 | 当前占用互斥的活动会话 |

##### HolidayDay



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `date` | string | 是 |  |
| `type` | string | 是 | 法定节假日 / 调休补班 枚举：HOLIDAY / MAKEUP_WORKDAY |
| `name` | string | 否 |  |

##### HolidayYear



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `year` | integer | 是 |  |
| `dataVersion` | string | 是 | 数据版本，客户端缓存键 |
| `source` | string | 否 | 数据来源说明，如：国务院办公厅放假通知 |
| `updatedAt` | int64 | 否 |  |
| `days` | array<HolidayDay> | 是 |  |

##### StatsSummary

云端对账统计（二批）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `totalDurationMs` | int64 | 否 |  |
| `totalEarnCents` | int64 | 否 |  |
| `workDays` | integer | 否 |  |
| `recordCount` | integer | 否 |  |
| `overtimeMs` | int64 | 否 |  |

##### CityBenchmark



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `cityCode` | string | 否 |  |
| `cityName` | string | 否 |  |
| `avgMonthlySalaryCents` | int64 | 否 |  |
| `livingCostCents` | int64 | 否 |  |
| `commuteCostCents` | int64 | 否 |  |

##### CityBenchmarks



| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `dataVersion` | string | 否 |  |
| `updatedAt` | int64 | 否 |  |
| `cities` | array<CityBenchmark> | 否 |  |

##### Attachment

反馈附件（二批）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `attachmentId` | string | 否 |  |
| `size` | int64 | 否 |  |
| `expiresAt` | int64 | 否 |  |

##### BackupFile

云端备份导出（二批），结构对齐本地JSON备份

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schemaVersion` | integer | 否 |  |
| `exportedAt` | int64 | 否 |  |
| `settings` | Settings | 否 | 用户设置（单例，beat_user_settings）；深色模式/悬浮窗位置等设备本地设置不在此列 |
| `workRecords` | array<WorkRecord> | 否 |  |
| `goals` | array<Goal> | 否 |  |
| `schedules` | array<Schedule> | 否 |  |
| `payrolls` | array<Payroll> | 否 |  |
| `notifications` | array<Notification> | 否 |  |

##### ErrorResponse

统一错误响应：`code`（业务错误码）、`message`、`data`（可空，部分错误携带上下文）、`requestId`、`timestamp`。


---

## 7. 错误码与 HTTP 状态码

### 7.1 HTTP 状态码使用

| HTTP | 场景 |
|---|---|
| 200 / 201 | 成功 / 创建成功 |
| 304 | `GET /config/holidays` 客户端缓存版本未变化 |
| 400 | 参数/格式错误、缺少幂等键 |
| 401 | 未认证、Token 无效/过期、会话被撤销 |
| 403 | 账号被冻结/已注销、无权限操作 |
| 404 | 资源不存在**或不属于当前用户** |
| 409 | 状态冲突：计时互斥、会话状态非法、迁移状态非法、游标无效 |
| 413 | 请求体/变更条数超限 |
| 422 | 语义校验失败：幂等键内容不一致、时间戳/金额不合理、迁移校验不通过 |
| 429 | 限流 |
| 500 / 503 | 服务内部错误 / 暂不可用 |

> 同步 Push 的**单条冲突/拒绝不会使整体失败**：整体返回 200，逐条结果见 `results[].status`。

### 7.2 业务错误码

| code | HTTP | 名称 | 说明与客户端建议 |
|---|---|---|---|
| 0 | 200/201 | OK | 成功 |
| 10001 | 400 | INVALID_PARAM | 参数校验失败，`message` 含字段说明 |
| 10002 | 400 | MALFORMED_BODY | 请求体不是合法 JSON |
| 10003 | 400 | IDEMPOTENCY_KEY_REQUIRED | 缺少 `Idempotency-Key` |
| 10004 | 422 | IDEMPOTENCY_KEY_REUSED | 同一幂等键对应不同请求内容 |
| 10005 | 413 | PAYLOAD_TOO_LARGE | 变更条数/批次条数超限，拆分后重试 |
| 20001 | 401 | UNAUTHENTICATED | 未携带 Token |
| 20002 | 401 | ACCESS_TOKEN_EXPIRED | 用 Refresh Token 续期后重试原请求 |
| 20003 | 401 | TOKEN_INVALID | Token 无效，重新登录 |
| 20004 | 401 | REFRESH_TOKEN_INVALID | Refresh Token 无效/过期；转第二层静默授权或交互登录 |
| 20005 | 401 | REFRESH_TOKEN_REPLAYED | 检测到旧 Token 重放，会话已吊销，需重新登录 |
| 20006 | 401 | HUAWEI_AUTH_FAILED | 华为授权校验失败/过期，重新授权 |
| 20007 | 401 | SESSION_REVOKED | 当前设备会话被撤销（被移除或全部退出），清除本地凭证 |
| 20008 | 403 | ACCOUNT_RESTRICTED | 账号冻结或处于注销冷静期 |
| 20009 | 403 | SYNC_CONSENT_REQUIRED | 未同意云同步，或隐私政策版本已更新；客户端转本地模式，必要时重新展示政策并调用 `PUT /sync/consent` |
| 30001 | 403 | FORBIDDEN | 无权限（极少使用，越权资源统一 404） |
| 30002 | 404 | NOT_FOUND | 资源不存在或不属于当前用户 |
| 40001 | 409 | VERSION_CONFLICT | 版本冲突（非同步类写入使用；同步类走 `results[]`） |
| 40002 | 409 | CURSOR_INVALID | 游标无效/过期，等价 `fullSyncRequired=true`，全量重拉 |
| 40003 | 409 | MIGRATION_STATE_INVALID | 迁移任务状态/批号非法，或存在其他未完成迁移 |
| 40004 | 422 | MIGRATION_VERIFY_FAILED | 迁移数量/关键字段校验不通过，补传后重新 commit |
| 40005 | 404 | MIGRATION_NOT_FOUND | 迁移任务不存在或已过期 |
| 50001 | 409 | TIMER_ALREADY_RUNNING | 已有活动会话，`data.currentSession` 返回占用方 |
| 50002 | 409 | TIMER_STATE_INVALID | 当前状态不允许该操作/会话已被他端结束/`expectedVersion` 不一致 |
| 50003 | 404 | TIMER_NOT_FOUND | 会话不存在 |
| 50004 | 422 | TIMER_TIME_INVALID | 时间戳不合理（未来时间、顺序错误、分段不连续、时长不一致） |
| 50005 | 422 | AMOUNT_OUT_OF_RANGE | 金额超出合理区间 |
| 50006 | 403 | TIMER_NOT_OWNER_DEVICE | 暂停/继续仅限会话所属设备；他端只能结束或取消 |
| 60001 | 429 | RATE_LIMITED | 请求过于频繁，按 `Retry-After` 退避 |
| 90001 | 500 | INTERNAL_ERROR | 服务内部错误，可退避重试（写接口依赖幂等键安全重试） |
| 90002 | 503 | SERVICE_UNAVAILABLE | 服务暂不可用 |

---

## 8. 数据库表与接口对应

### 8.1 对应关系

| 表 | 作用 | 读写它的接口 |
|---|---|---|
| `sys_user` | 应用用户（含状态、注销时间、云同步同意时间/版本/撤回时间） | AUTH-01（创建）、AUTH-05/06、SYNC-04/05/06 |
| `sys_user_identity` | 华为账号标识 ↔ 用户：以 UnionID 为唯一键，保存 OpenID 作辅助 | AUTH-01 |
| `sys_user_session` | 设备会话、Refresh Token **哈希**、过期/吊销 | AUTH-01~04、DEV-03 |
| `sys_user_device` | 设备信息、最后活跃/同步时间 | DEV-01~03、AUTH-01、SYNC-01/02 |
| `beat_user_settings` | 设置（单例/用户）、字段级时间 | SYNC-01/02、DAT-01、MIG-02 |
| `beat_work_record` | 工时记录、版本、软删除、`conflict_of` | SYNC-01/02、TMR-05、DAT-02/03、MIG-02 |
| `beat_work_segment` | 记录分段与计价片段（`pricing_json`） | 同上（随记录整体读写） |
| `beat_timer_session` | 计时会话；**唯一索引 (user_id, active_flag)** 保证互斥 | TMR-01~06、SYNC-03 |
| `beat_goal` | 存钱目标 | SYNC-01/02、DAT-04、MIG-02 |
| `beat_schedule` | 排班（唯一键：user_id + date） | SYNC-01/02、DAT-05、MIG-02 |
| `beat_payroll` | 薪资台账（唯一键：user_id + pay_month） | SYNC-01/02、DAT-06、MIG-02 |
| `beat_notification` | 通知 | SYNC-01/02、DAT-07、MIG-02 |
| `beat_sync_change` | 同步变更日志：自增序号（游标）、user_id、entity_type、entity_id、op、version、origin_device_id、payload | SYNC-01/02/03、TMR-05、MIG-02/04 |
| `beat_sync_receipt` | 请求幂等记录：user_id + 幂等键 + 请求摘要 + 响应 + 过期时间；`changeId` 处理记录 | 所有"幂等"POST |
| `beat_migration_task` | 迁移任务、各批次接收统计、状态 | MIG-01~04 |
| `beat_holiday`（**新增表，二批**） | 服务端兜底的年度节假日/调休数据（年、日期、类型、名称、数据版本）；本期不建 | CFG-01（二批） |

> 原背景文档表清单之外，仅二批**新增 `beat_holiday`**；`beat_sync_receipt` 同时承担通用幂等键存储，不再另建表。所有业务表均含：`user_id`（所有查询强制带）、`created_at`、`updated_at`、`version`、`deleted`（软删除）。

### 8.2 关键约束

| 表 | 约束 |
|---|---|
| `beat_timer_session` | `UNIQUE (user_id, active_flag)`（见第 4.2 节）；`session_id` 唯一 |
| `beat_work_record` | `PK(id)`；索引 `(user_id, work_date)`、`(user_id, updated_at)` |
| `beat_sync_change` | 自增主键即游标；索引 `(user_id, id)` |
| `beat_sync_receipt` | `UNIQUE (user_id, idem_key)`；定期清理过期行 |
| `sys_user_identity` | `UNIQUE (provider, union_id)`；`INDEX (provider, open_id)` |
| `beat_migration_task` | `UNIQUE (user_id, migration_id)`；同一用户至多一个未完成任务（应用层校验） |

### 8.3 字段命名对照

接口字段 camelCase ↔ 数据库 snake_case 一一对应，如 `totalEarnCents` ↔ `total_earn_cents`、`workDate` ↔ `work_date`、`baseRateCents` ↔ `base_rate_cents`；`Settings.payRule` 等嵌套对象在库中可展开为列或 JSON 列（实现阶段决定），**接口字段名不变**。

---

## 9. 计算职责划分

| 计算/校验 | 客户端 | 服务端 | 说明 |
|---|---|---|---|
| 计时中实时时长、预计收入 | **负责** | — | 基于时间戳，本地即时计算 |
| 日类型判定（平日/休息日/节假日） | **负责**（App 内置自有 JSON） | 本期不参与（二批可通过 CFG-01 下发） | 判定在本地；记录保存时已固化计价片段，不受后续数据更新影响 |
| 分段、加班阈值拆分、计价片段、金额 | **负责** | **校验合理性** | 服务端校验区间，不强制逐分重算 |
| 记录总额、总时长 | 负责（保存时计算） | **校验一致性**（总额=片段和；总时长=片段和） | 不一致拒绝（422） |
| 周期汇总、趋势图、月度小结、标签筛选统计 | **负责**（本地库） | 二批提供对账统计（STA-01） | 保证离线可用 |
| 存钱目标进度、是否达成 | **负责** | 记录 `achievedAt`（客户端上报） | 达成通知经同步，按 `goalId` 去重 |
| 工具箱本期 5 项 | **全部本地** | 不参与 | 无接口 |
| 计时互斥、会话状态机 | 展示/本地缓存 | **负责（权威）** | 数据库唯一索引 |
| 时间戳合理性（未来时间等） | — | **负责** | 以服务端时间为准 |
| 同步版本、冲突、幂等、去重 | 队列与应用结果 | **负责（权威）** | |
| 身份认证、Token、权限 | 保存与携带 Token | **负责** | |
| 迁移去重与数量/总额校验 | 提供预期数量/总额 | **负责** | |

---

## 10. 核实结论与待实测清单

### 10.1 核实结论（依据官方文档核实信息）

| 项 | 结论 |
|---|---|
| 华为账号登录是否需要申请 scope | 基础登录不需要；昵称/头像需请求 `profile` scope；一键登录/手机号才需额外申请且仅企业开发者，本项目不使用 |
| 授权码 | 一次性、用后即焚、有效期极短；换取失败即作废，不可重试 |
| 华为侧令牌 | Access Token 60 分钟、Refresh Token 180 天（本系统不存储，只用于换取身份） |
| 静默登录 | 成功条件：系统账号已登录且已授权过；可启动时无界面调用，失败再拉起授权界面 |
| 账号标识 | **UnionID 作唯一键**；OpenID 随系统大版本、包名、签名变化；个人开发者可获取 UnionID |
| Token 本地存储 | Asset Store Kit，不设持久化（卸载即删）；重装后需重新登录并全量 Pull |
| 节假日 | HolidayManager 无节假日/调休类型字段，不采用；改用自有 JSON |
| 代理提醒 | 5.1.1(19) 及之前完全不生效，登记返回 1700002；生效前提见需求 TMR-022 |
| 注销 | 平台要求处理时限 ≤ 15 个工作日 |

### 10.1b 待实测（联调时验证，不影响接口设计）

| 项 | 说明 |
|---|---|
| 默认授权范围下 UnionID 是否一定返回 | 影响账号唯一键，登录联调时第一时间验证 |
| `profile` 授权页表现、用户信息接口返回字段、头像地址有效期 | 影响 `UserInfo` |
| 华为账号侧取消授权/注销后再登录的实际表现 | 影响"账号失效"流程 |
| 代理提醒最短/最长时长与精度 | 客户端提醒逻辑 |
| 移动应用备案要求 | 上架前置，开工即向通信管理局/备案平台确认 |
| holiday-cn JSON 的实际字段与转换方式 | 影响节假日内置数据的整理（二批 `API-CFG-01` 同构） |

### 10.2 已定稿事项（与需求文档第 10 章对应）

| 项 | 决定 | 接口影响 |
|---|---|---|
| Token 时长 | Access 30 分钟 / Refresh 30 天（滚动） | AUTH-01/02 |
| 暂停是否占用互斥 | 占用 | TMR-02 |
| 他端对会话的权限 | 可查看 + 可结束/取消，不可远程暂停/继续 | TMR-03~06、错误码 50006 |
| 工时记录冲突 | 保留冲突副本；删改冲突保留修改 | SYNC-01 `resolution` |
| 墓碑保留期 | 90 天 | SYNC-02 `fullSyncRequired` |
| 注销冷静期 | 7 天，且 15 个工作日内物理删除（平台上限） | AUTH-06 |
| 金额舍入 / 单条记录最大跨度 | 四舍五入到分，总额为片段之和 / 48 小时 | TMR-05、SYNC-01 校验 |
| 节假日数据 | 本期内置自有 JSON，二批可由 CFG-01 下发 | CFG-01（二批） |
| 迁移后本地备份 | 保留 7 天 | 客户端行为，MIG-04 之后 |
| 休息日/节假日是否叠加加班阈值 | 不叠加，日类型费率优先 | 客户端计算与服务端区间校验 |
| 云同步同意 | 账号级、带隐私政策版本；撤回=关闭；清除=关闭并删除；未同意前不保存设备详情 | SYNC-04~06、错误码 20009 |

---

## 11. 版本与演进约定

- 接口前缀含主版本 `/api/v1`；向后兼容的新增字段不升版本，客户端必须忽略未知字段。
- 破坏性变更发布 `/api/v2`，旧版本保留至少 6 个月。
- 同步实体载荷自带 `schemaVersion`（迁移请求）与 `version`（实体版本），旧客户端导入旧结构时由客户端兜底缺省字段。
- 二批接口（`API-STA-01`、`API-BAK-01`、`API-CFG-01`、`API-CFG-02`、`API-FBK-01`）编号已预留，**本期不开发、不上线**。
