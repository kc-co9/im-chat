# Bolt 连接层身份认证原型设计

> 状态：已批准，仅限测试原型。用户批准先验证连接层方向，独立设计 reviewer `0461448d-b90c-462` Accept/findings none；不切换生产调用链，纳入既有 A5，不创建新 active 任务或 Git 提交。

## 与后续生产设计的边界

后续生产方案另见 [Bolt 连接层认证与统一 invoke 生产设计](2026-10-20-bolt-unified-invoke-design.md)。该文为待 Review 的未实施提案，包含首次发现身份来源等待用户决定项；不继承本原型的批准或测试结论。本文继续只记录测试原型及其已确认的讨论边界。

## 后续范围确认（用户批准）

生产方案讨论继续沿用当前自定义证书 URI 身份规则：受信 TLS 证书在配置命名空间内恰好一个 URI，且等于目标 Broker 的完整预期身份；允许其他命名空间 URI。此次不迁移为标准 X.509-SVID、不引入 SPIRE 或外部身份服务，也不把 spiffe:// 字符串视为完整 SPIFFE 合规保证。保留现有证书签发、部署和续期责任，不能因无新身份服务就省略这些运维要求。

后续重点是将认证职责下沉到连接创建/Channel，并完成尚未验证的自动重连、轮换与预算边界；本范围确认不等于批准直接把测试原型切换生产，正式生产设计仍需独立 Review 和验收策略。

## 后续证书更新边界（用户批准，生产设计待 Review）

首版不实现证书/信任材料热加载；运维在到期前更新材料并滚动重启对应客户端/服务端，使新连接使用新材料。修改文件不承诺现有SSL Context或连接自动生效。认证旧连接证书到期后禁止发送新的业务请求；已发送请求仍按原RPC预算完成，不因该到期事件主动打断。实现需明确可用截止时间取自经验证证书链的范围，并在发送屏障处检查，不只在首次握手校验；使用墙钟判定证书绝对有效期，与请求单调预算分开。此政策只确认生产方案的一项取舍，仍需正式设计Review和过期/在途/滚动替换回归，不批准直接切换生产。

## 后续统一调用方向（用户批准，生产设计待 Review）

生产设计应统一现有 Bolt 客户端调用的身份约束，不只迁移 probe/report。最终公开入口统一为 invoke，连接层承担认证；受控调用目标须绑定地址与预期对端身份，固定证书/URI规则来自配置。不能只改方法名或保留另一条同类地址调用绕过认证；接收方caller认证、成员准入及业务校验继续保留。全部实际调用需区分定向节点与允许的服务身份，不能机械给所有服务绑定单个Broker ID。

范围盘点包含 BrokerPeerAdapter 的probe/report/lookup/cleanup/read-index/lease/fence、BrokerClient（含bootstrap发现）、GatewayClient及BoltRpcClient通用入口，排除Dubbo同名invoker。真实caller清单 `/tmp/bolt-unified-invoke-callers.txt` 仅为本机调查证据；正式生产设计须把身份来源、发现可信边界、启动前配置、普通/认证旧入口退出及SDK/测试迁移映射写入正文。开发明文模式若保留须显式配置并限制能力，不得认证失败后自动回退。该确认不等于批准生产接口具体类型/热加载/提交或直接实施；正式设计及独立Review仍待完成。

## 摘要

验证 Bolt 1.6.6 是否可通过公开连接工厂/管理器与 Channel 扩展，在交付业务连接前完成 TLS 和指定身份校验，并按地址及预期身份隔离池。原型只在 im-bolt 测试源码内实现，不更改 BoltInvoker、生产 Bean、Broker 调用或依赖，不以测试成功直接批准生产迁移。

## 背景与已知事实

当前 invokeAuthenticated 在取得 Connection 后验证 TLS 与 URI SAN，再用同一连接调用。目标是让固定身份规则下沉到连接层，而不是仅隐藏参数。

本地 bolt-1.6.6.jar 的 javap 表明 RpcClient.setConnectionManager、DefaultConnectionManager.setConnectionFactory 和 createConnection(Url) 可扩展；Url 的显式 uniqueKey 供连接池索引，properties 本身不改变池 key。原生 getConnection/createConnection 仅等 TCP active；CONNECT 为异步通知，并非身份验证屏障。原生 SSL Context 初始化私有，不能直接覆盖或以配置注入动态 TrustManager。源码证据为当前依赖字节码，测试必须证明可用性，不能据方法存在推断时序安全。

