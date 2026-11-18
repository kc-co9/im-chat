# IM Ops CLI

提交返回 `IN_PROGRESS` 表示同身份请求正在 Broker 本地处理，不证明 Begin 已登记，epochs 可为空。CLI 保留此状态并以非成功退出，提示保留原记录/ID、稍后查询或原记录重发，不自动重试、不创建替代 ID。取证期间 `operation show` 可能找不到操作，不能据此断定未提交。Broker/Ops/CLI 成组部署；旧 CLI 会将未知状态保守映射 UNKNOWN。

`im-ops-cli` 是 Java 21 前台交互客户端，通过明确的 IAM OAuth2 和 Ops HTTP 契约执行受控运维命令，不部署服务，不依赖 Spring 或 Server 实现。

## 实现状态

客户端 T10 范围的实现、冻结验收和独立 Review 已完成，证据由[归档计划 T10](../../docs/exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md#t10-自有交互-cli)拥有；真实 IAM／Ops／MySQL／浏览器 HTTP 旅程由已完成的 T11 验证。以下局部证据保留其原始边界，不将本地 fixture 解释为真实跨服务或生产验收。已实现单次登录 state、PKCE S256、120 秒会话截止时间和一次性消费，以及固定 IPv4 loopback 端口的有界回调监听器。请求记录已覆盖创建时 owner-only、原语义恢复与禁止覆盖，读取权限和严格输入已取得局部独立 Accept。HTTP 客户端已实现换码／内存 Bearer 集群读取、到期禁发、撤销先清本地引用、禁止 redirect／Cookie／Authenticator 和连接预算校验的窄切片。有界 Body 接收与原始异步请求的绝对 deadline／超时中断取消已取得局部独立 Accept；严格远端 JSON／envelope、串行命令、原 operation UNKNOWN 读取及非法 Bearer 防泄漏修复已通过模块反馈，Bearer 修复已取得同范围独立 Accept。写边界修复已取得同范围独立 Accept：提交结果关联原 operationId 与实际 status，401 清除本地认证，403 明确权限拒绝。入口已有 help、配置、login／logout／exit 和两个读取命令；配置祖先信任与撤销失败报告修复已取得局部独立 Accept。子进程已通过本地受信 JDK HTTPS fixture 的 callback→换码及失败撤销组合，不代表真实 IAM／浏览器验收。Shade JAR 与实际 `--help` smoke 已通过一次，后续源码变化后仍需重验。五个固定写命令入口及 `operation retry` 已接入本地 HTTPS fixture，并验证发送时原记录存在及 retry 不改原 bytes；写入口退出分类／UNKNOWN 恢复提示修复已取得同范围独立 Accept，后续本地 TLS 自签／hostname 拒绝、reason 和认证后写拒绝回归已通过，增量契约 Review 已取得局部 Accept；Java 中断恢复提示与真实线程中断回归已通过，源码窄 Review 已取得局部 Accept，不把局部测试通过视为完整登录或运维验收。

## 当前命令用法

IAM Token 响应显式接纳标准可选 `scope` 字段；它不替代 Ops 服务端权限校验。解析仍拒绝未知字段、重复键、refresh token 和不合法的 Token 类型或有效期，不保存或刷新凭据。

以下为当前客户端接口，不是完整跨服务或生产验收声明。需要 Java 21、可用的人工浏览器、受信 HTTPS IAM／Ops，以及在 IAM 精确注册的固定 loopback redirect。配置文件和请求记录的父目录须满足下文信任边界；不要放入其他用户可写的共享临时目录。配置只含非秘密输入，例如：

```json
{
  "iamOrigin": "https://iam.example.test",
  "opsOrigin": "https://ops.example.test",
  "clientId": "ops-cli",
  "callbackPort": 18881,
  "scope": "ops:broker:read ops:broker:raft:write"
}
```

示例值必须替换为实际部署与注册值。将配置权限设置为 `chmod 600 <config-file>` 后，构建并启动：

```bash
mvn -q -pl im-management/im-ops-cli -am package
java -jar im-management/im-ops-cli/target/im-ops-cli.jar --help
java -jar im-management/im-ops-cli/target/im-ops-cli.jar --config <config-file>
```

1. 输入 `login`。CLI 先绑定固定 `127.0.0.1` 端口，再尝试打开浏览器；无法打开时仅打印授权 URL，仍需人工浏览器完成认证。端口占用时失败，不换端口。
2. 登录成功后输入 `cluster show` 或 `operation show <operationId>`。Token 只驻留进程内存，最长 300 秒；到期后重新 `login`，不刷新或保存凭据。
3. 写操作须显式提供环境、目标、原 operationId、expectedEpoch、原成员端点、新请求文件及 `--confirm`。先核对目标和语义；本地文件保存失败时不提交。所有参数严格采用下列顺序，以空格分隔，不支持 quoting 或值内空格。

```text
raft promote --environment <Ops-origin> --broker <brokerId> --operation-id <operationId> --expected-epoch <epoch> --record <new-file> --membership-endpoint <host:port> --confirm
raft add-learner --environment <Ops-origin> --broker <brokerId> --operation-id <operationId> --expected-epoch <epoch> --record <new-file> --membership-endpoint <host:port> --confirm
```

五个成员写操作均必须提供 `--membership-endpoint <host:port>`；`complete-learner-sync`、`promote`、`demote`、`remove` 可在端点之后、`--confirm` 之前增加 `--reason <single-token>`，`add-learner` 不接受 reason。确认请求的端点与可选 reason 一起保存到原记录，retry 原样发送，不查询当前端点替换。旧的缺失端点成员操作记录会被拒绝读取，不能自动补写；保留原记录与 operationId 并查询原操作，读取失败不代表操作未登记。CLI 展示环境、目标、动作和 operationId；`--confirm` 表示调用者已明确确认，不再二次询问。

`APPLIED`／`DUPLICATE` 只表示提交响应，不表示 operation 已到终态；继续用 `operation show <operationId>` 查询。拒绝、冲突、不可用或 `UNKNOWN` 会使进程非零退出。`UNKNOWN` 不表示未提交：保留原文件及原身份，先查询；必要时重新登录，由具有当前权限的管理员重发原记录以确认登记，不刷新 epoch 或端点，不创建新 operationId。对 learner 同步确认，历史未完成操作由 Broker 后台协调器恢复，重发本身不推进恢复：

```text
operation retry --record <original-file> --confirm
```

`logout`、`exit` 或正常 EOF 会清除内存认证并尝试撤销。撤销未确认时明确报告，不能据此推断远端已失效；异常终止不保证执行远端撤销。写响应等待发生 Java 线程中断时，CLI 明确报告提交未确认，给出原 operationId 查询和原记录显式 retry 命令，保留文件并非零退出，不自动重放。入口子进程测试实际中断 main 线程，验证原 future 取消、单次写及撤销失败不覆盖恢复提示；不声称动态验证了 JVM 终止时的线程标志。Ctrl-C／SIGINT 和强制结束不等价于 Java 线程中断，不保证执行 finally、远端撤销或新输出；恢复仍依赖发送前保存的原记录，不能由进程退出推断未提交。

## 本地请求记录边界

请求记录不是凭据或授权依据。文件只含固定操作语义，读取要求当前用户所有且无 group／other 权限；读写均限制为 64 KiB，不覆盖既有文件，恢复拒绝缺失或 null epoch、重复字段、尾随 JSON 和隐式数值转换。HTTP 提交接缝已验证记录失败零发送，五个固定写在实际发送边界可读取已落盘原语义；显式 retry 读取原记录且不覆盖。断连返回 UNKNOWN 并保留原 operationId，不表示未提交；这些局部行为已取得独立写边界修复复核 Accept，客户端交互组合的后续 T10 验收记录由活跃计划拥有；真实 IAM 权限与审计当前调用者的恢复旅程仍由 T11 验证。

读写先解析父路径的 canonical 目标，再从 root 到父目录逐级验证真实目录、owner 为当前用户或 root、无 group／other write，后续仅使用该 canonical 路径。原始父路径中的系统 symlink 可解析到可信目标，不声称拒绝所有原始 symlink。该防误操作边界信任当前 UID 与 root，只支持常规 POSIX 权限；无相应属性时失败，不回退放行。不承诺抵御同 UID、root、额外 ACL 或 NFS 权限模型中的恶意写入者；终端部署必须确保这些机制不授予其他主体目录写权限。

## 验证

```bash
mvn -q -pl im-management/im-ops-cli -am test
```

登录会话测试只证明内存会话校验，不启动 IAM、浏览器或 Ops，不证明 Token 换码请求未发出。监听器测试使用真实本机 loopback Socket，不接触外部服务。监听器原局部独立 Review 的两项 `Revise` 已修复并取得同范围 `Accept`，原件 `.harness/t10/loopback/review-repair.json`；有效回调不受浏览器响应写失败影响，关闭／超时测试补充活动读取同步与遗漏负例。该结论不覆盖完整登录或 T10 验收；预算测试仍存在真实 Socket 等待的调度敏感性。完整流程及运行约束以[批准设计](../../docs/design-docs/2026-10-30-ops-cli-authentication-design.md)和[归档计划](../../docs/exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md#t10-自有交互-cli)为准。
