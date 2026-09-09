# IAM 与 Monitor 实现收敛设计

状态：已确认

执行计划：[IAM and Monitor hardening implementation plan](../exec-plans/completed/2026-08-30-iam-monitor-hardening.md)

关联设计：

- [Management IAM](2026-08-26-management-iam-design.md)
- [Broker 业务监控管理接口](2026-08-13-broker-business-monitoring-design.md)
- [Web 基础设施边界](2026-08-27-web-infrastructure-boundary-design.md)

## 1. 背景

IAM 与 Monitor 已完成第一版实现，但基于当前安全、可靠性、DDD、CQRS、时间和 Harness
规范复审后，仍存在两类问题：

- IAM 的 Token 撤销和最后一个超级管理员约束没有形成完整的运行时闭环；
- IAM 与 Monitor 的部分实现沿用了第一版快速交付结构，尚未稳定隔离领域、应用、接口和基础设施边界。

本设计不改变既有产品目标，不增加兼容层，也不重新设计 OAuth2/OIDC、BFF Session、实时
Introspection、Broker 管理协议或 Monitor 页面功能。它只修复已经确认的行为缺陷，并让实现
收敛到当前仓库规范。

## 2. 目标

- IAM 授权一旦撤销，本地资源认证和远程 Introspection 都立即返回失效。
- 并发禁用、删除或移除角色时，IAM 始终保留至少一个有效超级管理员。
- IAM 多实例不会因共享固定 Snowflake 节点号生成重复业务 ID。
- IAM 聚合、持久化、时间和 HTTP 边界符合当前 DDD 与 Harness 规范。
- Monitor 多节点查询具有有界线程、有界队列、明确拒绝语义和部分成功能力。
- Monitor 的 Broker 协议模型、应用结果和 HTTP Response 相互隔离。
- Monitor 浏览器边界使用毫秒时间戳，页面按用户 IANA 时区格式化。

## 3. 非目标

- 不改变 Access Token、Refresh Token 和 IAM SSO 的有效期。
- 不改变 IAM SDK 的五分钟故障缓存策略。
- 不更改 Broker 管理接口、Nacos 发现或 Gossip 语义。
- 不新增 Monitor 写操作、告警引擎或长期指标存储。
- 不为现有错误结构保留兼容构造器、旧 DTO 或双路径调用。
- 不在本轮统一修复其他业务服务已有的固定 Snowflake 节点号。

## 4. 实施顺序

优化分为三个可独立验证的阶段。前一阶段通过后才进入下一阶段，避免行为修复与结构重构
同时发生而掩盖回归。

### 4.1 阶段一：认证与并发正确性

#### Token 撤销

`DbIamAuthorization.status` 是授权是否可用的权威状态。授权查询必须满足：

- `findByToken` 不向调用方返回 `REVOKED` 授权；
- `findById` 仍可按 Spring Authorization Server 的持久化需要重建授权，但重建的 Token
  必须携带 invalidated metadata；
- Access Token、Refresh Token、Authorization Code 和 ID Token 采用同一撤销事实；
- 本地 `IamLocalOpaqueTokenIntrospector` 与标准远程 Introspection 对撤销结果保持一致；
- 管理员禁用、删除、密码重置、会话撤销和 OAuth2 revocation endpoint 都复用该语义。

测试必须先证明当前撤销后仍可认证，再实现修复。覆盖直接撤销、按管理员批量撤销、本地
Introspector 和远程 Introspection，不只断言 Repository 更新次数。

#### 最后一个超级管理员

“至少保留一个有效超级管理员”是跨聚合并发不变量。所有可能减少有效超级管理员数量的用例
必须共享同一个全局分布式锁：管理员禁用、管理员删除、移除 `SUPER_ADMIN` 角色。

锁位于应用服务边界，使用已有 `@DistributeLock` 和同一个 Scene/Key。事务在获得锁后读取
最新状态、检查数量并写入。启用管理员、增加超级管理员和不涉及超级角色的更新不获取该全局锁。

聚焦并发测试必须证明两个并发减员操作不能同时通过，而不是只检查方法是否存在注解。

### 4.2 阶段二：IAM 结构收敛

