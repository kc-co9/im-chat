# 自有运维 CLI 与 IAM 接入设计

> 状态：已批准。2026-10-30 用户最终确认继续；独立设计复核 Accept，findings none。历史 Block 与修订记录保留。本次批准覆盖与[自有 CLI 安全设计](2026-10-30-ops-cli-authentication-design.md)及[治理迁移设计](2026-10-28-official-jraft-governance-simplification-design.md)组合后的最终方案；允许创建执行计划，不代表运行验收。实施计划：[治理迁移与自有 CLI](../exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md)。

## 摘要

提供自有交互式 CLI，经 IAM 认证、Ops 授权和审计访问 Broker 受控管理用例，不直接连接 JRaft。复用现有身份权威、应用权限、集中审计与 Broker operation，不建设第二套身份系统。

CLI 使用专用 public client，经系统浏览器登录，以 Authorization Code + PKCE S256 取得短期用户 Opaque Access Token。Ops 新增隔离的 `/api/cli/**` Bearer 入口；浏览器仍使用 BFF Session 与 CSRF。终端不持有 Client Secret、Ops Introspection 凭证或 Broker 服务凭证。

首版仅支持一个前台交互进程内的登录与命令会话，不支持无人值守、Refresh Token、Device Flow、PAT 或长期 Token。Token 到期重新登录；进程退出后可凭非敏感请求记录恢复同一 operation 查询或重试，不恢复 Token。

## 背景

Broker 已有 management HTTP；Ops 已有受控成员操作、权限检查与集中审计。当前 Ops 只接受 IAM BFF Session。IAM 虽有授权码与 PKCE，Client 模型仍要求 Secret，注册映射使用 `CLIENT_SECRET_BASIC`，尚无 public client 的完整约束。不能用复制浏览器 Cookie、分发 Web Client Secret 或改用机器主体模拟管理员来完成 CLI 接入。

当前管理员 authorities 快照不自动按请求 scope 裁剪，因此新入口必须同时检查 Token scope 与管理员 RBAC，不能只限制 Client 注册 scope。

## 目标与非目标

- 目标：交互式 CLI 的正式登录、最小命令集、逐操作授权、可追溯审计及同一 operation 的可靠重试。
- 非目标：无人值守、自动刷新、凭据落盘、通用 OAuth 客户端平台、审批平台、全量诊断、任意原生 Raft 命令、手动转移 Leader、snapshot、resetPeers、强制解锁或漂移修复。
- 未上线，不建设旧协议兼容；现有 Web Client 与 BFF 的安全行为不得削弱。

## 已知事实与约束

IAM Server 唯一拥有 Client、管理员、授权、Token 和撤销。IAM SDK 提供受信 Introspection 与主体适配，不依赖 Server。Ops 拥有运维用例与对 Broker 的服务调用；Broker 拥有成员拓扑、operation、执行许可、revision/epoch 与 Raft 提交。Audit Server 唯一持久化审计。CLI 不保存权威集群副本。

事实入口为根 `ARCHITECTURE.md`、`docs/SECURITY.md`、`im-management/AGENTS.md`、Ops/IAM README。代码依据包括 `OAuthClientAppService`、`OAuthRegisteredClientTransformer`、`IamIntrospectionService`、`IamOpaqueTokenAuthenticationConverter`、`BrokerClusterOperationController` 与 `BrokerClusterOperationAppService`；现有机器 Converter 不可直接作为管理员主体 Converter。

## 方案概览

```text
CLI --系统浏览器授权码+PKCE--> IAM
CLI --用户 Bearer--> Ops /api/cli/**
Ops --服务端 Introspection--> IAM
Ops --既有服务凭证--> Broker management HTTP --> 受控 operation / im-raft
Ops --既有 Audit SDK--> Audit
```

认证只证明调用者身份，授权只证明允许请求某项用例；二者都不能跳过 Broker 执行许可、排空、quorum、revision 或 fencing。

## 详细设计