## 目标与非目标

- 目标：真实 TLS 下验证普通 Url RPC 经认证工厂/管理器取得连接后才发送；同地址不同预期身份隔离；失败连接清理、重连、并发等待的安全性。
- 非目标：不更换生产客户端、不新增生产配置/SDK对象、不移除 invokeAuthenticated、不切换 SPIFFE 规范、不改 Bolt/JRaft 依赖、不声称硬返回期限。
- 原型沿用现有唯一 namespace URI + 精确预期身份规则及现有测试证书，不能因使用 spiffe:// 就声称完整实现 X.509-SVID。

## 方案概览与详细设计

测试内提供固定 URI 命名空间校验策略；动态 expected identity 附着到不可变目标 Url 的属性，池 uniqueKey 明确包含地址及完整身份且使用无歧义编码。新 Url 每次独立构造，禁止更改缓存解析 Url 或维护全局 address→expectedId 可变表。

首选 ConnectionFactory 装饰原生 RpcConnectionFactory.createConnection(Url)：在原生连接创建后等待握手、验证可信证书唯一 URI、成功才返回工厂调用方/进入池；失败关闭该次新建连接并传播异常。必须覆盖拒绝缺失身份的工厂调用路径，不能让 String 重载或重连回退成未认证创建。固定策略在工厂构造时提供，按次身份在 Url 中传递，普通 invokeSync(Url,...) 路径直接消费，不再由业务 Invoker 读证书。

原生工厂可能提前发送 CONNECT 事件，该事件仍不作为授权或业务可用信号。原型需要明确区分“原生 TCP通知”与“受支持管理调用的连接交付”：如果早期事件回调或 Connection overload 能绕过工厂完成屏障，则应通过 Channel outbound gate/管理器check认证标记阻断并验证；若公开hook无法在不侵入原生内部协议的情况下实现，应输出有界阻塞结论，而不是默许绕过并称已认证连接。允许原型证明部分边界并记录未通过条件，但不得把部分通过当生产可行性完成。

实现可在测试内利用公开扩展点核验该策略；若需复制大量原生初始化、反射访问private或fork才能成立，停止并报告，不实施这些备选。每条连接认证完成状态绑定Channel/Connection不可变事实，不能把对B验证复用于预期C；认证必须先于业务BoltRequest的任何发送。TLS协议自身握手报文不属业务请求。

## 可靠性与安全

等待使用有界 Future/握手预算，不用 Thread.sleep。不得在 Netty event loop 阻塞等待本事件循环完成握手。失败关闭本次候选连接，最终关闭客户端/服务器、未入池channel、并发执行器和测试证书临时目录；不关闭其他正在使用的池连接。失效池条目可重新创建，并仍执行同身份校验。目标身份属性/池key由受控调用方构造，不信任远端业务负载。失败cause保留；原型不改变生产TRANSPORT/LOCAL分类。

## 验证策略

测试用实际 RpcClient/RpcServer、SslHandler 与受信/错误/多URI/无URI证书；目标接口为无额外校验的 invokeSync(Url, BoltRequest, timeout)，服务端计数器证明非法请求handler零次。关键用例：

1. 正确身份成功，重复获取复用已验证连接。
2. 错误/歧义/明文或无身份请求在业务handler前失败，连接不留在可用池。
3. 延迟握手期间业务发送不放行；超时后可清理和再次调用。
4. 同地址不同预期身份不同池key；已验证B后请求C不能复用B通过。
5. 并发首次调用共享同一目标但都在认证后返回；B/C并发无身份覆盖。
6. 关闭已认证连接后重连仍验证，错误身份不会因旧认证标记放行。
7. 早期CONNECT事件尝试使用连接的绕过探针；不能只测handler最终计数而忽略提前写入。能证明不发送时方可关闭该退出条件，否则明确blocked。

先针对不带屏障的原生工厂运行关键回归取得 RED，再增加测试内最小屏障取得 GREEN；对于不能实现项保留 RED/Block 不弱化断言。运行 im-bolt 聚焦/全测，再独立代码审查，最后按既有门禁执行 architecture/quick/clean/full。生产配置没有变化，不宣称覆盖真实Broker路由HA或生产时源预算。

