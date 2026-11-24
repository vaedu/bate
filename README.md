# beat-pay-server

Beat计薪 后端服务骨架（Spring Boot 单体 + MyBatis-Plus + MySQL）。对应《需求规格说明书》V1.1、《后端接口设计文档》V1.1（本期 32 个接口，含云同步同意管理 3 个）。

> 这是**骨架**：实体、DTO、枚举、统一返回、错误码、全局异常、JWT 认证、控制器与服务接口都已就位，
> 服务实现类（`service/impl`）全部是 `TODO` 桩，调用会返回 90001。

## 技术栈

Java 17 · Spring Boot 3.5 · MyBatis-Plus 3.5.9 · MySQL 8 · JJWT 0.12 · Lombok · Maven
（版本号请以 Maven Central 最新稳定版为准，pom 里写的是起点。）

## 快速开始

```bash
# 1. 建库建表（本期 15 张表；schema-phase2.sql 是二批表，现在不要执行）
mysql -uroot -p < sql/schema.sql

# 2. 配置环境变量（见 .env.example），JWT_SECRET 至少 32 字节
export DB_PASSWORD=... JWT_SECRET=...

# 3. 启动，接口前缀 /api/v1
mvn spring-boot:run
```

## 目录结构

```
sql/                 建表脚本（schema.sql 本期 / schema-phase2.sql 二批）
src/main/java/com/beat/pay/
  BeatPayApplication
  common/            Result、PageResult、ErrorCode、BizException、GlobalExceptionHandler、RequestIdFilter
  config/            MybatisPlus（分页/自动填充）、WebMvc（认证拦截）、JwtProperties、HuaweiProperties、BizProperties
  security/          JwtUtil、AuthInterceptor、ConsentInterceptor（云同步同意校验）、UserContext（userId 只取自 Token）
  client/            HuaweiAccountClient（授权码换取，TODO）
  controller/        7 个控制器 = 32 个接口，注释里带接口编号与需求编号
  service/ + impl/   服务接口与 TODO 桩
  entity/            15 个实体（与建表脚本逐字段对照过）+ BaseEntity / SyncEntity / CreatedOnlyEntity
  mapper/            15 个 Mapper
  model/             JSON 列值对象（PayRule、PricingPart、TimerSegmentData…）
  dto/               按接口分组：auth device migration sync timer record query
  enums/             全部枚举（TimerStatus 带 isActive()）
  util/              PricingUtil（计价）、HashUtil、IdUtils
  job/               注销清理、同步清理（定时任务桩）
src/test/            PricingUtilTest（需求 5.3 的 150 元示例）
```

## 关键约定（实现时务必遵守）

1. **越权防护**：业务实体主键是客户端 UUID，**禁止对带 user_id 的实体直接用 `selectById/updateById/deleteById`**，
   一律用 `LambdaQueryWrapper` 同时带 `user_id`；用户 ID 只用 `UserContext.userId()`。
2. **版本与删除**：不要用 MyBatis-Plus 的 `@Version` / `@TableLogic`。版本号和墓碑由同步服务显式控制
   （`baseVersion == 当前 version` 才直接接受，否则按需求 5.9 处理冲突）。
3. **金额与时间**：金额/费率一律整数“分”，时长毫秒，时间点毫秒时间戳（UTC）；计价用 `PricingUtil`（四舍五入到分）。
4. **计时互斥**：靠 `beat_timer_session` 的生成列 `active_flag` + 唯一索引 `(user_id, active_flag)`。
   `active_flag` 是生成列，**实体不映射、不可写入**；开始计时时捕获唯一键冲突，再查询占用会话，返回 `50001`。
5. **同步游标**：`beat_sync_change.id` 是自增游标。并发事务可能乱序提交，Push 处理时应对同一用户串行化
   （例如事务内先 `SELECT ... FOR UPDATE` 锁该用户的 `sys_user` 行），否则 Pull 可能漏掉晚提交的行。