### 模块与协议边界

CLI 放在 `im-management/im-ops-cli`，作为独立启动的客户端模块，不部署服务、不依赖 Ops/IAM/Broker Server 实现，也不为一次客户端使用新增 SDK 平台。采用 Java 21，与仓库工具链一致；仅维护窄 HTTP 输入输出模型。具体命令解析库与打包方式在执行计划中固定，不影响此信任契约。

Ops 增加独立 CLI Controller 与安全链，委托现有应用服务；成员业务逻辑不得复制。IAM SDK 增加可复用的管理员 Bearer 校验能力，不能放宽 BFF 的 client 绑定或改写现有机器主体规则。CLI 接入不改变 Admin、Audit 及其他应用的认证入口。

### 专用 public client

IAM Client 显式区分 confidential 与 public；既有 Client 保持 confidential。public client 仅用于此交互式 Ops CLI，认证方式 `NONE`，无 Secret，只允许 Authorization Code，强制 PKCE S256，拒绝 plain、缺少 challenge/verifier、Client Credentials 和 Refresh Token。不能把所有 Client 的认证方式全局改成 NONE。

CLI Client 绑定 Ops 应用与 `imOps` audience，服务器仅允许本设计命令需要的 scope。Ops 使用部署配置中的精确 CLI client_id 白名单，不接受同 audience 下任意 Client。IAM 对 Client 类型、grant、redirect URI 和权限目录作一致校验；Token 必须能通过可信 Introspection 区分管理员授权与机器授权，不能靠 sub 的字符串形状判断。若现有响应不足以提供这种判据，须新增明确的、由 IAM 生成的主体类型契约。

### 登录与回调

CLI 先绑定 `127.0.0.1` 的单一固定端口及 `/oauth/callback` 路径，再打开系统浏览器；redirect URI 必须与 IAM 精确预注册值一致。不支持任意端口、通配路径、localhost DNS 或自定义 URI scheme。端口作为 CLI/IAM 一致的部署输入固定，绑定失败立即停止，不换端口继续。回调仅接受预期方法、路径与 Host，拒绝非 loopback；不提供其他本地 HTTP 功能。

每次登录生成独立高熵 state 和 verifier，challenge 为 S256。只接受匹配当前待完成登录的 state，一次性消费；错误 state 不兑换 Token。使用原 client_id、redirect URI 和 verifier 换码，授权码与 verifier 不落盘、不记录。一次会话最多一个待完成登录，登录预算 120 秒，超时关闭监听器；回调请求限制为 8 KiB，超限拒绝。浏览器自动打开失败可展示 IAM 授权 URL，由操作者手工打开，不输出授权码或 Token。

IAM 与 Ops 远端通信生产使用 HTTPS，不允许跳过证书校验；仅 loopback redirect 使用 HTTP。CLI 只向显式配置且匹配预期环境的 IAM/Ops 发送请求，不跟随跨 origin 重定向，认证失败不回退匿名或 Broker 直连。

### Token 生命周期与退出

采用 5 分钟 Access Token 有效期作为首版配置，不签发 Refresh Token。Token 只存在前台 CLI 进程内存，禁止命令行参数、环境变量导出、URL、日志及明文配置存储。进程可多次执行命令；这不是后台代理。Java 内存不承诺可证明擦除，进程/core dump 属于终端部署保护边界。

退出时尝试调用 IAM 标准撤销端点，以 public client 允许的 client_id 绑定撤销本 Client 的 Token，然后清除引用并结束进程；撤销能力及 ownership 校验是本次 IAM 增量，不假定现成支持。网络失败时明确报告远端撤销未确认，仍退出；不得宣称 Token 已失效，剩余有效期至多受原有效期约束。到期、撤销、Client 禁用或权限撤回后，Ops 下次请求必须拒绝；重新认证不自动重发上次写操作。