#### OAuth2 持久化

`IamAuthorizationService` 继续实现 Spring Authorization Server 的
`OAuth2AuthorizationService`，但通过 `DbIamAuthorizationService` 使用 MyBatis-Plus，
不直接依赖 Mapper。Token digest 查询、状态过滤和持久化更新都封装在该持久化 Service 的
明确方法中；领域 Repository 不为 OAuth2 框架对象增加无价值转发层。

#### 聚合构建

`Administrator`、`RegisteredApplication`、`Role` 和 `ServiceClient` 按统一聚合规则调整：

- 移除 Lombok `@Builder` 和领域长参数构造器；
- 聚合保留私有无参构造器；
- 手写 Builder 内部持有聚合实例，各 setter 直接写入聚合字段；
- `build()` 统一校验领域不变量；
- 数据库 `pkId` 不进入 Builder，由 Repository 在构建完成后回填；
- 不增加只供测试使用的构造器或兼容工厂。

简单业务创建仍可保留表达业务意图的工厂或领域行为，不为一次 `new` 增加委托型领域服务。

#### 临时登录保护

连续密码失败后的自动过期限制用于防止暴力破解，不是管理员账号生命周期中的可管理业务状态。
`Administrator` 只保留启用和禁用状态，不持有失败次数、临时锁定截止时间或 `LOCKED` 状态。
只有出现人工锁定、审批、锁定原因或后台解锁等业务用例时，锁定事实才进入管理员领域。

IAM 使用 `support/security/LoginProtection` 承担技术安全保护。它以管理员业务 ID 作为 Redis
键的一部分，原子累加失败次数，在达到配置阈值后写入带 TTL 的临时限制，并在认证成功后清理
失败状态。Redis 异常不降级为允许登录，避免安全保护失效时静默放行。管理员禁用、启用、删除
和密码重置同步清理对应的临时状态。

认证应用服务先加载管理员并校验长期启用状态，再由 `LoginProtection` 校验临时限制和记录认证
结果。失败次数不再写入 MySQL，认证查询不再通过 `FOR UPDATE` 锁定管理员行。审计监听器可以
查询 `LoginProtection` 的当前限制状态，但不得把 Redis 状态重新映射为管理员领域状态。

#### 时间与接口模型

- IAM 领域、应用 DTO 和 MyBatis Entity 的绝对时刻统一使用 `Instant`；
- 浏览器 HTTP Response 的绝对时刻使用毫秒时间戳 `Long`；
- MapStruct Transformer 完成 `Instant` 与 epoch milliseconds 转换；
- 删除 `Instant -> LocalDateTime -> Instant` 和固定 `ZoneOffset.UTC` 中转；
- Controller 只返回接口层 `*Response`，不直接返回应用 DTO；
- Request 到 Command、应用 DTO 到 Response 的转换通过接口 Transformer 完成，并按输入转换、
  用例调用、输出转换分段书写。

#### 分布式 ID

IAM Snowflake 节点号改为 typed `@ConfigurationProperties`，配置包含合法范围校验。不同部署实例
必须由部署配置获得唯一节点标识；非本地环境配置缺失或越界时启动失败，不回退到所有实例共享的
固定值。当前仓库没有通用节点租约器，本轮不额外引入 Redis/Nacos 租约协议；重复节点配置由部署
清单和发布校验负责阻止，并在模块 README 中明确该前置条件。

本轮只调整 IAM。Account、Social、Message 的同类历史基线继续由 Harness 以 Observed 状态跟踪，
后续单独收敛。

### 4.3 阶段三：Monitor 边界与可靠性

#### 有界多节点查询

Monitor 使用显式 `ThreadPoolExecutor`：

- 核心和最大线程数由 typed Properties 管理；
- 等待队列容量有明确上限；
- 使用能够立即暴露过载的拒绝策略，不在请求线程静默执行额外 Broker 调用；
- 单节点继续使用既有 HTTP 超时；
- 某节点超时或失败只产生该节点失败结果，保留其他健康节点数据；
- 任务被拒绝时返回稳定的 Monitor 过载错误，不无限等待。

