# Bolt TLS 拒绝证据修正设计

> 状态：已批准。用户已批准严格测试侧取证方向；独立设计 Reviewer `aefe0c40fa1d38c8e` Approved、必须修改项 none，仅覆盖有界取证设计，不代表实现验收。

## 背景与目标

证书轮换测试 `rejectsPresentedClientAuthority` 假定 TLS 1.3 客户端握手先成功，再通过 read 获得 alert。实际复现于 Finished 写入时 Broken pipe，尚未进入旧会话证书断言。目标是去除错误时序假设，保留真实受控服务端证书拒绝、精确客户端证书材料和零业务执行证据，不接受任意断连作为认证拒绝。

## 方案与边界

仅修改 `BoltAuthenticatedTlsTest.java`。保留受控客户端拒绝及 handled=0。独立 SSLSocket 探针先绑定 loopback 临时端口，服务端通过现有 CONNECT 事件按完整远端地址/端口关联该探针的真实 SslHandler.handshakeFuture，不能复用上一次失败连接的结果。

测试私有 X509ExtendedKeyManager 原样委托真实 KeyManager 的全部操作；仅观察该 SSLSocket 实际 chooseClientAlias 及匹配 getCertificateChain 返回，复制观察数据，返回原链。断言选取身份及完整链顺序、长度、DER 与旧材料一致。此证据证明 JSSE 为该实际连接选取并获得的链，不宣称直接抓取服务端收到的 DER。结合同连接原生信任校验失败，证明实际证书拒绝，不以配置材料代替实际选择。旧成功会话的双向 DER 断言不变。

允许客户端握手或读取提前失败，但其 IOException 仅作诊断；决定性断言是关联服务端 handshakeFuture 失败，因果链含 SSLHandshakeException、CertificateException 和真实 CertPathValidatorException 或 CertPathBuilderException。不存在指定原因、超时、普通关闭均失败。等待有界，结束关闭 socket/channel，无 sleep、重试或无限轮询。

## 非目标与备选

不改生产配置、协议、信任材料、TLS 版本或生产扩展接口；无反射、全局 provider、强制 alias、trust-all 或制造拒绝。TLS 1.2 探针不能覆盖 TLS 1.3，不选用。直接服务端 trust callback 需替换冻结原生装配，代价过大，不实施。客户端和服务端两侧观察边界明确，不承诺服务端逐字节链捕获。

## 验证与执行

沿用真实 RED `closeout-diagnostics/reproduce.log`，先实现测试侧修正，再执行单用例及包含完整 TLS 测试的窄层验证；补负例证明错误材料或非信任原因不能通过断言，保留原失败记录。独立实现 Review 检查连接关联、原样委托、原因类型和资源回收。纳入现有 Bolt 计划 T3，不新增竞争任务。JRaft 原件缺失、历史 DECODE 归因与完整验收缺口独立保留。无生产架构变化；现有 UNIT_TEST_GUIDE 已要求具体 cause 与真实边界证据，不新增通用框架或 checker。