撤销的权威对象固定为当前 Token 所属的整个 authorization；首版每个 authorization 只有一个用户 Access Token，不因登出撤销管理员的其他登录。IAM 将框架 Token invalidated 信号显式映射到领域撤销，不能沿用始终 ACTIVE 的保存命令或仅更新凭据的既有分支。撤销成功响应必须晚于数据库失效事实提交；未知 Token 按标准幂等响应，但不得把数据库写失败包装成成功。以 authorization 行为并发边界，保存与撤销使用行锁或等价数据库条件更新，REVOKED 单向不可逆；撤销后迟到 ACTIVE 保存不能复活授权，也不能通过更新 Token 绕过。读取重建与 Introspection 均以该已提交状态为准。客户端 ownership 按受信 client_id 与 Token 所属 authorization 比较，不能撤销其他 Client 的授权。

#### T7 保存并发前提澄清（2026-11-01，独立设计复核 Accept）

框架授权读取必须携带该次读取的原始 `rowVersion`：由应用 DTO 经不可变内部 framework attribute 传入保存命令 `expectedRowVersion`，不是请求参数、Token Claim 或任意属性 JSON。已有授权在现有行锁内先核对原始版本，再修改并保存，保留 Repository 的 ACTIVE／版本 CAS；不得重新加载最新版本替旧快照补齐前提，不自动重试或 rebase。仅新授权插入允许空预期版本；带版本但行已不存在、已有行缺版本或版本不符的 ACTIVE 保存均拒绝，通过既有 `AuthException` 映射为 `invalid_grant`，不得返回成功 Token。正常授权码消费仍为 ACTIVE，confidential Refresh Token 轮换保持原语义。

终态撤销只保留窄幂等例外：保存请求明确为 REVOKED 且锁内当前授权已 REVOKED 时只读返回，Token 摘要、版本和撤销时间均不变化；这保障同 Client 并发重复撤销成功，不允许旧 ACTIVE 兑换／刷新快照借终态 no-op 返回成功。首次撤销仍须核对原读取版本并提交，数据库技术失败照常传播，不伪装成幂等成功。未知 Token 及 ownership 校验沿用既有协议。框架对象不可变，成功保存不修改其中的版本；除上述终态撤销外，再次保存必须先重新读取，不引入可变版本持有者或新 Repository／Schema。

确定性验证覆盖同码并发兑换、confidential 同 Refresh Token 并发轮换、同 owner 并发重复撤销、现存行 save／revoke 两种锁顺序及回滚。使用真实 MySQL 事务与生产 adapter，先证明参与者读取同一原始状态，再通过事务绑定 `CONNECTION_ID()` 与 `performance_schema` 精确核对请求者／阻塞者在目标授权行的锁等待；有界 gate 和清理，不用 sleep 推断竞态。此澄清落实既有单用、不可逆撤销及幂等要求，不新增 refresh 历史或胜者撤销规则。独立设计复核 Accept、findings none，原件 `.harness/t7-reaccept/concurrency-design-recheck.json`；仅设计通过，实际 RED／GREEN、实现 Review 和数据库验收仍开放。

Client 禁用、管理员停用及角色／权限撤回以各自权威变更与受影响 authorization 失效的同一事务提交为界；跨所有受影响授权的选择规则须由 IAM 拥有，不由 Ops 猜测。提交后新开始的 Introspection 必须返回 inactive，不使用旧权限快照；已在提交前通过授权的请求不追溯取消，Broker operation 不随登出撤回。并发测试覆盖撤销／保存乱序、变更事务回滚、提交后 Introspection 与已准入请求边界。

### Ops Bearer 信任与授权

仅 `/api/cli/**` 使用独立无状态 Bearer 安全链，不接受 Session Cookie 作为替代身份；原浏览器 `/api/**`、`/iam/**` 继续 BFF 与 CSRF。只对该无状态链配置相应 CSRF 策略，不能全局关闭。认证错误返回结构化 401/403，不重定向 HTML 登录页；拒绝 Cookie 与 Bearer 的身份混用。