测试覆盖队列饱和、节点超时、部分失败和正常关闭。固定线程数本身不视为容量有界。

#### DDD 与 CQRS 边界

Monitor 只有一个领域能力，使用扁平结构：

```text
interfaces/http          HTTP Request、Response 与 Controller
application              MonitorQueryAppService
domain/model             Monitor 领域事实和值对象
domain/repository        读取监控事实所需的领域接口
adapter                  Broker/Nacos 能力到 Monitor 语义的适配
infrastructure           HTTP Client、Discovery、配置和执行器
model/cqrs/query         查询输入
model/cqrs/dto           应用输出
model/io                 HTTP Response
transformer              接口、应用、领域、协议边界转换
```

不新增只有一个类的二级包。`MonitorQueryService` 改为 `MonitorQueryAppService`，公开方法接收
单个 Query；无入参总览也使用明确 Query，而不是继续用标量重载。Controller 不直接返回
Broker 管理协议类型或领域对象。

Broker HTTP Client 的 wire records 留在基础设施客户端边界。Adapter 将其转换成 Monitor 领域
事实；应用服务完成多节点聚合并输出 DTO；HTTP Transformer 再生成 Response。三个模型即使字段
暂时相同也不复用，防止 Broker 协议变化直接改变浏览器 API。

#### 时间展示

Monitor HTTP Response 的绝对时刻使用 epoch milliseconds，TypeScript 使用 `number`。前端提供
统一时间格式化函数，根据浏览器 IANA 时区展示 `yyyy-MM-dd HH:mm:ss`，不直接渲染 ISO 字符串，
也不在服务端固定为中国或 UTC 展示时区。

## 5. 错误处理

- 撤销 Token 使用现有无效凭证语义，不暴露授权是否曾存在。
- 最后一个超级管理员冲突继续使用稳定状态转换异常。
- Monitor 单节点失败作为节点失败数据返回；整个查询无法调度时才返回应用级过载错误。
- Transformer 不捕获业务异常；统一 Web 异常处理继续输出 `HttpResult`。
- 不在日志中输出 Token、Token digest、客户端密钥或完整认证请求。

## 6. 测试策略

每项行为修改遵循 Red-Green-Refactor：

1. 新增最小失败测试并确认失败原因与目标缺陷一致；
2. 实现最小修复；
3. 运行所属类、所属模块和关联模块测试；
4. 完成结构重构后运行 Architecture、Harness、Drift 和完整验证。

关键测试边界包括 IAM 撤销、本地认证、远程 Introspection、超级管理员并发减员、聚合 Builder
不变量、Repository `pkId` 回填、IAM HTTP 时间戳与 Response 隔离、多实例 ID 配置、Monitor
执行器饱和与部分成功、Monitor 边界 Transformer，以及前端 IANA 时区格式化。

## 7. 兼容、发布与回滚

项目仍处于开发阶段，不保留旧 DTO、旧构造器或旧 Monitor 内部模型兼容路径。结构调整与调用方、
测试和文档在同一任务中原子更新。

运行时发布顺序：

1. 先发布 IAM 撤销和超级管理员并发修复；
2. 再发布 IAM 结构调整和实例唯一 ID 配置；
3. 最后同时发布 Monitor 后端与前端协议调整。

IAM Snowflake 配置上线前必须为每个实例分配唯一节点号。Monitor 后端与前端不能跨版本混部，
因为时间字段由 ISO 字符串改为 epoch milliseconds。回滚时按阶段整体回滚，不保留双协议转换。

## 8. 完成条件

- 撤销后的所有 Token 路径立即失效；
- 并发操作无法移除最后一个有效超级管理员；
- IAM 不再直接编排授权 Mapper，不再使用聚合 Lombok Builder 或 `LocalDateTime` 绝对时刻；
- IAM Snowflake 节点标识可配置且多实例唯一；
- Monitor 使用有界执行器并具有拒绝、超时和部分成功测试；
- Monitor HTTP、应用和 Broker 协议模型完成隔离；
- Monitor UI 统一按 IANA 时区展示时间；
- 相关模块测试、Architecture、Harness、Drift 和完整验证通过；
- 执行中形成的新通用规则同步回写 Harness。
