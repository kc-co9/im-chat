# 路由容量 Driver 设计

> 状态：已批准。2026-10-10 用户授权新增 driver 与使用独立子代理 Review；三轮修订后 Reviewer 确认无剩余 Major。实施仍需真实隔离环境配置。

## 摘要与边界

新增 Node driver，直接启动仓库已有 k6 connection/message 场景。协调器继续负责五阶段、三次重复、两类 workload 的矩阵；环境显式选择 connection/message 各一个固定目标档位，全部重复使用同档位。不探索容量边界、不推荐机器规格、不选择低档掩盖失败。不修改既有验收阈值。

## 环境与数据流

环境 JSON 显式声明 environmentKind（production-candidate 或 local）、topologyId、WebSocket 地址、k6 路径、消息连接数和六个 executable hook 的 argv：prepare、fault、metrics、restore、cleanup、inspect。执行使用 spawn 的 argv，不通过 shell 拼接。环境文件可以放仓库外，不能提交凭据。

prepare 使用现有数据工具或环境工具准备独立批次，向指定文件写 batchId、manifestFile、credentialFile。fault 接收阶段、档位、测量开始时间；负责在测量窗口内执行故障并等待业务完成，稳态不调用 fault。metrics 查询环境监控并读取原始 k6 summary，写既有容量契约所要求的真实指标；缺失指标不补零。各 hook 的输入通过 ROUTING_CAPACITY_* 环境变量传入。

每组合流程：首次写入前 inspect 并保存普通数据基线 → 预登记批次 → prepare → 启动 k6 → 到测量开始执行 fault，同时 k6 持续发压 → 等待发压/故障结束 → metrics → 校验并分类 → restore。每组合执行 restore，即使 prepare 部分失败。批次身份由 driver 在 prepare 前持久化，cleanup 读取完整 journal，不依赖 prepare 成功返回。环境必须隔离且外部确保独占，inspect 输出普通业务数据的稳定投影（不含压测批次）。所有中间文件权限 0600，目录 0700。

connection 设置 REQUESTED_USERS/EFFECTIVE_USERS=目标连接数、CONNECTION_RATE=20、STABLE_SECONDS=60、MEASURE_SECONDS=300、DISCONNECT_SECONDS=60；测量起点为 ROUND_START_MS + level/20 秒 + 60 秒。message 设置 TARGET_RATE=目标消息速率、EFFECTIVE_USERS=配置消息连接数、WARMUP_SECONDS=60、MEASURE_SECONDS=300、DRAIN_SECONDS=60；测量起点为 ROUND_START_MS + 60 秒。两个场景都绑定同一 batchId/roundId/groupSize=10、summary 路径、manifest、credentials 和 WebSocket URL。非零 k6 退出拒绝本轮，包括 108，保留 summary 供排查；不把有强制结束的结果当作成功。fault 输出 status、实际 startedAt/finishedAt、operationOutcome 和 operationEvidenceFile，时间必须落在测量窗口内。

hook 以 argv 执行，单次 180 秒上限，k6 以 ramp+窗口+30 秒上限执行。取消时停止活动子进程组并执行 restore。外层超时必须在杀掉协调器进程组后单独启动 driver cleanup，并给予独立 600 秒预算；cleanup 幂等执行 restore/cleanup，再 inspect 与基线比较。未完成 cleanup 保留 journal 和基线供重入恢复。30 组合固定档位总预算约 6 小时，默认 12 小时包含每组合 hook 预算；仍保留显式总超时配置。

## 可靠性与交付

只有 production-candidate 环境可进入生产容量门禁；local 可以直接执行 driver 调试，但不能获得生产证据。禁止无配置时默认向本机业务栈发压。恢复失败停止矩阵。inspect 还输出实际节点清单和资源规格；driver 核对 Broker/Gateway 数和 voter 数，报告绑定环境配置 SHA-256、节点规格、driver/k6 版本。环境声明不替代真实部署确认，hook 的真实性仍需部署环境 Review。

