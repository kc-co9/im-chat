# 路由高可用验证

本模块维护 25 类场景清单，并运行自动故障观察的隔离三 Broker 网络测试。完整入口为：

```bash
./scripts/verify.sh routing-ha
```

## 证据边界

- `RoutingHaScenarioManifestTest` 校验场景 ID、证据类型及当前测试方法定位。
- `AutomaticFailureTlsHaTest` 使用真实三个 `BrokerRaftAdapter` / JRaft 节点、三个 SOFA `RpcServer` / `RpcClient`，以及生产观察用例、领域资格服务、RPC 身份准入、fence 和 Lease 安装链路。每个 Broker 使用独立私钥和唯一 URI SAN。测试启动时调用 JDK `keytool` 生成临时证书并互相信任公钥，测试后由 JUnit 删除临时目录；不提交证书或私钥。
- RH-05 的 `isolated-runtime-test` 不代表容器自动摘流。测试停止真实 follower 或 Leader 的 RPC/Raft 资源，等待存活 voter 独立观察和 Leader 本轮报告拉取产生 `FAIL_BROKER`，核对旧 epoch 隔离、目标 `INACTIVE`、新 epoch Lease 安装，以及 Gateway 确认成功或缺失确认降级。
- 成员维护增量通过公开 `BrokerMembershipAppService` 验证 follower 两步降级、Leader 自降级后向替代 Leader 发送真实 mTLS 排空确认及显式恢复。夹具采用生产允许的默认最小 voter 数 1，核对最终 native voters／learners、步骤排空及 operation 终态；该参数不改变 JRaft 多数派算法。
- PENDING 门禁场景先通过真实 Raft 提交维护意图，受控暂停 native 执行，验证三个节点拒绝观察资格且不登记自动操作，再显式完成原维护并恢复资格。该场景不模拟 native UNKNOWN，不单独证明 LEARNER_SYNCING 或旧许可跨重启的 D16 阻塞。
- UNKNOWN 门禁场景在 native 调用前真实提交许可，停止旧 Leader 并在替代 Leader 显式恢复，验证不能替换未排空许可；随后以原身份、端点和日志重启旧 executor，等待回放后确认 UNKNOWN 及维护阻断持续存在，新 incarnation 不得执行旧许可。该场景不证明 native 调用中崩溃的全部窗口，也不替代独立 LEARNER_SYNCING 门禁覆盖。
- B4 新增独立初始两 voter／一 LEARNER_SYNCING 的真实 JRaft／mTLS 夹具：没有 PENDING operation 时仍拒绝观察，正式 ReadIndex 同步确认后角色转为 LEARNER、operation 成功、观察资格恢复。该场景不证明动态加入过程，后者由 E2E 加入旅程拥有。
- B4 在既有 UNKNOWN 场景发送真实 typed mTLS 请求，变异证书调用者、许可 ID、incarnation 与目标摘要，断言生产准入的准确拒绝原因、完整 operation／revision 及含 learners 的 native 配置不变；不以客户端超时或任意异常冒充服务端认证拒绝。新增 focused 套件及 affected 同轮报告均为 8 项通过，独立 B4 限定 Accept 见 `.harness/t11/b4-gap-fix-20261106/b4-final-review.json`；不沿用旧 XML，不替代最终 full／真实容器 HA。
- Gateway 进程与完整路由重建在此测试中替换为注册事实和显式应用 ACK 输入。真实 WebSocket、Gateway 重建和容器重启仍由既有 E2E 与 Compose 场景覆盖，不能从隔离测试推导。

## 聚焦运行

```bash
mvn -q -pl im-test/im-routing-ha -am \
  -Dtest=RoutingHaScenarioManifestTest,AutomaticFailureTlsHaTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./scripts/test-routing-ha-harness.sh
```

运行要求 JDK 21、Maven，以及本机 loopback 监听权限；完整入口另需 Docker Compose。Raft 和 RPC 端口动态分配，日志、fence 标记和证书只写 JUnit 临时目录。观察、稳定窗口与冷却使用显式测试常量；不覆盖生产默认值，不降低多数派阈值。失败等待由 Awaitility 和调度器限定，不以固定睡眠推断选举或恢复完成。

完整 runner 保留原恢复测试及所有历史场景映射，只接受本轮新生成的通过用例。单独通过 Maven 模块测试不等于完整 `routing-ha` 验收通过。

## 受控传输前提

真实 TLS HA fixture 的客户端使用 `BoltControlledClient` 和 typed 目标统一 invoke，地址/完整身份由 fixture 显式配对。服务端使用原生 `RpcServer`，由 fixture 显式配置原生 TLS 与 client auth，并注册正式 `BoltRequestProcessor` 及身份/成员准入链，仍验证接收方角色及 fencing/Lease；不是 `BoltControlledServer` 或 Spring 受控服务端装配，不能据此声称已验证其启动契约。开发模式不能提供有效观察票或自动故障摘流，不能用 plaintext 容器恢复替代真实安全 HA。标准 JSSE、D10 会话数组区间与 D11 受控来源边界见 [im-bolt](../../im-plugin/im-bolt/README.md)。本模块成功也不证明生产证书签发/分发或容量。

## Compose 材料边界

真实容器拓扑每轮由 runner 签发一次性测试 CA 和五个独立叶库，保持默认 MTLS、双向 client-auth、严格 Broker/Gateway URI namespace 与部署可信 bootstrap/Gossip 配对。节点只读挂载自己的 `0600` 材料，启动前离线校验，先 down 本轮 project 再删除材料；down 失败保留，不接管旧目录。具体配置键、观察 ID 与稳定身份区别及崩溃边界见[本地部署说明](../../deploy/local/README.md#ha-专用测试材料)。

`bash scripts/test-routing-ha-tls.sh` 只验证离线材料、权限、配对和失败关闭；`test-routing-ha-process.sh` 以 stub 验证前检顺序/原码传播/清理，不冒充实际容器 mTLS 成功。Compose 调试使用旧 exec JAR 时须单独标记制品 SHA，不复用跨输入 full manifest 或发布 25 场景验收证明。
