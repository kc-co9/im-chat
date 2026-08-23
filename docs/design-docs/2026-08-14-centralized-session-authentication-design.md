# Centralized Session Authentication Design

Implementation is recorded in [Centralized Session Authentication Plan](../exec-plans/completed/2026-08-14-centralized-session-authentication.md).

> **最终实现说明（2026-08-23）**
>
> 实施期间根据 Review 收敛了部分中间设计：启用 JWT 时必须提供至少 64 个 UTF-8 字节的显式密钥，不再由 local profile 自动生成临时密钥；认证成功返回 `SessionAuthDTO(userId, sessionVersion, accessTokenExpiresAt)`，失败统一抛出 `AUTH_FAIL(10001)`，不返回 `valid` 字段。账号领域最终使用 `SessionService` 编排会话建立、刷新和认证，通过 `SessionTokenCodec` 隔离 Token 技术；相关模型命名为 `RefreshFingerprint`、`SessionEstablishment`、`AccessToken` 和 `RefreshToken`。以下正文保留设计形成时的决策过程，当前实现以本说明、模块 README 和源码为准。

## Background

当前 HTTP Gateway 和 WebSocket Gateway 直接使用 `im-session` 的本地 JWT 解析能力完成认证。JWT 密钥硬编码在插件源码中，令牌没有过期时间；账号服务退出登录只修改 Session 状态，Gateway 不查询 Session，因此旧 Token 在退出后仍然可用。登录失败信息还会区分用户不存在和密码错误。

本设计将认证决策集中到账号服务，增加短期 Access Token、可轮换 Refresh Token 和单端互踢语义。HTTP 与 WebSocket Gateway 只依赖账号 Facade，不接触账号存储或签名密钥。

## Goals

- JWT 密钥、算法和有效期使用 typed properties，由账号服务独占签发和解析。
- Access Token 默认有效期 2 小时，客户端在剩余 15 分钟内主动刷新。
- Refresh Token 默认有效期 30 天，通过轮换实现无感续期。
- 退出登录、新设备登录和服务端注销能够立即撤销已有凭证。
- 当前阶段保持单一有效登录会话，后登录设备替换先登录设备。
- HTTP 请求和 WebSocket 握手使用同一账号服务认证语义。
- 登录失败不暴露账号是否存在。
- 已建立的旧 WebSocket 在会话被替换或注销后能够被定向关闭。

## Non-goals

- 不实现多设备并行会话。
- 不在 Gateway 中复制账号 Session 或直接访问账号 Redis/MySQL。
- 不在普通 HTTP 响应或 WebSocket 帧中静默签发新 Token。
- 不实现认证结果缓存；第一版优先保证即时撤销语义。
- 不改变社交、消息等业务服务的数据所有权。

## Architecture

### Ownership

- `im-session` 提供与业务无关的 Token 类型、声明模型和 JWT 编解码组件，不持有硬编码密钥，也不决定账号是否有效。
- `im-account-server` 独占 JWT 配置、令牌签发、Refresh Token 摘要和当前 Session version，做最终认证决策。
- 账号领域在 `domain/session/service` 定义 `SessionCredentialService`，只表达签发会话凭证、认证 Access/Refresh 凭证的领域能力；Refresh fingerprint 是不可解释的领域值，不暴露 digest/HMAC 动作。JWT、HMAC、JTI 和 JCA 的具体实现位于 `infrastructure/domain/service`。`application` 只编排领域服务，不直接依赖 codec、加密算法或插件 Token 模型。
- `im-account-facade` 提供传输中立的认证、刷新结果和参数契约。
- `im-http-gateway` 每次受保护请求调用账号 Facade，认证失败或账号服务不可用时 fail-closed。
- `im-ws-gateway-server` 在握手阶段调用账号 Facade；建立连接后通过 Broker 接收会话关闭控制信号。
- `im-broker` 只根据现有用户到 Gateway 的路由定向传递关闭信号，不拥有账号 Session 或 Token。

### Dependency Direction

```text
HTTP Gateway ------\
                    -> Account Facade -> Account Server -> Session Repository
WS Gateway --------/                       |
                                            `-> JWT codec/config

