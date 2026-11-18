# JaCoCo synthetic class 报告清单补充设计

> 状态：已批准。2026-11-05 用户确认继续按本窄合同推进；独立技术 Review Approved／findings none，原件 `.harness/t11/b3-synthetic-design/review.json`，批准前受审 SHA `1795a23bd341edd3ec35314d8d70165f717b7214e098f13e81bca62b22249089`。仅批准原 checker 与隔离 fixture 实施，不新增真实清除／Maven／full／HA 授权；T11/B3 保持开放。

## 摘要

建议保留完整生产 class 清单，只在报告期待集上精确复用 JaCoCo 0.8.13 的 class-level `ACC_SYNTHETIC` 跳过规则。规则依据官方实现和本机官方 Analyzer 的逐 class 分析，而非从 XML 缺项反推。沿用现有 class header reader 和同一 manifest，不新增 Java helper、依赖、sidecar 或方法体解析。

## 背景与事实

[原覆盖设计](2026-11-03-owned-namespace-coverage-instrumentation-design.md)第 3.5 节要求整类消失时先停下做设计 Review。此前候选假设生产 class 集合与 XML class 集合相等，独立 Review 为 `Revise`，候选已撤回；记录在 `.harness/t11/b3-report-manifest-review/`。

已有 8 个模块的静态产物包含 694 个 class、676 个 XML class；18 个缺项均为 synthetic class，31 个顶层生成实现均有 XML 记录。69 个 XML class 无正数 `INSTRUCTION` counter，不能要求每类都有该 counter。这些是历史产物观察，不是本轮生成鲜度或 full 证据。

本次使用本机缓存的官方 `org.jacoco.core:0.8.13`，为每个 class 创建独立 `Analyzer`、空 `ExecutionDataStore` 和计数 visitor，逐份读取实际 class bytes：694 次输入中恰有 18 次无回调，其余 676 次恰有一次回调。日志 `.harness/t11/b3-synthetic-design/official-analysis.log`。`javap -c -p` 显示 `Analyzer.analyzeClass(byte[])` 在建立 visitor 前对 `ACC_MODULE`（32768）和 `ACC_SYNTHETIC`（4096）直接返回；本次 18 个缺项均命中后者。未依赖测试是否执行或 probe 是否存在来分类。

## 目标与非目标

- 目标：让报告期待集与固定版本官方 class-level 分析边界一致，同时保持完整所有权、鲜度、摘要及生成实现义务。
- 非目标：放宽 namespace、接受旧产物、按 `$1` 名称跳过、重现 JaCoCo 方法过滤器、修改 report includes/excludes、增加覆盖率阈值或新增验证入口。

## 详细设计

对于需要 exec/report 的模块，定义三个集合：

- `C`：全部生产 class，继续逐项验证 binary name、路径、namespace、非空、鲜度和 SHA256，无 synthetic 豁免。
- `F`：`C` 中 header 的 `access_flags & 0x1000 != 0` 的 class。逐项记录身份、SHA256、flags、固定理由 `jacoco-0.8.13-class-synthetic`；不是发现 XML 缺失后才加入。
- `R`：XML 中唯一 class 记录集合。要求 `R = C - F`，同时要求 `R` 与 `F` 无交集。额外、重复、缺失记录均拒绝。XML 中出现已声明跳过的 synthetic class 也拒绝，不能同时支持互相矛盾的分类。

这是需用户批准的明确边界变化，不是沿用旧规则自动通过。固定 JaCoCo 版本由现有有效模型合同保证；版本变化停止并重新 Review，不能沿用 `0.8.13` 的分类理由。

沿用现有受界 class header reader，读取 constant pool 后的 `access_flags`；不解析 Code、方法或注解。`ACC_MODULE` 不新增豁免：`module-info` 不属于现有自有 namespace 合同，仍拒绝。未知布局和坏 class 继续失败关闭。无测试模块仍有完整 `C`，不伪造 exec/report 或把其全部 class 填入 `F`。

MapStruct 源码期待集及顶层实现对应义务不变：每个期待顶层实现必须存在于 `R`。若其被标记 synthetic 或整类消失，即使官方会跳过，也停止并重新 Review；不能因本补充降低为 Review-only。编译器生成的嵌套 helper 可进入 `F`，但仍留在 `C`。

XML 实际存在的 counter 必须具有合法类型、唯一类型和非负整数 missed/covered。允许 class 没有 `INSTRUCTION` counter，不把它解释成 XML class 可缺失。固定未执行、有可分析指令的 smoke 类仍须 `covered=0`、`missed>0`；不要求所有 class covered 为正。

`C/F/R`、生成对应关系、exec/XML 摘要进入既有同一 manifest；`check` 重读 header、重新枚举并比较集合、flags、理由与摘要。不能只信 manifest 中的 `F`，不能以 touch、历史 target 或手写名单证明本轮结果。

## 可靠性、安全与交付

不改变删除范围、并发锁、Maven 次数、网络及业务连接授权。类清单大小沿用现有 reactor/class 边界；不增加常驻进程或逐 class 外部命令。正式实现仅涉及既有 Ruby reader、原 self-test 和相应规范，不改 POM 或 Java 运行代码。真实 prepare/清除/Maven/full/HA 仍需精确授权。

## 备选、风险与回滚

- 保持 `C=R`：最简单但与官方输出不兼容，当前正式接入会稳定失败；可以维持阻断，不能作为已解决方案。
- 每轮启动官方 Analyzer 分类：可避免手写 flags 规则，但增加 Java 启动、受控依赖定位及输出协议。当前仅有官方明确的 class header 短路规则，建议不引入该框架；未来官方边界不再能由受界 header 表达时重新设计。
- 推荐固定版本 header 分类：规则小且与官方早返回一致；风险是后续版本变化，故不得跨版本复用。原 Review 禁止未经批准单靠 synthetic 标志豁免，本补充正是申请批准这一有精确官方依据的报告分类，不豁免 class inventory。
- 回滚：撤回报告分类接线并恢复失败关闭；不删除旧失败或把旧证据刷新为本轮通过。

## 验证策略与后续文档

实施前独立设计 Review 与用户批准均必需。批准后在原 self-test 添加普通未执行类、无指令接口保留 XML、synthetic helper 无 XML、synthetic 仍必须具备完整 class 摘要、篡改 flags/理由、XML 额外/重复/普通类缺失、顶层 generated synthetic/缺失及版本变化拒绝。先取得可观察 RED，再冻结完整隔离 GREEN 和独立实施 Review。

正式验收仍按原计划，静态 Analyzer 探针不替代同轮清理/重建/report/check 或 full。实现时同步原覆盖设计及 Harness Guide/Matrix 的清单规则；本提案不改 Architecture/README，因为没有运行拓扑或业务行为变化。

## 决策与独立 Review

用户已确认上述固定版本分类；独立 Review Approved／findings none，记录见文首。设计批准不等于实施或运行验收。
