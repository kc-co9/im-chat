# Broker 启动可信配对职责内聚设计

> 状态：已批准。用户批准删除 `BrokerIdentityConfiguration`，将其职责合回 `BrokerClient`；独立设计 Reviewer `a6eeb49b80726e483` Approved、findings none，不删除安全校验。

## 背景与决定

五角色前缀固定后，身份对象只保存一个 bootstrapTargets 列表，其校验、发现交集和节点目标转换全部服务于 BrokerClient。删除该公开包装类型及 identities 字段，客户端构造器最后一个参数改为 `List<BoltTarget> bootstrapTargets`。不保留兼容构造器，不合并 GatewayIdentityConfiguration。

## 行为与所有权

在客户端构造流程先防御性复制并校验部署配对，再查询发现地址并取交集；校验仍在发现调用前。保留 null/空列表拒绝、固定Broker前缀和非空身份后缀、地址对应不同身份或身份对应不同地址的冲突拒绝。重复相同配对仍可归一，发现未知地址仍不被信任。只保存筛选后的不可变启动目标，不保存无用配置副本。

新增private方法承担完整启动配对校验/筛选职责，原异常类型及信息保留；`target(BrokerNode)` 仅使用固定常量与节点地址/ID生成目标，原调用和method reference迁移到客户端自身。committed拓扑刷新、调用预算、重试及认证职责不变。配置Bean仍提供显式完整配对，不从metadata、首响应或证书学习首次信任。

## 范围与兼容

修改BrokerClient，删除身份类型，同步Gateway/Account/Message装配、SDK及消费方测试、E2E直接构造调用。将旧对象私有校验测试移到真实BrokerClient构造入口，节点转换期待值采用独立字面量，不通过反射调用新私有方法。新增null/反向冲突、可变列表快照及发现交集断言时使用确定性输入。公开Java构造类型变化，外部消费者须重编译；配置键与wire不变。

备选为保留单字段包装对象（多一层调用协议，当前无独立复用）或新增Factory/Resolver（不减少复杂度），均不采用。删除对象不是删除信任边界，不改变固定前缀设计或通用Plugin能力。

## 验证与执行

等价职责搬迁不强造RED：先运行BrokerClientTest/BrokerClientRoutingTest基线，迁移后同层验证，再运行三个消费方配置测试；精确比较原校验/筛选和目标构造token，保留真实公共入口行为断言。E2E引用迁移但依赖私有JRaft缺失时不重复同因构建，标记未编译。full/clean/module/architecture暂停、既有Revise和历史失败保留。

纳入现有T3，修改前保存真实源码before；同步SDK/Server README中构造说明及当前设计取代范围，不重写旧事实。无新增工程规则，不新增checker。附带用户要求的BoltRequestContext六字段中文注释仅影响注释和record排版。独立Review依据精确差异、窄测试和未补齐的编译证据判定，不以源码无问题代替验收。

## 独立 Review

独立设计 Reviewer `a6eeb49b80726e483` Approved、findings none，仅批准本增量，实施与完整验收另见当前 T3。用户另明确删除 BrokerRouteCallerInterceptor 中构造依赖判空；仅该处删除，不改变请求身份与成员校验，不推广到其他构造器。