安全链装配由 Ops 显式拥有完整组合，不能只新增一条 CLI Bean 并假定 SDK 默认 BFF 链仍存在。Ops 通过 SDK 可复用的 BFF 配置构件装配原 BFF 行为；优先级 1 的 CLI 链精确匹配 `/api/cli` 与 `/api/cli/**`，优先级 2 的兜底链处理剩余路径，保留现有 BFF public/authenticated matcher、Session、client 绑定、CSRF、错误及故障降级规则，其他路径 denyAll。SDK 对未启用此组合的应用仍提供原默认链，不全局放宽。BFF Filter 与 CLI Filter 仅在各自安全链注册，使用禁用的 Servlet FilterRegistrationBean 防止 Filter Bean 被容器全局自动注册；BFF Filter 亦明确跳过 CLI 路径，不能在链外先按 Cookie 处理请求。测试必须启动完整 Ops ApplicationContext 核对实际链选择，而非只测试单个 Filter。

Introspection 的可信响应固定携带 active、exp、aud、client_id、principal_type、管理员 subject、scope 和 authorities；scope 按 OAuth scope 语义解析，principal_type 为 IAM 唯一生成的 ADMINISTRATOR/MACHINE 显式判据，SDK 不丢弃这些字段。CLI 只接受 ADMINISTRATOR；缺字段、类型错误或不匹配即拒绝，不能从自报 Header、sub 格式或 authorities 是否非空推断身份。

每次请求通过服务端凭证 Introspection，不使用 stale-if-error 或正向身份缓存；IAM 不可用则 fail closed，不登记业务操作。校验 active、有效期、audience、精确 client_id、可信管理员主体类型及 scope，建立现有 `IamPrincipal`，保留原管理员 ID 供授权和审计。机器 Token、其他应用 Token、其他 Client Token 均拒绝。

Controller 按固定用例校验 scope 与管理员对应 Authority 的交集。调用方不能通过请求 Body/Header 自报权限；Client scope 也不能提升管理员 RBAC。集群及 operation 查询使用现有 cluster-state 读取 Authority，五个成员写命令使用 `ops:broker:raft:write`；准确读取权限名以现有权限目录为准，执行计划必须展开映射。权限撤回及 Client 禁用须同步使既有 Token 的 Introspection 权限失效，不能仅禁止新 Token 签发。

### 首版命令与重试

CLI 命令固定为 `cluster show`、`operation show`、`raft add-learner`、`raft complete-learner-sync`、`raft promote`、`raft demote`、`raft remove`，另有 login/logout/exit 会话控制。不开放动态 action、任意配置提交或 force。

本轮 Review 修正由用户授权实施：五个成员写操作均显式提供 `membershipEndpoint`，与 Broker 已有必填协议一致；端点随原 operationId/expectedEpoch 保存并原样 retry，不从当前发现或绑定补写。历史缺端点记录拒绝且保持原字节，查询原操作后再人工处理；本修正不放宽 Broker 校验。权限目录只在本轮 `ACTIVE → INACTIVE` 时撤销 audience 管理员授权，历史停用项不触发重复撤销。修正的独立增量 Review 与完整验收尚未取得，实施证据由当前 T2 拥有。

CLI API 为 `GET /api/cli/cluster-state`、`GET /api/cli/operations/{operationId}`、五个明确 POST `/api/cli/brokers/{brokerId}/raft/{add-learner|complete-learner-sync|promote|demote|remove}`。它们复用 Ops 应用服务及 Broker 协议；operation 查询需新增 Ops 的窄投影链。集群查询是带来源与 revision/epoch 的运行投影，不声称线性一致；operation 无法查到或查询失败不能推断原请求没有提交。