Account Server -> Broker SDK -> Broker -> target WS Gateway -> close old user connections
```

Gateway 依赖 Facade，账号服务实现不反向进入 Gateway。Broker 只处理控制投递，不解释 JWT。

HTTP Gateway 基于 WebFlux，WS Gateway 基于 Netty；两者都不得在事件循环线程同步等待 Dubbo。认证调用使用 Dubbo 异步结果或隔离的有界认证执行器，并设置短超时、无自动重试。执行器饱和、超时和 RPC 失败都 fail-closed，并产生有限基数指标。

## Token Model

### Access Token

- 默认有效期：2 小时。
- 客户端建议刷新阈值：剩余 15 分钟。
- Claims：`userId`、`sessionVersion`、`tokenType=access`、`jti`、`iat`、`exp`、issuer。
- 只用于 HTTP 认证和 WebSocket 握手，不允许调用刷新接口。

### Refresh Token

- 默认有效期：30 天。
- Claims：`userId`、`sessionVersion`、`tokenType=refresh`、`jti`、`iat`、`exp`、issuer。
- 只用于账号刷新接口，不允许访问普通业务接口或建立 WebSocket。
- 服务端只保存当前 Refresh Token 的加盐摘要或不可逆摘要，不保存明文 Token。

### Configuration

使用 `im.session.jwt` 前缀的 typed `@ConfigurationProperties`，至少包含：

- `secret`：生产环境必须外部提供，启动时校验强度；源码不提供可用于生产的固定密钥。
- `issuer`。
- `access-token-ttl`，本地默认 `2h`。
- `refresh-token-ttl`，本地默认 `30d`。
- `refresh-threshold`，作为响应元数据或客户端约定，默认 `15m`。

自动配置继续允许应用提供等价 Bean 覆盖默认实现。

为了保持本地单实例开发可用，显式 local profile 在未配置密钥时可以启动期生成临时强密钥，并输出不含密钥内容的警告；进程重启后旧 Token 失效。非 local profile 缺少密钥时直接启动失败，多实例环境必须共享同一外部密钥。

## Session Model

账号 Session 增加：

- `sessionVersion`：每次成功登录生成新的不可预测值或单调版本。
- `refreshFingerprint`：当前 Refresh 凭证指纹；领域只把它当作不透明值，不感知摘要算法。
- `refreshTokenExpiresAt`。
- 保留登录状态、登录时间和退出时间。

当前模型为每个用户一份有效 Session。再次登录时覆盖 session version 和 Refresh Token 摘要，因此旧设备的两类 Token 都无法通过在线校验。

## Flows

### Sign in

1. 使用统一的认证失败结果校验邮箱和密码，不向客户端区分用户不存在或密码错误。
2. 生成新的 session version。
3. 签发 Access Token 和 Refresh Token。
4. 在同一事务中保存在线 Session、Refresh Token 摘要和过期时间。
5. 返回两类 Token 及各自过期时间。
6. 如果替换了已有在线 Session，提交事务后发布会话替换控制事件，要求关闭旧 WebSocket。

### Authenticate HTTP or WebSocket

1. Gateway 从请求或握手中提取 Access Token。
2. 调用账号 Facade 的认证方法。
3. 账号服务验证签名、issuer、`tokenType=access`、过期时间和必要 claims。
4. 查询用户当前 Session，验证在线状态和 session version。
5. 成功时返回裁剪后的 `userId` 和必要身份信息；失败统一返回无效认证。
6. RPC 超时或账号服务不可用时 Gateway fail-closed，不使用仅验签降级。
7. HTTP Gateway 清除客户端提供的内部用户和会话头，再把账号服务返回的 `userId`、`sessionVersion` 写入可信内部头；下游只能读取 Gateway 重建后的值。

### Refresh

1. 客户端在 Access Token 剩余不足 15 分钟、已经过期或应用重新启动时调用公开刷新接口。
2. 账号服务只接受 `tokenType=refresh`。
3. 验证签名、过期时间、Session 在线状态、session version 和 Refresh Token 摘要。
4. 使用事务和版本条件更新保证并发刷新只有一个请求成功。
5. 成功请求轮换 Access Token 和 Refresh Token，并原子保存新摘要。
6. 使用旧 Refresh Token 的并发或重复请求返回统一无效结果，并记录不含 Token 内容的安全事件；不注销刚成功刷新的新会话，避免并发误伤。

### Sign out

1. 根据 Gateway 认证后写入的可信用户和 session version 执行退出，不能只依赖客户端请求体中的 userId。
2. 将 Session 标记为离线并清除 Refresh Token 摘要。
3. 事务提交后发布会话注销控制事件，关闭该 Session 对应的旧 WebSocket。
4. 旧 Access Token、Refresh Token、HTTP 请求和后续 WS 重连立即失败。

### Single-device replacement

新登录提交后，账号服务通过 Broker SDK 发布包含 `userId` 和被替换 `sessionVersion` 的控制请求。Broker 使用现有用户路由将控制请求定向给目标 Gateway；Gateway 只关闭匹配旧 session version 的连接，避免登录竞态中误关新连接。

WS Gateway 在握手认证成功后把 session version 保存到 Gateway 本地连接元数据中；Broker 仍只路由到 Gateway ID，不保存 connection ID 或 session version。关闭控制到达 Gateway 后，由本地连接注册表筛选并关闭匹配的旧连接。

关闭通知失败不回滚新登录。失败记录有限基数指标和不含身份、Session 或 Token 数据的日志；Session version 在线校验仍确保旧连接无法重新认证。当前仓库没有适合复用的持久化调度或 outbox，第一版只在事务提交后单次发布，不增加无界内存重试队列，并把持久化有界重试记录为可靠性债务。关闭现存连接属于加速收敛，不作为账号事实的唯一保证。

## API Contracts

### Sign-in result

登录结果增加：

- `accessToken`
- `accessTokenExpiresAt`
- `refreshToken`
- `refreshTokenExpiresAt`

移除语义不明确的单一 `token` 字段；当前仓库没有已发布兼容承诺，不保留无调用方的兼容 wrapper。

### Refresh endpoint

账号服务增加公开刷新端点，输入只包含 Refresh Token，输出与登录结果相同。HTTP Gateway 将该端点列入 permit-all，但后端仍执行完整 Refresh Token 校验。

### Authentication facade

Facade 认证成功结果只返回 `userId`、`sessionVersion` 和 Access Token 过期时间，不返回 Refresh Token 摘要、JWT 密钥或持久化 Session。Token 或关联 Session 无效时统一抛出 `AUTH_FAIL(10001)`，不通过结果字段表达失败，也不向调用方暴露具体失败原因。

HTTP Gateway 需要新增可信 session-version 内部头，并像 user-id 头一样先清除客户端输入再重建。账号、社交和消息服务不从业务请求体接受 session version。

## Error Handling And Security

- 用户不存在和密码错误对外返回相同认证错误。
- Access Token 过期、类型错误、session version 不匹配和 Session 离线统一表现为认证失败。
- Refresh Token 失败使用独立但稳定的刷新失败码，客户端据此回到登录页。
- 日志不得包含 Access Token、Refresh Token、摘要、密码或完整认证请求体。
- JWT 密钥缺失或强度不足时生产配置启动失败，不回退到源码默认密钥。
- 签名比较和摘要比较使用库提供的安全实现，不自行实现密码学原语。

## Verification

### Unit tests

- Access/Refresh claims、类型、issuer、过期时间和错误签名。
- 登录生成新 session version 并统一失败信息。
- Access Token 在线校验覆盖在线、离线、版本不匹配和过期。
- Refresh Token 成功轮换、旧 Token 失效及并发刷新仅一条成功。
- 退出清理 Refresh Token 摘要并撤销两类 Token。

### Integration tests

- HTTP 登录、认证、刷新、退出后旧 Token 失败。
- 第二天 Access Token 过期但 Refresh Token 有效时可无感恢复。
- WS 握手使用 Access Token，Refresh Token 不能握手。
- 新设备登录后旧 HTTP 请求和旧 WS 重连失败。
- 会话替换控制请求只关闭旧 session version 的已有 WS 连接。
- 账号服务超时或不可用时 HTTP/WS fail-closed。

### Repository gates

- 运行受影响模块测试。
- `./scripts/verify.sh behavior`
- `./scripts/verify.sh quick`
- `./scripts/verify.sh full`

## Rollout

这是一次认证契约变更，需要服务端与客户端一起发布。部署顺序应先支持新账号 Facade 和双 Token，再更新 Gateway 和客户端，最后移除旧本地 Token 认证入口。若当前环境无法原子升级，应在执行计划中定义短期、可退出的双读迁移；不得长期保留硬编码密钥或仅验签降级。
