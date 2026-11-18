# IM Ops

`im-ops` 是运维能力的 Maven 聚合模块，统一构建服务端与终端客户端，不包含运行进程。

| 子模块 | 职责 | 运行方式 |
|---|---|---|
| [im-ops-server](im-ops-server/README.md) | Broker 诊断聚合、受控集群 operation、HTTP API 和 Web UI | 独立 Spring Boot 服务，运行服务名仍为 `im-ops` |
| [im-ops-cli](im-ops-cli/README.md) | 经 IAM 登录并调用 Ops 固定 HTTP 用例 | 独立 Java 21 前台客户端 |

Server 和 CLI 分别打包、运行，CLI 不依赖 Server 的 Java 实现。认证、权限、审计和 Broker operation 契约见各子模块 README。

在仓库根目录构建整套 Ops：

```bash
mvn -q -pl im-management/im-ops -am test
mvn -q -pl im-management/im-ops -am package
```

单独开发、配置和启动时使用对应子模块路径。