## 原型结果与生产退出边界

测试内公开hook原型已实现，独立代码 reviewer `67e6d4d5-1e52-4a6` 两项证据finding修复后 scoped Accept；此结论不是生产迁移批准。

真实TLS的Url RPC可通过工厂/Channel发送屏障运行，证书错误/歧义/无URI/明文及缺预期身份均在业务前拒绝。CONNECT回调在握手pending时直接使用Connection重载：无gate对照在handler仍为0时已向编码层放行2次，加gate后暂停窗口内业务forwarded为0，独立计数器区分attempted和forwarded。同址B/C用无歧义key隔离，四并发无身份覆盖，正确B复用，C拒绝原因链明确为身份错误而非任意timeout。失败候选及teardown跟踪channel断言关闭完成成功且!open。

测试实现以ThreadLocal在同步工厂创建链暂存目标身份，bootstrap.clone.attr在connect前绑定，finally remove；不使用全局address→identity表，不复制initializer、不fork反射。该ThreadLocal仅证明当前同步创建路径，不代表任意重入或后台创建安全。固定prefix策略仍为项目自定义URI规则。

未完成退出边界：重连只证明显式关闭并manager.remove后的重新创建，不包括自然失效回收/后台Reconnector；未测试证书轮换、其他handler通过ctx.write绕过尾部gate、完整端到端预算或初始化失败故障注入。构造失败清理已有结构性处理，但未做该注入，不能声称无限执行无泄漏。生产方案必须先决定这些边界及认证失败分类/连接创建预算/多命名空间配置；本次不据6项测试直接承诺所有退出条件已完成。

## 运维、风险与回滚

仅测试原型，无生产发布或配置迁移。回滚只删除任务归属的测试源码，不撤回用户已有生产WIP。固定命名空间策略只是试验输入，生产多命名空间/连接池身份key稳定编码/证书轮换/握手线程模型和失败分类的最终契约需原型结果后独立设计批准。

## 后续文档与执行

实施归现有 [Broker Adapter A5](../exec-plans/active/README.md)，WIP=1。README仅在结果确定后补“测试原型、不代表生产接入”说明；生产Architecture/Security不提前宣称已下沉。无新的通用规则，不因原型放松现有认证保证。

## 附录 A：备选方案与依据

- 保留当前逐调用认证：已有真实TLS与同连接保障，但身份策略仍在Invoker；作为对照，不删除。
- 工厂/Channel gate：符合连接职责，需证明握手、入池、发送时序和所有重用路径；本次推荐验证。
- 仅CONNECT事件校验：异步通知不能阻止其他路径使用，不能作为独立安全方案。

版本依据：本地SOFA Bolt 1.6.6 API/字节码；SPIFFE规范 https://spiffe.io/docs/latest/spiffe-specs/x509-svid/ 仅用于说明标准身份唯一性与本项自定义前缀规则不同，不声称实现标准SPIFFE。

## 附录 B：决策记录

| ID | 问题 | 决策 | 理由与影响 |
|---|---|---|---|
| D1 | 优先快速减参还是职责下沉 | 优先连接层原型，生产不切换 | 用户明确要求长期结构，不只少参数 |
| D2 | 身份由何处提供 | 固定策略工厂拥有，完整预期身份绑定目标 | 不把业务身份写死通用插件 |
| D3 | 是否接受异步事件后置验证 | 不接受作为唯一防线 | 认证前业务发送必须阻断 |
| D4 | 如何处理无法实现的退出条件 | 保留blocked证据，不复制大量原生内部/fork/反射 | 原型用于验证能力，不预设成功 |

## 附录 C：独立 Review

reviewer `0461448d-b90c-462` 设计Accept，仅批准有界测试原型。实现 reviewer `67e6d4d5-1e52-4a6` 首轮Revise：拒绝原因断言过宽、关闭完成及部分初始化清理证据不足；已收紧cause/message、closeFuture完成成功/!open断言与构造清理并限制证据措辞，第二轮scoped Accept。不能用TLS缓存或最终handler零次伪称屏障成功；生产迁移及未测退出条件另行批准。
