# Broker 单集群身份简化

> 状态：用户已批准范围，独立 Review 待完成。

## 摘要

Broker 当前只有一个运行集群，业务请求不能选择集群。移除领域租约、fence、观察模型及
SDK 业务请求响应中的 clusterId 透传，保留目标 Broker 身份、epoch、任期、序列、nonce 和权限校验。
移除 BrokerCluster 聚合 id/Builder identity；状态机只持有一个聚合，不注入技术标识到领域。
同步 application command、Transformer、配置装配和 Ops 状态查询投影/前端契约。
这不是防脑裂机制调整，Raft 的多数派、任期及提交规则保持不变。

## 技术归属

RaftNodeSettings 与 identity.json、快照 envelope 和 fence 水位文件仍可持有技术归属标识。
业务 SDK 状态不为快照校验保留 clusterId；快照归属应单独保存在技术 envelope 中。
快照 envelope 升至版本 4，必填技术 clusterId 从本机不可变配置取得并与恢复值核对；
先验证版本/归属，再原子恢复状态、操作、fence 和端点绑定。节点身份仍由目录与水位文件校验。
不新增多集群或租户能力，不变更 Peer Client 的传输职责。

## 兼容与安全

请求和响应字段删除涉及 SDK、Broker 控制面协议及快照序列化，节点和 SDK 须使用匹配版本。
快照格式需要显式版本化，旧格式不得静默错误恢复；保留目录与水位文件归属检查。
共识日志 Codec 同步升级至版本 3，旧版本 2 日志明确拒绝，不忽略旧 fence 身份字段。
当前改动用于开发环境格式升级；禁止直接对带旧日志/快照的生产目录滚动升级。若需升级已运行
环境，必须另行提供经验证的离线迁移与回滚流程，不删除日志或 fence 水位来绕过版本校验。
控制面继续核对受信 Broker 身份、凭证、调用角色，不以去除字段放宽调用准入。
删除空 BrokerControlRequest 后，以四种明确的请求类型保留 fail-closed 控制面能力校验。
生产环境继续禁止 trusted-network 退化认证，依赖 mTLS 与部署作用域凭证；本设计不修改该条件。
SDK 仍拒绝 revision 回退、同 revision 异内容和算法漂移；bootstrap/discovery 绑定当前部署，
切换部署必须重建缓存，不通过保留业务 clusterId 增加多集群选择。

## 验证

领域租约/fence/观察与聚合回归、SDK 协议/缓存、控制面拦截器、快照/Codec 回归；
再运行 Broker clean test 与分层/模块边界测试。用户仍在 Review，不推进全仓收口。

## 决策与审查

用户批准先去掉业务层身份，技术归属暂留。独立 Review 检查删除范围、技术归属与授权边界。
首轮 Review 为 Block，提出聚合身份范围、技术快照归属、旧日志升级、认证和 Ops 调用遗漏；
本次增量已明确上述范围、版本及禁止未迁移生产升级边界，复核后再实施。