写入需显式 operationId、expectedEpoch 与用例参数；展示环境、目标和动作，并要求交互确认或显式确认参数。首次发送前在用户指定本地文件以仅当前用户可读权限写入非敏感请求记录，包含目标环境、operationId、expectedEpoch 及固定语义，不包含凭据；已有文件先核对，不静默覆盖。记录失败不发送。重试须显式引用原记录，不自动刷新 epoch、更换 operation 身份或生成新 operation。这里的身份指 operationId 与固定请求语义，不绑定原 IAM 管理员。用户已确认具有当前权限的另一管理员也可显式接续，服务端每次重新检查权限；本地文件只帮助防误操作，不是身份凭证或授权依据。审计记录本次实际管理员、CLI Client 和 operationId，不声称已验证原发起人关系，不新增原发起人绑定表。

请求有界等待，超时／断连报告 UNKNOWN，保留 operationId，查询或重提同一请求；明确区分受理、进行中与成功终态。认证过期先重新登录，再显式恢复，不自动重放。Ops 最多跟随一次可信 Leader 提示且保留原 operation，不给 CLI 返回可直接写入的 Broker 服务凭证或要求终端追随 Broker 地址。

## 可靠性与安全

CLI 单线程串行执行命令，最多一个 HTTP 操作在途；远端连接和请求预算各不超过 10 秒，响应 Body 上限 1 MiB，不无限轮询、排队或自动重试写入。Ops 复用现有有界远端查询资源，不新建无限执行池；资源耗尽不得变成未认证放行。具体阈值是保守设计值，不是容量实测结论。

审计复用 Ops 应用服务和 Audit SDK，不在 CLI 自建审计持久化。新认证拒绝按现有安全审计规则记录；操作审计绑定 IAM 管理员和来源 Client，不记录 Token、Cookie、授权码、verifier、完整请求体或响应。Audit 投递失败保持现有可观测且不覆盖业务结果的语义。CLI 成功退出不等于审计持久化成功。

## 运维与交付

部署须注册专用 CLI public client、精确回调 URI、Ops audience/scope，提供 HTTPS IAM/Ops 地址与信任材料；无 public client 时 CLI 登录失败，不复用 Web Client Secret。CLI 无服务端监听，仅在登录预算内临时监听 loopback。

先完成 IAM/SDK/Ops 安全回归，再联调 CLI；JRaft 迁移与 CLI 可拆任务，但 CLI 不得先连未受许可保护的成员写入口作为已验收最终能力。原生 JRaft CLI 仍关闭。回退禁用 CLI Client 和新 CLI API 即停止新访问；已接受的 Broker operation 继续由原协议拥有，不能因撤销登录删除操作或跳过旧许可。

## 风险与回滚

- 终端失陷可窃取短期 Token；PKCE 不提供本机恶意程序隔离。使用短有效期、无落盘和终端权限约束，不声称绝对防护。
- 固定回调端口可能占用，选择失败关闭而非自动回退；无浏览器或远程纯 headless 场景不支持。
- 每请求 Introspection 增加 IAM 依赖，故障时停止新管理请求；不影响既有 Broker operation 或数据面运行。
- 不建设旧协议兼容；保持原 confidential Client、BFF Session、机器 Client 行为不变。

## 验证策略

- IAM：public/confidential 注册矩阵，NONE 仅限 public；S256、精确 redirect、码单用、错误 verifier、非法 grant、无 refresh；Token 管理员类型／audience/client/scope；撤销 ownership、禁用及权限撤回即时失效。
- CLI：假 HTTP 与受控时钟覆盖 state 错配／重复、超时、端口占用、回调大小、错误 Host/path、HTTPS/跨域拒绝、凭据不落盘、响应上限、提交前记录失败及 UNKNOWN 原身份重试。
- Ops：错误 client/audience/主体/scope/RBAC 拒绝；IAM 不可用拒绝；Cookie/Bearer 不混用；Web Session/CSRF 与机器端点不回归；审计绑定真实管理员和 Client。
- 全流程：浏览器登录 → 集群查询 → 提交 learner 操作 → 查询进度；五个用例契约，超时／换届保持同 operation；管理员仍不能绕过未排空许可。确定性负例必须在修复前失败。
- 按窄层至广层运行，执行计划列出精确 Maven/前端命令；最终 `./scripts/verify.sh clean` 与 `./scripts/verify.sh full`，受影响 startup/behavior/E2E 证据不能省略。当前未实施，未运行上述验收。

