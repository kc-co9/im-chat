# 路由高可用验证

本模块维护 25 类场景清单，并运行自动故障观察的隔离三 Broker 网络测试。完整入口为：

```bash
./scripts/verify.sh routing-ha
```

## 证据边界

- `RoutingHaScenarioManifestTest` 校验场景 ID、证据类型及当前测试方法定位。
- `AutomaticFailureTlsHaTest` 使用真实三个 `BrokerRaftAdapter` / JRaft 节点、三个 SOFA `RpcServer` / `RpcClient`，以及生产观察用例、领域资格服务、RPC 身份准入、fence 和 Lease 安装链路。每个 Broker 使用独立私钥和唯一 URI SAN。测试启动时调用 JDK `keytool` 生成临时证书并互相信任公钥，测试后由 JUnit 删除临时目录；不提交证书或私钥。
- RH-05 的 `isolated-runtime-test` 不代表容器自动摘流。测试停止真实 follower 或 Leader 的 RPC/Raft 资源，等待存活 voter 独立观察和 Leader 本轮报告拉取产生 `FAIL_BROKER`，核对旧 epoch 隔离、目标 `INACTIVE`、新 epoch Lease 安装，以及 Gateway 确认成功或缺失确认降级。
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