6. **幂等**：请求级/变更级/迁移批次级都落 `beat_sync_receipt`（用 `receipt_type` 区分）。
7. **JSON 列**：用 `JacksonTypeHandler`，泛型字段（`List<String>` 等）需要 MyBatis-Plus ≥ 3.5.6；表上有 JSON 列的实体都开了 `autoResultMap`。
8. **授权码一次性**：`HuaweiAccountClient` 失败即抛 `20006`，**不重试**；日志不要打印 Token、授权码、client_secret。
9. **所有时间为 UTC**；`workDate` 是客户端按设备时区算好的日期，服务端不重算。

## 与接口文档 8.1/8.2 的差异（已在 SQL 注释里说明）

| 位置 | 差异 | 原因 |
|---|---|---|
| `beat_payroll` | 只建 `(user_id, pay_month)` 普通索引，不建唯一键 | 软删除后再加同月份会冲突，去重交给应用层（需求 5.8） |
| `beat_sync_receipt` | 唯一键为 `(user_id, receipt_type, idem_key)` | 同表承载请求级/变更级/迁移批次幂等 |
| `sys_user` | 增加 `sync_consent_at`、`sync_consent_version` | 云同步单独同意（PRV-004）需要留痕 |
| 业务表主键 | 单列主键（UUID），不是 `(user_id, id)` 复合主键 | MyBatis-Plus 不支持复合主键；靠查询必带 `user_id` 保证隔离 |
| `beat_holiday` | 放在 `schema-phase2.sql` | 本期节假日用 App 内置 JSON |

## 建议实现顺序

| 顺序 | 内容 | 接口 |
|---|---|---|
| 1 | 会话校验（`AuthInterceptor` TODO）、幂等工具类、`HuaweiAccountClient` | — |
| 2 | 登录、续期（轮换 + 重放检测）、退出 | API-AUTH-01~04 |
| 3 | 用户信息、注销（冷静期 + 15 个工作日内删除）、设备管理 | API-AUTH-05/06、API-DEV-01~03 |
| 4 | 同步 Push / Pull / Status（先做 WORK_RECORD 与 SETTINGS）；同意管理与清除云端数据 | API-SYNC-01~06 |
| 5 | 计时会话（互斥、状态机、结束时落库 + 校验） | API-TMR-01~06 |
| 6 | 游客数据迁移 | API-MIG-01~04 |
| 7 | 只读查询 | API-DAT-01~07 |
| 8 | 定时任务（注销清理、墓碑与幂等记录清理） | — |

## 还没做 / 待补

- **PRV-009 已补进文档与骨架**：`GET/PUT /sync/consent`、`DELETE /sync/data`，以及 `ConsentInterceptor`（业务数据接口未同意返回 20009）。
  实现要点：同意记录在 `sys_user.sync_consent_*`；撤回=关闭；清除=关闭并删除云端业务数据（不删账号、会话、设备）。
  已有库升级：执行 `sql/upgrade-prv009.sql`。
- `HuaweiAccountClientImpl` 按华为官方文档实现（地址、参数、鉴权为配置项，未写死）。
- 二批接口（统计对账、备份导出、节假日下发、城市数据、反馈附件）未建骨架。

## 已验证 / 未验证

- 已验证：`schema.sql` 在 MariaDB 10.11 上可执行；计时互斥唯一索引行为符合预期；
  15 个实体与 15 张表的字段逐一一致；Java 源码无语法错误；`PricingUtil` 结果与需求 5.3 示例一致（60 + 90 = 150 元）。
- **未验证**：没有用 Maven 在真实依赖下完整编译（本环境无法访问 Maven Central）。第一次 `mvn compile` 若报依赖/版本问题，
  先核对 pom 里的版本号；MyBatis-Plus 3.5.9 起分页插件在独立模块 `mybatis-plus-jsqlparser`，pom 已加。