## 后续文档与执行

实施时同步根 Architecture、Management/IAM SDK/Ops README、`docs/SECURITY.md` 与 Harness 规则矩阵；新增 public client 和管理员 Bearer 规则需确定性测试，不以 Review 代替可自动化断言。设计通过独立 Review、用户确认精确契约后创建 execution plan，当前没有计划或生产修改。

## 附录 A：备选方案与行业依据

1. **采用：Authorization Code + PKCE public client。** 复用 IAM SSO，终端无 Secret；代价是必须补全 public client 与 Ops 用户 Bearer，需浏览器及 loopback。
2. **延后：Device Authorization Grant。** 适合 headless，但仓库尚无 Device Flow；需要设备码、用户码、轮询、过期与防滥用协议，首版范围更大，不因框架可能支持就认为现成可用。
3. 保留纯 Web 运维可避免新增终端凭据，但不满足用户选择自有 CLI 的目标。复制 BFF Cookie 或下发服务 Secret 不作为可接受方案。

标准依据为 OAuth 2.0 RFC 6749、PKCE RFC 7636、Native Apps RFC 8252、Token Revocation RFC 7009；标准不替代对当前框架 public client、Introspection 和撤销实现的验证。参考：[RFC 7636](https://www.rfc-editor.org/rfc/rfc7636)、[RFC 8252](https://www.rfc-editor.org/rfc/rfc8252)、[RFC 7009](https://www.rfc-editor.org/rfc/rfc7009)。本设计主动限制固定 loopback 端口，不宣称实现完整通用 Native App 平台。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| C1 | 2026-10-30 用户认可接入建议 | 自有交互式 CLI 使用 IAM public client、浏览器授权码与 PKCE S256，经 Ops 窄 Bearer 入口调用受控用例 | 不复制 Cookie，不向终端分发服务 Secret，不另建身份系统 |
| C2 | 用户认可首版生命周期取舍 | 短期用户 Token，不支持长期 Token、自动刷新或无人值守；过期重新登录 | 减少凭据生命周期范围；准确超时、模块、端点及撤销契约随后经独立设计复核，并纳入 2026-10-30 用户最终批准的组合方案，尚未实施 |

| C3 | 2026-10-30 用户确认跨进程接续规则 | 具有当前权限的管理员可显式接续同一 operation，不强制原管理员绑定 | 保持 operationId、epoch 和请求语义；每次重新授权，审计记录实际调用者；不扩建发起人持久化协议 |

## 附录 C：独立 Review

2026-10-30 独立 Reviewer `a72e0e5ea9cfc866e` 首轮 **Block**：撤销持久化／并发闭环未固定；SDK 默认链会因新增 CLI 链退让且 Filter 可能全局注册；跨进程重试身份含义不明。已补充 authorization 单向撤销与事务提交、Ops 显式双链及 Filter 注册边界、可信 Introspection 字段；用户选择有权限管理员可接续，已记录 C3。同一 Reviewer 复核 **Accept，findings none**，确认三项设计缺口关闭；原 Block 保留。结论覆盖本补充设计及 D19 对应边界，不代表实现验收；用户随后于 2026-10-30 最终批准组合方案，见页首状态。撤销映射须区分 Access Token 撤销与正常授权码消费，不得将所有框架 invalidated 信号一律解释为整个 authorization 撤销。复核只读检查设计与相关 IAM 路径，未编辑、构建或运行测试；public-client 撤销接入、数据库并发和完整安全链均待实施验证。只读审查未构建，框架 public client／撤销行为及全部实现验证仍未执行。前序调查不是本设计 Accept，原 JRaft 设计 Review 不覆盖本增量。