metrics 输出 metrics、pressureEffective 和 observations：每个数值指标都有 source、rawFile、sampleCount、startedAt、finishedAt；rawFile 必须存在且非空，时间覆盖整个测量窗口，sampleCount>0。source 必须明确查询或 summary 字段与分母。k6 使用 --summary-export 保存未转换的原始 metrics；driver 核对 connection 的 planned/attempted/opened 计数存在，message 的 sends/successes/expectedDeliveries/ACK 计数存在，分母必须大于零，之后才允许读取业务 summary。未出现成功或 ACK counter 按无证据失败，不能被 summary 的默认零掩盖。

非适用指标不冒充观测：connection 的 businessSuccessRate、ackSuccessRate、receiveLatencyP95Ms/99Ms，以及非故障阶段的 migrationP95Ms/electionRecoveryMs、非重建阶段的 snapshotAckP95Ms，可显式声明 applicable=false、reason；其数值固定为 0 只是旧契约的占位，门禁不使用这些非适用指标判断对应 workload。lookup/snapshot correctness 的 rate/count 和所有资源仍必须有真实监控证据；若无事件但语义可评估，source 必须提供计数分母与查询依据。其他适用延迟无事件就失败，不虚构分位数。资源 observations 必须提供所有节点 IDs、采样间隔不超过15秒、最大间隔不超过30秒、覆盖整个测量窗口；所有适用分位数提供 observationCount>0，counter 记录窗口边界与 reset 处理说明。driver 和门禁验证这些条件。

## 验证

Node 契约测试以替代 k6 和 hook 的独立进程验证参数、固定档位、缺采样/原始计数、非零退出、超时、故障失败恢复和普通数据比较；增加外层取消后cleanup、进程组残留、prepare部分成功、cleanup超时重入、基线仅首次采集、local证据拒绝。替代边界不证明真实容量。运行真实矩阵需要环境配置和真实监控查询；缺环境仍 fail-closed。同步 load-test README、Harness、当前计划和 PROGRESS；完整门禁按根 AGENTS 执行。

## 备选方案与决策

直接复用 load_test.sh 会操作固定本机单 Broker 栈，无法适配候选拓扑；完全重写协议发压会重复现有 k6 能力。选择复用场景、环境 hooks 隔离部署差异。hook 是实际环境适配，不允许用固定数字模拟生产指标。

## 独立 Review

第一轮 Block：修订逐档选择掩盖失败、总超时清理、窗口、采样来源和批次恢复。第二轮 Block：补充原始计数和非适用指标。第三轮 Revise：固定适用矩阵并补充连接测量末在线率；Reviewer 复核后结论 Approved。

## 固定适用性与连接验收

仅 connection 可将 businessSuccessRate、ackSuccessRate、receiveLatencyP95Ms/99Ms 标记非适用。仅 gateway-switch、broker-drain、leader-route-broker-failure 要求 migrationP95Ms 和 snapshotAckP95Ms；其他阶段这两项非适用。仅 leader-route-broker-failure 要求 electionRecoveryMs；其他阶段非适用。其余指标全部适用，hook 无权扩大豁免。connectionSuccessRate 取握手成功率和 measurementEndOnline/plannedConnections 中的较低值，必须满足99.5%，避免握手成功掩盖测量末掉线；原始measurement_online计数必须存在。反例测试证明握手100%而在线90%归类service_failed。

## 验收分轨决策（2026-10-10）

用户确认将 Broker 代码 Review 与生产候选容量定规格分开管理。代码 Review 以聚焦测试、架构、启动、行为、E2E、routing-ha、quick、clean、full 和独立 Review 为验收边界；这些证据不构成生产容量结论。原 T10 的真实候选环境、30 轮矩阵、阈值、恢复、机器规格及独立复评全部移交独立容量计划，不取消、不降低，也不把本机 smoke 当成正式测量。

容量计划先在隔离候选环境做短时演练，只验证六个环境 hook、真实采样来源、故障注入、恢复与普通数据不变；演练不产生容量资格。环境就绪后再执行完整矩阵取得正式结论。日常开发不重复运行长时正式矩阵，但更改容量语义、阈值、发压或采样契约时须重新评估既有正式证据的新鲜度。

每个拟推荐的 3-voter 或 5-voter 拓扑都须单独完成环境演练。各拓扑的 connection/message 目标档位依据预期业务峰值事先声明，不在测试后选择较低通过档。固定档位的三次稳定通过只证明该档位的已验证承载界限；推荐负载最多取其 70%，更高档位和系统绝对容量上限仍为待测，不从单档结果推断。
