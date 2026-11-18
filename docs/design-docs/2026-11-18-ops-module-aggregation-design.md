# Ops 模块聚合设计

> 状态：已实施。用户批准的 Ops 聚合已完成，独立设计 Review Approved；实现 full、真实 HA 与独立 quality Accept 的原始证据见已归档计划。本次完成状态与链接更新不改变原验收时点，也不声称原 full 对更新后的文档指纹仍为 fresh。

## 摘要与背景

迁移前，`im-management` 直接聚合运行控制台 `im-ops` 和独立客户端 `im-ops-cli`。迁移完成后，`im-ops` 为 packaging=pom 的聚合模块，子模块为 `im-ops-server` 和 `im-ops-cli`，与 IAM、Audit 结构一致。

## 目标、非目标与约束

目标是统一 Maven、目录、部署构建输入、架构传感器和开发入口。Server 与 CLI 分别打包运行，CLI 通过 HTTP 访问 Server，不依赖其实现。Java 包、API、认证、审计、配置、端口和数据所有权不变；不增加 SDK。运行服务名、Compose service、应用名、身份 URI 保持 `im-ops`。Server artifact 改为 `im-ops-server`；现有 Boot repackage 未指定 finalName，部署 glob 随新目录定位 exec JAR。CLI artifact 和 JAR 名不变。

## 详细设计

```text
im-management/im-ops/                 # im-ops，pom
  im-ops-server/                      # im-ops-server，jar，原控制台及 UI
  im-ops-cli/                         # im-ops-cli，jar，独立客户端
```

两个子 POM 的 parent 改为 `im-ops`，management 仅聚合 `im-ops`。测试模块对原 Server 的依赖改为 `im-ops-server`。同步脚本、Docker ignore/Compose JAR 路径、架构测试源码与 class 路径、UI 检查路径、affected 路由及 CLI 标准流精确许可。Compose 文件仅将 `JAR_FILE` 从旧 Server 根迁至新根；独立精确 diff Review Approved（`/tmp/ops-compose-diff-review.txt`），反向替换后的字节与旧摘要一致。固定新 SHA256 为 `c19fffab3b47756f8516eb96b33efa5caaa376b44ca8f3ce435ee19d0ff78835`，不改变读取、dotenv、volume 或其他输入形状。父 POM 变动应影响两个子模块，Server 与 CLI 单独变更仍精确映射各自模块及原下游测试。

正式 reactor 从 52 项变为 53 项：新增 `im-ops` pom 身份，原 Server 身份改为 `im-ops-server` 并迁移路径，CLI 迁移路径。同步 reader 的固定 GAV/POM/basedir/packaging 合同，以及独立 raw oracle、独立 effective XML fixture、active-profile 输出计数等受影响断言。保持独立来源与所有拒绝条件，不从实际模型动态学习清单。历史设计及已归档验收中的 52 项描述保留其历史含义，本设计拥有新拓扑的清单差异。

迁移源码、资源与 UI，忽略的旧 target 不迁入新模块；已有 node_modules 可随 UI 移动但不作为构建或验收证据。迁移 README 后修正相对文档链接。根 Architecture、management README/AGENTS、Coding Guide 的精确路径和 Harness 文档随实现同步。

## 可靠性、安全与交付

无协议或状态迁移。CLI 标准流许可只迁到精确新根，不放宽其他源码根。运行服务名保留，因此 Nacos/OAuth/身份无需迁移。仓库内构建与部署入口一次性更新，不提供旧目录兼容壳。回滚本次目录、POM 和所有消费者路径即可，无数据回滚。

## 验证策略

- 在 RuntimeDependencyPolicyTest 增加明确的父子模块及 CLI 无 Server 依赖契约，迁移前运行得到 RED。
- 执行 `scripts/test-reactor-evidence.sh`、`scripts/test-harness.sh`、`scripts/test-management-ui-harness.sh` 和 `scripts/test-local-compose-harness.sh`；保留已有正反 fixture。
- `mvn -q -pl im-management/im-ops,im-test/im-architecture-test -am test` 验证模块、依赖与源码路径。
- 最终 `./scripts/verify.sh clean` 与 `./scripts/verify.sh full`，真实启动/E2E/HA 由同轮 full 证明；独立实现 Review 后才关闭计划。

## 备选方案与决策记录

平级组织可保留较短路径，但无法从同一能力父模块构建 Server/CLI。选用嵌套聚合需要迁移所有路径及固定模型清单，换取能力导航与命名一致性。

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户批准结构建议 | im-ops 聚合 im-ops-server 与 im-ops-cli | 与 IAM/Audit 一致 |
| D2 | 运行身份与构建身份 | Server artifact 改名，运行服务名保持 im-ops | 控制迁移范围 |
| D3 | Harness 固定模型 | 精确更新为 53 项并保留独立 fixture 与拒绝约束 | 新增一个真实聚合模块 |

## 后续执行与独立 Review

实施与验收见[已归档 Ops 模块聚合计划](../exec-plans/completed/2026-11-18-ops-module-aggregation.md)。独立设计 Review Approved、findings none，原件 `/tmp/ops-aggregation-design-review.txt`；该设计 Review 本身未运行构建或测试。实现阶段的 full、25 个真实 HA 场景和正式 quality 第二轮 Accept 已完成，原件保留于 `/tmp/ops-aggregation-acceptance-full3/`。归档后的状态、链接与文档检查独立记录，不改写原 full 时间、指纹或历史失败。
