# Coding Guide

本规范是新增和修改代码的稳定基线，优先保证职责清晰、依赖方向稳定和行为可验证。历史代码按实际触碰范围渐进收敛，不为了统一格式进行无关重构。自动检查范围见 [Harness Guide](HARNESS_GUIDE.md)。

## Java 与可读性

- 使用 Java 21 和显式类型，不使用 `var`。
- 生产代码引用类型时使用 `import` 和简单类名，不在注解、表达式、构造调用或方法体中直接书写全限定类名。只有两个同名类型确实同时出现在同一源码文件、无法通过清晰的边界转换消除歧义时，才允许对其中一个使用全限定类名，并由 Review 确认。
- 一个类只承担一个清晰职责；类名表达角色，公开方法名使用调用方能够理解的业务动作。
- 包按稳定职责和依赖方向组织。使用 `model`、`codec`、`spi`、`client`、`repository` 等已有语义，不把能力接口、技术实现和数据模型堆在同一包，也不按类数量机械拆包。单个 Adapter 可直接放在 `adapter` 根包；只有形成多个同类能力或明确独立依赖边界时才增加二级包，不为预期扩展创建只有一个类的空洞层级。
- 方法优先提前返回。复杂条件提取为能够表达业务判断的方法，避免嵌套分支和难以阅读的 Stream 链。
- Stream 用于纯转换、过滤和聚合；删除、异常隔离、多步副作用和状态修改使用普通循环。
- 集合返回不可变结果；无结果使用空集合或 `Optional.empty()`，不返回 `null`。只有明确允许空值的回调或框架契约才返回 `null`。
- 公共契约、配置项、复杂业务规则、生命周期和非显然失败行为提供简洁 Javadoc。自解释的私有方法、getter 和简单 Controller 不写重复注释。普通说明使用 `/* ... */`；Javadoc 使用多行 `/**`、` *`、` */` 结构，类注释统一采用多行 Javadoc；字段和 record 组件的简短说明优先使用 `/* ... */`，只有需要生成 API 文档时才使用 Javadoc。
- record 属性较多时每个组件单独换行，并为每个组件提供简短块注释，避免长参数列表降低可读性。
- 日志使用项目统一 Logger，不输出密码、Access/Refresh Token、指纹、完整消息内容或用户隐私。

## DDD 分层

业务服务使用以下职责边界：

```text
interfaces -> application -> domain
                    |          ^
                    v          |
               adapter   infrastructure
```

- `interfaces` 只做协议适配、反序列化、边界校验和结果序列化，不承载业务编排。
- Spring Security `AuthenticationProvider`、`OAuth2AuthorizationService`、Filter 等入站框架 SPI 遵循相同边界：同一入口需要认证、授权状态迁移、权限解析或其他领域协作时，由应用服务完整编排并返回应用层结果；SPI 实现只转换框架输入输出和协议异常，不直接访问领域 Repository 或 MyBatis 类型，也不把用例拆成应用服务调用和领域服务调用。`findById`、`findByToken` 等查询 SPI 不修改领域状态；需要撤销、消费或记录失败时，由认证 Provider 调用明确的 Command 用例。纯技术接口适配（例如 `PasswordEncoder` 委托领域密码能力）不因此强制增加薄应用服务。
- Spring Authorization Server 使用未知 Token 类型查询授权时，只把请求提交的原始 Token 恢复到摘要匹配的凭据；其他授权码、Access Token、Refresh Token 和 ID Token 保持摘要占位，避免同一原始值对应多个凭据并改变默认 Provider 的活跃性判断。
- OAuth Claims 以 `Map<String, Object>` 保存为 JSON 时，数据库 Transformer 保留 JSON 边界值；进入 Spring Authorization Server 的适配 Transformer 再恢复框架要求的标准类型。`iat`、`exp` 和 `nbf` 在构建 Spring Token 前使用 `Instant`，不能把 JSON 反序列化产生的字符串直接交给 Token 活跃性判断。
- `application` 只放应用服务和用例编排；事务、锁和跨领域协作由应用服务使用，但锁场景常量、审计切面、应用层异常等可复用组件放在模块顶层 `support`，不在 `application` 下建立非应用服务子包。
- `domain` 保存业务状态和规则，不依赖 Spring、HTTP、Bolt、Dubbo、Nacos、Redis、Mapper 或持久化实体。
- `infrastructure` 实现仓储、技术型领域能力、缓存、消息和外部集成。
- `adapter` 将外部服务能力转换为当前业务可理解的语义；它不是 inbound Controller/Handler 的替代名称。
- `transformer` 负责接口、应用、领域、持久化和 SDK 模型之间的边界转换。
- 运行态监控和诊断属于技术能力，不建模为业务领域聚合。DDD 业务服务仍按标准分层组织；Broker 等技术运行模块可以在 `support/<capability>` 下按 `model`、`service`、`state`、`tracker` 等稳定职责垂直组织完整技术子系统，不为其制造虚假的业务 Domain。远端诊断访问使用 Adapter 语义，不伪装成持久化 Repository；只有出现真实替代实现或需要稳定依赖倒置接口时才额外抽象 Gateway，不为唯一 Adapter 创建同形接口。

可独立部署的 DDD 业务模块统一使用稳定的顶层骨架：`interfaces`、`application`、`domain`、`adapter`、`infrastructure`、`model` 和 `transformer`。业务能力可以在 `domain`、`interfaces` 内继续分组，但配置属性和 Bean 声明放在 `infrastructure/config`，生命周期组件放在 `infrastructure/lifecycle`，Repository 技术实现放在 `infrastructure/domain/repository`；不因模块入口或 UI 形态不同而另造平行分层名称。

模块只有一个领域能力时，领域层直接使用 `domain/model`、`domain/repository` 和按需存在的 `domain/service`，不增加与模块语义重复的能力包。出现多个相对独立的领域能力后，再使用 `domain/<capability>/model`、`repository`、`service` 分组；不为尚未出现的扩展预建包层级。

领域模型和领域服务的业务标识、金额和状态载体使用值对象或枚举，不直接传播 `Long`、`String`、`Boolean` 等基础类型。时间类型和枚举可以直接使用；值对象内部、接口 DTO、CQRS 模型、持久化 DTO 和事件边界允许承载基础值，并由 Transformer 转换。值对象的必填、容量、格式、归一化和脱敏规则必须由规范构造入口保证，不依赖调用方选择某个可绕过的工厂方法。

聚合根沿用项目共享领域骨架：继承 `Identification` 获取实体身份能力，并按需要实现 `Validator`；使用 `Serializable` 时声明 `serialVersionUID`。聚合需要由 Lombok 生成相等性时使用 `@EqualsAndHashCode(callSuper = false)`，由聚合自身字段表达相等性并排除持久化技术主键；不使用 `onlyExplicitlyIncluded` 和字段级 `@EqualsAndHashCode.Include` 定制业务 ID 专属相等性。组成可序列化聚合的值对象也必须可序列化。普通领域对象不为了形式强行继承聚合根基类。

继承 `Identification` 只是聚合根的身份骨架，不代表模型已经符合聚合要求。新增聚合不使用类级 `@Data` 或 `@Setter` 暴露任意状态写入；构建入口统一校验不变量，生命周期变化通过具有业务语义的领域方法完成。属于同一协议阶段或生命周期的多组字段应组合为不可变值对象，避免把数据库扁平列原样复制成可任意修改的贫血聚合；聚合负责状态转换是否合法，Repository 在统一保存入口通过乐观锁等技术机制保证持久化并发一致性。

数据库技术主键、领域业务 ID 和可识别业务编码必须按语义分离：`Identification.pkId` 只标识当前表行，不进入领域 Builder、外部契约或跨表关系；聚合之间通过稳定业务 ID 关联；供配置、页面和协议 Claim 使用的短编码不能冒充数值业务 ID。拥有独立身份、生命周期或一对多基数的接入配置应建模为独立聚合并通过业务 ID 归属业务应用，不因 Grant Type、传输协议等技术分支复制平行聚合。能够独立存在和重试创建的两个聚合使用独立 Command、应用用例和事务边界，不为了页面操作便利强制在一个注册方法中同时保存；只有业务不变量要求原子共生时才由一个用例编排。

领域服务必须承载跨实体规则、多个领域对象协作或不自然属于单个聚合的业务决策。纯 codec、digest、Redis、HTTP 或委托调用不是领域服务；JWT、HMAC、BCrypt 等实现放在 `infrastructure/domain/service`，领域接口只表达签发、认证、密码校验等业务语义。不引入仓库未采用的 `application/port`、`application/model` 平行体系。

跨多个领域对象的全量同步、差集计算和状态迁移属于领域服务；应用服务只负责将 CQRS 输入转换为领域定义、调用领域服务并保存结果。领域服务不直接接收 Command、Query 或接口 DTO，也不把同步决策散落在应用服务中。

自动过期且只用于限流、防暴力破解或短期故障隔离的计数、窗口和限制属于技术保护状态，放在 `support` 或 `infrastructure`，不映射为聚合业务状态，也不进入业务表。只有能够由业务人员管理、参与业务状态机并需要长期追溯的封禁、停用等事实才进入领域模型。技术保护组件必须定义原子更新、过期、成功清理和依赖不可用时的安全策略，并通过聚焦行为测试证明。

单个聚合能够独立完成的字段变更由聚合行为表达，避免用一个通用资料更新方法混合多个独立业务意图。依赖 Repository 判断跨聚合唯一性等规则时使用领域服务；应用服务只负责加载聚合、调用领域行为并保存，不内联领域判断。聚合状态变更通常返回 `void`，不以布尔返回值驱动应用服务跳过持久化；只有调用方确实需要旧版本、授权结果等领域事实时才返回明确的领域结果对象。

Repository 承担聚合生命周期删除时，聚合只校验删除所需的业务前置条件，不制造随后不会由 Repository 保存的内存状态。软删除字段由持久化机制维护时，应用服务先调用 `ensure*` 领域校验，再调用 Repository 的删除语义；只有删除事实作为聚合状态被正常保存时，才由聚合执行状态变更后统一 `save`。

聚合从持久化状态重建时使用 Builder 或明确的重建数据对象承载多字段状态。Transformer 可以直接填充 Builder；只有重建包含业务决策或多对象协作时才进入领域服务。Builder 承担领域不变量校验时必须手写：Builder 内部持有通过私有无参构造器创建的领域对象，各方法直接填充该对象字段，不再重复声明一套 Builder 属性；`build()` 在返回对象前统一执行校验。不使用 Lombok `@Builder` 绕过该入口，也不为此保留长参数构造器。不要为简单 `new` 或 `build()` 增加无价值的委托方法。

数据库自增主键等仅用于持久化映射的标识不进入领域构造器或 Builder。需要恢复此类标识时由 Repository 在领域对象构建完成后，通过持久化基类能力回填；领域构造器和 Builder 只接收业务身份与业务状态。

当前 Broker 的少量历史 `domain` 类仍兼有 Spring 装配和运行编排职责，不是新增代码模板。修改时按风险渐进迁移，不为满足文档进行无关包移动。

Servlet MVC 的 Springdoc 运行时依赖由 `im-web` 统一提供。文档路径、扫描范围和 SecurityFilterChain 访问策略仍由消费应用拥有；不要在通用 Web 插件中写入业务路径或默认匿名放行文档。

直接依赖 `im-web` 的可部署 Servlet MVC 应用显式声明 Swagger UI 与 API description 路径；SDK、Plugin 和 Facade 不声明应用路由。管理 BFF 的文档端点使用 IAM Session 保护，不能因文档属于技术接口就加入匿名前端资源列表。

## 管理后台 UI

- IAM、Admin、Audit 和 Monitor 使用 Vue 3、Element Plus 与 Element Plus Icons，并遵循 `docs/design-docs/2026-09-09-unified-management-console-design.md`。统一风格不改变各应用的路由、API、权限和部署所有权，也不建立跨应用 UI 源码依赖。
- 管理工具采用安静、高密度的工作界面：深色导航、浅色工作区、紧凑筛选和表格、克制状态色。页面章节不包装成装饰卡片，不使用营销 Hero、渐变球或超大标题；圆角不超过 `8px`，字体不随视口缩放，字间距为 `0`。
- 创建、编辑和详情优先使用右侧 Drawer；短确认和敏感单字段输入使用 Dialog。危险操作明确目标和后果后确认。脏表单关闭提供“继续编辑/放弃修改”，禁止 `window.confirm` 和 `window.alert`。
- 列表只直接展示一个高频安全操作，其余放入 More 菜单。刷新、关闭、下载等熟悉命令使用图标并提供可访问名称；状态同时使用文字，不仅依赖颜色。
- 页面明确区分初始加载、数据、空结果、失败重试、保留旧数据的刷新失败、局部提交中、成功和失败。写操作失败保留输入，成功后重新读取权威数据。筛选必须在服务端分页前执行；空白可选条件不发送，禁止把当前页过滤伪装成完整搜索。
- 每个后台只展示自己拥有的本地菜单。其他控制台通过 typed server configuration 返回的绝对 URI 执行顶层导航，不在浏览器包中硬编码生产地址。

## 应用服务与 CQRS

- 应用服务以 `*AppService` 表达一个边界内的用例编排；按聚合、用例主体和事务边界组织职责，同一聚合的认证、管理和关系维护等紧密命令可以合并在一个应用服务中。不要仅按方法类别拆出只有单一调用方的薄应用服务，也不要为了减少类数量合并不同聚合或不同事务边界的用例。
- 应用层 CQRS 查询意图使用 `*Query`；Repository 或持久化查询的过滤条件使用 `*QueryCondition`。新增过滤对象不使用语义模糊的 `*Criteria`、裸 `*Condition` 或持久化层 `*Query`。
- 公开用例方法接收单个 Command、Query、Params 或其他明确输入对象，不直接接收 `String`、基本类型等标量业务参数，也不保留无调用方的标量重载。
- Command、Query 是应用层输入边界，字段使用 `String`、包装数值、`Boolean`、时间、边界枚举或 `im-common` 中稳定的通用边界值对象，不直接持有领域聚合、业务值对象或 Repository 查询条件。分页 Query 可以直接持有 `Paging`，由接口 Transformer 从外部分页参数构造；应用服务在用例入口将其他基础值构造成领域对象和内部查询条件。必填数值使用包装类型并显式拒绝 `null`，避免 primitive 默认值掩盖缺失输入。
- 可选文本查询条件在 Command/Query 构造边界先将 `null`、空串和纯空白统一归一化为 `null`，再执行字段关联校验；不得把空串继续转换为要求非空的领域值对象。
- 框架回调提交已经完成协议处理的最终状态快照时，使用一个保存 Command 和应用用例，不根据状态变化来源再次执行协议动作。只有框架回调要求应用执行不同业务流程时，才由适配层选择具体 Command；不要在通用 `handle` 命令中加入可空判别字段和多组互斥参数。
- 查询和有业务结果的命令返回 DTO、Result 或其他结果对象；没有结果的命令返回 `void`。
- `*AppService` 不直接依赖其他 `*AppService`。共享业务规则下沉领域对象或领域服务，共享技术步骤提取为合适的基础设施组件；跨用例组合由接口层分别调用，或由拥有完整事务边界的单个应用服务直接编排所需领域能力。
- 应用服务方法按“输入转换、用例调用、输出转换”分段书写；不要把边界对象构造、适配器调用和结果转换压缩到同一行，保持用例编排可读且便于审查。
- 审计、指标等具有统一前后置策略的横切关注点，优先复用项目已有注解与切面，避免在每个应用方法中重复注入执行器并用 Lambda 包裹业务。注解只声明该横切能力自身的语义，不重复承载由其他框架负责的策略；例如审计注解不复制 Spring Security 权限表达式。领域决策、状态机和跨聚合编排仍保留在领域与应用层，不下沉到切面。
- 管理应用通过 `im-audit-sdk` 发布 BUSINESS/SECURITY 审计，只声明稳定动作码、安全目标和必要说明；不得重新建立本地审计 Repository、MyBatis 类型、表、查询 API 或页面，也不得依赖 `im-audit-server` 实现。登录、Token 和协议事件等无法由确定性业务方法表达的事实显式调用 `AuditClient`；普通管理写操作及其他结果确定的应用用例优先使用 `@Audited`。`targetId` SpEL 引用方法声明的真实参数名，例如 `#request.clientId()` 或 `#command.userId()`，切面不得假定第一个参数统一名为 `command`。动态属性只在需要采集的方法参数上声明 `@AuditAttribute`：普通参数使用参数名，对象参数展开一层属性，成功结果自动按相同规则采集；不递归展开，空值忽略，不应记录的字段或 record component 必须声明 `@AuditAttribute(include = false)`。应用服务不得为动态属性创建专用 Resolver，也不得重复手写审计成功/失败 `try/catch`。接收端点本身不审计，避免递归。
- 审计契约不携带方法参数、返回值、请求体、异常对象、凭据、Cookie、Session/CSRF 标识、Token、SQL 或调用栈。事务提交时序、脱敏、失败隔离、重试和幂等属于行为契约，使用聚焦测试验证，不通过源码正则猜测数据流。
- 认证身份和授权信息由 Spring Security `SecurityContext` 管理，不复制到自定义 `ThreadLocal`。确需跨线程传播的客户端地址、User-Agent、Trace 等非安全元数据使用边界清晰的上下文对象，并在请求完成后清理；Servlet 请求上下文必须在 Spring Security 之前由 Filter 建立，不能只依赖 MVC Interceptor，否则登录和 OAuth 等安全端点无法读取；线程池传播必须使用项目选定的上下文传播机制及对应测试，不依赖裸 `InheritableThreadLocal`。
- 事务与分布式锁属于应用或基础设施边界。优先使用项目已有注解；只有动态锁键、需要返回值或注解无法表达时使用模板。
- `@DistributeLock` 的 SpEL 锁键直接返回业务标识或值对象，由锁切面统一转换为字符串；不要在表达式中显式调用 `toString()`。
- 固定要求在事务成功提交后执行的 Adapter 外部副作用，可以在 Adapter 方法上声明 `@AfterTransactionCommit`；需要延迟锁、状态修改和外部调用等完整步骤时，由应用服务通过提交后模板编排整段操作。已经进入 `afterCommit` 回调后，如果后续状态变更不依赖数据库事务，应直接执行；不得为了再次触发提交后回调而人为开启无业务必要的 `REQUIRES_NEW` 事务。确需新事务时必须说明其持久化一致性目的。
- 不增加仅供单元测试使用的生产构造器、兼容方法、wrapper 或 no-op/fake 实现。测试通过正式 Bean、`@TestConfiguration` 或直接依赖替身控制环境。

## Adapter 与跨服务边界

- Adapter 的公开业务输入和输出使用领域对象、聚合或领域结果，外部 SDK 的 Params、Result 和 DTO 只停留在 Adapter 内部。
- 纯命令 Adapter 可以返回 `void`，不为了形式统一创建空结果对象。
- Facade/SDK 只定义跨模块公共契约，不依赖 server 实现。Facade 与 SDK RPC 的公开请求模型使用 `*Params`，Facade 请求统一放在 `facade.params`；响应放在对应 DTO/Result 包。`Command`、`Query` 及对应目录保留给 Server 应用层 CQRS，不泄漏到跨模块契约。跨模块分页参数直接复用 `im-common` 的 `Paging`，不在每个 Facade 重复声明 `pageNo/pageNumber/pageSize`。Bolt、Dubbo 等框架类型只出现在 client、RPC adapter、configuration 或 infrastructure/support。
- Nacos 类型只出现在 `im-nacos`、Broker SDK 的服务发现封装和应用配置边界，不进入业务、domain 或 facade 契约。
- RPC Handler 解析 typed params、调用应用或领域门面并返回 typed result，不实现路由、迁移或 Gossip 算法。
- Dubbo Provider 由 `im-dubbo` 的统一切面将 `BaseException` 转换为稳定 `RpcException`，并隔离未知内部异常。RPC 实现不手写 `call/run`、逐方法 `try/catch` 或业务专属远程异常包装；调用方在自身 HTTP、审计等边界解释通用错误码。
- RPC 认证元数据使用 `im-dubbo` 保留 Attachment 传播，不进入业务 Params、DTO 或事件契约。Consumer Filter 只在当前调用期间设置 Token，Provider Filter 在接口适配器执行前认证并建立 Spring Security 上下文，二者均在 `finally` 中恢复原状态。可信调用方身份由接口适配器从认证上下文取得，不从业务载荷复制；Token 不得进入日志、异常消息或业务对象。
- 远程调用明确超时、失败日志、幂等条件和失败策略。写操作只有具备稳定幂等键且确认重复执行安全时才自动重试。

## Spring、配置与 Bean

- 依赖使用构造器注入，不使用字段注入。只有多个正式构造入口且需要明确 Spring 入口时使用 `@Autowired`。
- `im.*` 下多个相关配置使用 typed `@ConfigurationProperties`；单个第三方桥接值可以使用 `@Value`。配置扫描优先使用 `@ConfigurationPropertiesScan`。
- 类型化配置的层级必须表达所有权：服务提供方地址位于集成根，当前应用身份拥有其 OAuth Client、Session 等子配置；不得把应用标识放入单个 Client，也不得把属于同一应用的多套 Client 平铺成无所有者的根级兄弟配置。固定子配置优先使用一个根 `@ConfigurationProperties` 的嵌套不可变类型，不为每个叶节点注册并列配置 Bean。
- 本地配置提供可运行的非生产默认值，部署配置可以由 Nacos 覆盖。密钥、凭证和环境私有值不得以生产值提交到仓库。
- 条件 Bean 的提供者和消费者必须形成闭合装配：消费者启用时，其直接依赖必须由同一条件、前置条件或明确的自定义 Bean 保证存在。新增条件装配需要覆盖启用、关闭、覆盖 Bean 和缺失必要配置的启动测试。
- `@Bean` 方法按能力边界分组；声明 Bean 的配置类不承载密钥派生、随机生成或业务决策等实现逻辑，应委托给有明确职责的组件。
- 可部署应用中的 Controller、RPC Provider、应用服务、领域服务、Repository、Adapter、生命周期和必需基础设施 Bean 无条件装配，使缺失配置或依赖在启动阶段立即失败；不得使用 `@ConditionalOnProperty` 关闭整段生产业务链路或为无外部基础设施的测试绕过装配。
- `@ConditionalOnProperty` 仅用于插件、SDK 和自动配置中的可插拔基础能力；`@ConditionalOnMissingBean` 用于允许业务应用覆盖基础组件默认实现。测试通过测试配置、依赖替身或聚焦上下文隔离外部设施，不为测试增加生产开关。
- Spring Boot Starter/SDK 中的 Controller、Advice 和其他接入 Bean 只通过其 AutoConfiguration 注册，不得因为包名落入业务应用的组件扫描而绕过启用条件。可部署 Server 不直接依赖自身的客户端接入 SDK；SDK 对可选安全上下文等集成依赖使用 Maven optional 和条件装配，由真正需要该能力的应用显式依赖，不能把整个客户端接入 SDK 传递到无关 Server。共享权限注解等少量接口由 Server 在自身安全边界声明，或在确有多个独立消费者时提取到不包含运行实现的契约模块。
- 时间、随机数、ID 生成器和执行器按业务需要抽象。不要只为了测试注入一个会显著降低生产代码可读性的依赖；优先在确定性边界、codec 或基础设施组件中控制它。
- 可水平扩容服务中的 Snowflake 等分布式 ID 生成器必须取得实例唯一且范围合法的节点标识，不在生产 Bean 中硬编码所有副本共享的固定机器标识。固定标识只用于明确的单实例开发环境或测试替身；生产来源、冲突处理和启动失败策略必须可配置并有聚焦测试。

## 时间与时区

- Token、会话、审计事件、消息和其他表示绝对时刻的业务事实，在应用层、领域层及 Java 服务间契约中使用 `Instant`。`LocalDateTime` 只用于确实没有时区含义的本地日历语义，不能作为绝对时刻在层间传播。
- 领域查询中共同表达一个闭区间的绝对起止时刻，使用 `im-common` 的 `TimeRange`，不在 `*QueryCondition` 中并列传播两个 `Instant`。HTTP Request 和应用层 CQRS Query 可以保留带业务语义的起止字段，由 Transformer 在进入领域条件时组装值对象；单独的创建时间、失效时间等不同业务事实不强行合并。
- 浏览器 HTTP 边界的绝对时间统一使用毫秒级 Unix 时间戳，Java 请求和响应模型使用 `Long`；`im-web` 将包装 `Long` 序列化为十进制字符串时，TypeScript wire model 使用 `string`，并在 UI 边界校验为安全整数后转换成 `number`。接口 Transformer 负责与内部 `Instant` 转换，不用缺少时区信息的 `yyyy-MM-dd HH:mm:ss` 字符串传递查询时刻。
- MySQL 中需要毫秒精度的绝对时间使用 `TIMESTAMP(3)`，MyBatis Entity 直接映射为 `Instant`。Repository 和 Transformer 不手写 `Instant`、`LocalDateTime`、固定 `ZoneOffset` 之间的中转转换。数据库连接或客户端的会话时区只影响显示，不改变业务时刻含义。
- 浏览器使用 IANA 时区（例如 `Asia/Shanghai`）将时间戳格式化为 `yyyy-MM-dd HH:mm:ss`。需要由服务端生成人可读时间的导出文件时，前端显式传递经过校验的 IANA 时区；服务端不根据部署机器时区、IP 或固定地域推断用户时区。
- 时间范围在接口边界转换为 `Instant` 后再进入应用和领域查询。测试使用固定时间戳和显式时区断言，不能依赖执行机器的默认时区。

## 类型、Lombok 与构造

- 只存储属性且不需要可变绑定的 DTO、参数、结果、事件和值载体优先使用 `record`。
- Facade、HTTP/RPC Params/Result、CQRS Command/Query/DTO、事件和跨层快照等输入输出边界，可能缺失的数值或布尔属性使用 `Integer`、`Long`、`Boolean` 等包装类型。必填属性仍须在构造、绑定或用例入口显式拒绝 `null`，不能依赖基本类型的 `0`、`false` 默认值掩盖缺失输入。
- Facade 批量查询只有集合结果且没有分页、游标、缺失项或其他元数据时，直接返回 `List<DTO>`；不创建只包含该集合的单字段包装 DTO。
- Params、DTO 等边界对象使用 `AssertUtils.arg*` 统一完成必填、范围和文本校验；领域对象使用对应的 `domainProp*` 方法。不要重复手写等价的参数异常判断。
- 局部计算、循环计数、集合大小以及语义明确且不表示“未提供”的内部判断可以使用基本类型；`is`、`has`、`can`、`matches` 等谓词方法通常返回 `boolean`。包装类型用于表达边界上的缺失风险，不代表业务允许 `null`。
- 需要 Spring 可变绑定、ORM、协议无参构造或 setter 的类型使用普通 JavaBean，并用 Lombok 生成简单 getter、setter 和纯字段构造器。
- `@RequiredArgsConstructor` 用于没有额外构造逻辑的组件；存在校验、派生字段、防御性复制或多个正式入口时保留显式构造器。
- 领域对象的必填属性不超过 4 个且构造顺序清晰时，优先使用直接构造器并在构造器内校验；达到 5 个及以上，或参数组合容易错位、需要分组表达时，使用手写 Builder，在 `build()` 中统一校验。该阈值用于保持一致性，不覆盖具有特殊业务语义的工厂方法或重建入口。
- `@Data` 只用于确实需要完整可变 Bean 语义的配置、请求、实体或兼容模型。领域值对象使用 record 或显式行为，避免无意生成 setter 和包含敏感字段的 `toString`。
- 不使用 `@SneakyThrows` 隐藏边界异常。受检异常在合适边界转换为项目异常、记录或继续声明。

## Transformer 与 JSON

- 声明式字段映射使用 MapStruct 生成代码；不要保留一个全部由 handwritten default 方法组成的 `@Mapper` 接口。
- 不依赖 Spring Bean、Decorator 或其他运行时协作者的无状态 MapStruct Transformer，优先使用普通 `@Mapper` 和 `Mappers.getMapper(...)` 声明的 `INSTANCE`，不要仅为获取转换器而增加 Spring 注入及配置 Bean 参数。确实需要注入协作者时才使用 Spring component model，并由容器统一获取，不同时暴露 `INSTANCE`。
- MapStruct 可以直接转换同名枚举和普通属性。安全校验、身份覆盖、条件分支和异常转换使用显式方法。
- 跨层属性语义相同但命名不同时使用显式 `@Mapping`，并以转换测试验证关键字段不会静默映射为 `null`。
- Transformer 按主要转换目标归属：边界或 CQRS 模型转换为领域对象时使用 `transformer.domain` 下的 `*DomainTransformer`，领域对象转换为应用层 DTO 时使用 `transformer.application` 下的 `*AppTransformer`。不要在 `*AppTransformer` 中混入返回领域对象的反向映射；涉及身份推断、权限覆盖等业务判断时由应用或领域服务显式处理，不伪装成字段转换。
- 领域层、应用层和内部 CQRS 模型可以共享同一业务枚举；HTTP、RPC 等对外契约若需要枚举，定义边界专用枚举，由对应 Transformer 完成领域枚举与契约枚举之间的转换，禁止接口层直接暴露领域枚举。
- HTTP 查询参数较多时使用 `*Request` 聚合并通过 `@ModelAttribute` 绑定；接口 Transformer 负责从 Request 构造应用层 Command/Query，包括边界枚举转换和分页值组装，Controller 不手写长参数构造器或逐字段跨层转换。
- HTTP `@RequestBody` 输入使用接口层独立的 `*Request` 类型，不直接暴露 SDK Event、领域对象或应用层 CQRS 模型；普通 HTTP 输出使用 `*Response`，协议型接口按其协议语义处理。Request 与 SDK/CQRS/领域对象之间由接口 Transformer 转换。
- HTTP 请求响应由 Spring/Jackson 绑定；Bolt、Gossip、缓存载荷和数据库 JSON 字符串等显式 JSON 转换统一使用 `JsonUtils`，生产代码不自行创建 `ObjectMapper`。确需使用 MyBatis-Plus `JacksonTypeHandler` 时，必须由 `im-datasource` 绑定应用统一 `ObjectMapper`，不能使用缺少 Java Time 等模块的内部默认实例。
- 数据库 JSON 列默认由 Entity 使用 `String` 承接原始 JSON，并由持久化到领域的 Transformer 使用 `JsonUtils` 转换为领域值对象；写回时输出确定性 JSON。只有 Entity 明确把 `Map`、集合等结构作为持久化原生属性使用时才配置 TypeHandler，并通过聚焦测试固定该例外，不能让 TypeHandler 直接产生领域值对象。
- `JsonUtils.getMapper()` 是进程共享实例，只用于第三方组件接入，调用方不得重新配置。

## 持久化与缓存

- Repository 接口表达聚合查询、保存和删除语义，不重复声明本应属于聚合的 `consume*`、`rotate*` 等业务动作，也不暴露锁、compare-and-set 实现、Redis key、Mapper 或缓存 builder。并发冲突通过统一 `save` 的版本校验表达，不为每个业务动作增加条件更新方法。
- 完整聚合只由该聚合所属的 Repository 重建和返回；关联 Repository 只表达关系写入、标识集合和统计等关系事实，不跨聚合所有权返回其他聚合。需要按关联条件查询聚合时，在聚合所属 Repository 上增加领域查询入口。
- 跨上下文查询得到的外部投影由应用服务协调，领域服务可以将本上下文聚合与外部投影组合成不可持久化的派生领域资料；不得为了展示或查询便利把外部投影字段临时写回本上下文聚合。
- Repository 查询参数使用调用方已经确定的具体领域值对象。用户名、邮箱、手机号等具有不同语义和校验规则的标识分别提供明确查询入口；不要为了复用一个查询方法创建 `*Identifier`、`*Key` 等宽泛联合值对象，也不要退化为裸 `String`。只有接口输入仍允许多种标识时，才在应用边界完成识别后调用对应 Repository 方法。
- `*QueryCondition` 只承载过滤条件，分页通过独立的 `Paging` 参数传递。可省略的内部过滤条件使用非空 `Optional<T>`，必填条件使用直接类型；Nullable 来源值在 Transformer 或 Repository Adapter 等边界归一化为 `Optional`，`Optional` 容器本身不得为 `null`。
- 上述 `Optional` 约定只适用于内部过滤条件，不扩展到公开请求响应、持久化实体、领域聚合或依赖 Nullable 属性完成序列化和框架绑定的类型。
- MyBatis Mapper、Redis/JetCache/Redisson 和数据库实体只属于 infrastructure。
- MyBatis 持久化按 `entity`、`enums`、`mapper`、`query`、`service` 分工：Mapper 只声明框架映射或确有必要的自定义 SQL；MyBatis Service 提供表级 CRUD、Wrapper 和可复用查询能力，Repository 技术实现依赖 Service 而不直接编排 Mapper。只服务于某个领域 Repository 的查询语义可以由 Repository 使用 Service 组装 Lambda Wrapper，不为跨一层传参额外创建持久化 `*QueryCondition`；跨 Repository 复用的表查询才下沉到 Service。只有跨表、特殊锁语义或 MyBatis-Plus 无法清晰表达的 SQL 才进入 Mapper/XML，并说明原因。
- 业务 Mapper 已逐个标注 MyBatis `@Mapper` 且位于应用扫描根包时，依赖 MyBatis Boot 默认扫描即可，不再声明只有 `@MapperScan` 的空配置类。只有 Mapper 未逐个标注、扫描范围跨越应用根包或需要多个数据源分别绑定 Mapper 时，才使用显式 `@MapperScan`。`im-datasource` 提供公共 MyBatis/MyBatis-Plus 能力，不扫描任何业务模块的 Mapper。
- MyBatis Entity 统一继承 `BaseEntity`，复用数据库自增主键、创建时间、更新时间和逻辑删除字段，不在子类中重复声明这些属性。关联表、只追加事实表和协议状态表也保留完整模板字段，即使当前用例不执行更新或逻辑删除也不例外；DDL 与 Entity 必须同步保持该形状。
- 数据库自增 `id` 是技术主键；领域中存在稳定业务身份时，Entity 同时声明独立的业务 ID 列，并由 Transformer 在领域业务 ID 与持久化字段之间转换。数据库主键只通过 `Identification.pkId` 恢复和回填，不替代领域业务 ID；使用 `@EqualsAndHashCode(callSuper = false)` 的聚合不将该技术主键纳入相等性。
- 数据库列具有明确有限集合时，Entity 使用 infrastructure 自己的 `Db*` 枚举，不以 `String` 承载状态、动作、结果、种类等闭集值，也不直接依赖领域枚举。枚举映射到 `TINYINT` 等数值列时必须声明稳定数值和 MyBatis-Plus `@EnumValue`，不能依赖默认枚举名称或 ordinal。领域枚举与数据库枚举由 Transformer 转换；协议扩展码、第三方开放值和自由文本仍可使用 `String`。
- MyBatis Entity 中的密码摘要、Client Secret、Token/Cookie 摘要等认证材料不得进入 `toString()`；使用 Lombok 时通过 `@ToString.Exclude` 明确排除。Entity 名称与表所表达的事实保持一致，管理查询投影不得因为展示名再创建一套与事实主键重复的领域身份。
- 锁和事务负责保护应用用例；聚合负责校验业务前置条件和执行状态变化。不要把技术锁操作伪装成 Repository 领域方法。
- 缓存明确 key、TTL、失效策略和不可用行为。除非设计明确，缓存不是事实数据的唯一来源。
- SQL 遵循独立的 [SQL Guide](SQL_GUIDE.md)。

## 指标、事件与异常

- 指标使用稳定的小写点分隔名称和有限标签，不使用 userId、sessionVersion、connectionId、Token 等高基数或敏感值。
- Gauge 表示当前状态，Counter 表示累计事件，Timer 表示耗时。重复的计数和耗时样板优先通过 `im-metrics` 的注解能力隔离。
- 指标、日志增强和 Gossip 通知等旁路能力失败时记录异常并隔离，不改变核心业务结果；安全、持久化和业务校验异常不得被静默吞掉。
- 领域事件携带不可变领域值，不传播 HTTP request、RPC params、持久化对象或 Spring 事件类型。
- 领域内同步事件可以参与当前事务；集成事件在事务提交后发布，失败进入明确的重试、补偿或可靠消息策略。
- 来源应用、认证主体、租户等信任事实由接口适配层从已认证上下文或固定传输通道注入，不在不可信请求体或消息载荷中重复声明。应用层 Command/Event 可将可信事实与载荷属性扁平化，但必须保持单一、无歧义的来源字段。
- UTF-8 文本的确定性 SHA-256 等无业务语义哈希原语复用 `im-common` 的 `HashUtils`，业务适配器只保留摘要编码、协议值对象与校验语义。管理员密码、OAuth Client Secret 和 OAuth Token 是不同凭据边界；即使底层算法相同，也不得借用另一领域的密码服务或值对象完成框架认证。

## Web 与 API

- Controller 获取认证上下文、完成 HTTP 输入输出和边界校验，然后调用应用服务。
- `im-web` 只承载通用 MVC、`HttpResult`、异常映射、日志、CORS 和客观请求元数据，不依赖 Session、IAM 或业务模块。Session Header 到用户上下文的适配由 `im-session` 提供，公开业务路径由使用方配置或安全策略拥有，不硬编码在插件中。
- 可部署 HTTP 应用统一通过 `HttpResult` 表达普通成功和失败结果。Spring Security 的未认证、无权限响应也使用相同协议；OAuth 回调、重定向、下载和流式响应等协议型接口可以明确绕过包装。
- IAM BFF 应用显式区分公共前端请求和受保护请求：页面入口、`index.html`、favicon、`assets`、错误页、登录及回调不恢复 Session 或执行 Introspection；`/iam/**` 的其他端点和 `/api/**` 必须认证；未声明路径默认拒绝。Actuator 等运维端点使用独立安全策略，不并入前端白名单。
- 已知前端静态资源不存在时不作为未知系统异常记录 ERROR；统一异常处理按项目 `HttpResult` 协议返回 NOT_FOUND 业务语义，前端需要的固定资源应由 UI 构建产物提供。不得仅按 Spring 异常类型把所有未映射 API 都识别为静态资源。
- 常见静态资源后缀和 `/assets/**` 等前端静态资源不生成完整 HTTP 访问日志；`/` 等无后缀路径必须依据 MVC 实际解析的静态资源 Handler 判断，不能在公共插件中把根路径固定视为页面。Controller、认证入口和业务 API 继续保留访问日志。
- 通用请求上下文只传播客户端地址、User-Agent 等非安全元数据，由 Servlet Filter 在安全过滤链之前建立并在请求结束时清理。管理员身份与权限以 Spring Security 为准；审计模块在自己的边界内把通用元数据转换为领域值对象。
- CORS 使用类型化配置和显式允许列表，默认不开放跨域。业务应用不得通过重复声明通用 Web Bean 或第二套全局错误响应绕过插件行为。
- HTTP 权限校验使用 Spring Security 的认证对象与 `@PreAuthorize` 等标准能力；允许安全 SDK 提供仅组合标准注解、没有自定义授权逻辑的薄注解，不自建授权 AOP 或 MVC 权限拦截器。UI 权限只控制展示，服务端方法权限才是授权边界。
- 管理端业务权限使用枚举统一承载编码、展示名称和说明，并通过嵌套 `Code` 类提供注解可用的编译期常量。接入应用上报的权限目录与 `@RequiresPermission` 必须引用同一枚举；IAM Server 不反向依赖接入 SDK，使用标准 `@PreAuthorize` 引用同一 `Code` 常量。Controller 不重复书写权限编码或 Spring EL。模块契约测试校验权限元数据和接口授权引用，SDK 行为测试校验允许与拒绝语义，避免权限目录与实际鉴权漂移。
- IAM 内部管理权限与外部应用权限是不同的领域语义。IAM 内部权限只用于保护 IAM 自身管理接口；Admin、Monitor 等应用权限由应用自身声明并通过权限目录同步到 IAM。两者不得因为底层表结构可复用而共用领域对象、应用服务或目录同步入口；类型及其直接 Repository、领域服务使用 `Iam*`、`Application*` 等所有权前缀显式表达范围，不保留会掩盖归属的通用 `Permission`、`Role` 名称。需要共用持久化表时，也必须在领域与接口层保持明确边界。
- 同名业务概念面向不同授权对象、生命周期或可信来源时，应建立独立的领域模型和 Repository。若两类事实具有独立创建、分配和失效生命周期，持久化也使用独立 Entity 与表；不能仅通过状态、类型枚举或固定 `appId` 在同一聚合和关系表中区分。
- Spring Security 已提供符合协议的默认 Provider 时优先直接使用；业务扩展优先通过 Token Claims、资源服务器校验和框架公开配置完成。只有默认扩展点无法表达已确认的安全要求时才替换 Provider，并记录被替代的标准行为和对应测试。
- 同一应用同时提供浏览器 Session 接口和 OAuth Client Bearer 接口时，优先按稳定 URL 边界拆分有序 `SecurityFilterChain`。Client API 使用无状态 Session 策略并关闭不适用的 CSRF，Web 链只装配表单登录与浏览器 Session；不要在兜底链混合两套认证入口。
- 请求和响应模型放在接口模型包，不直接返回数据库实体或领域聚合。
- 统一异常处理器负责把业务异常转换为错误码和响应；Controller 不重复拼装失败结果，也不用 `valid=false` 表达本应抛出的认证失败。
- Swagger 注解描述公开 API 意图和约束，不替代参数校验。
- 管理应用不得各自实现管理员密码认证、RBAC 或安全 Session；统一通过 `im-iam-sdk` 接入 IAM，并在应用内保留最终 `@RequiresPermission` 权限判断。
- 浏览器不得把 Snowflake、数据库或其他 Java `Long` 业务标识转换成 JavaScript `number`；前端 wire model 使用十进制 `string` 并原样传回服务端。分页总数、epoch millis 等确实参与数值计算的值必须经过安全整数校验后转换。
- 由 Spring Boot JAR 直接托管且没有显式 SPA fallback 的前端使用 Hash Router，避免刷新深层路由时落入服务端 404。选择 History Router 时必须同时提供排除 API、认证和静态资源路径的 fallback 行为测试。
- 可部署 HTTP UI 必须在 Axios 等唯一 HTTP 边界统一拆解 `HttpResult`、映射认证/权限错误并提交同源 CSRF Token；页面组件不重复解释 envelope。前端 API 契约测试至少覆盖 URL、查询参数、写请求 Body、Long wire type 和时间格式，不能只 Mock 页面所期望的返回对象。
- 管理 UI 的列表必须显式区分首次加载、已有数据、空结果和失败状态；刷新或翻页失败时保留上一份成功数据与原页码，写操作只禁用当前行或当前编辑器。较长编辑流程使用可恢复上下文的抽屉，失败保留输入；停用、删除、撤销以及会立即使旧凭据失效的密钥轮换必须展示对象名称和后果并要求显式确认。状态不能只依赖颜色表达，抽屉、确认框、菜单和 Toast 必须具备键盘与辅助技术可识别语义。
- 独立 Vue/TypeScript UI 必须提供 `lint`、`format:check`、`test:unit`、`typecheck` 和 `build` 脚本；ESLint 与 Prettier 配置归各 UI 所有，不为了减少少量配置复制而共享 Router、状态或业务前端源码。
- IAM BFF 登录的页面恢复地址随一次性 OAuth state 保存，只接受当前应用内以 `/` 开头且不以 `//` 开头的相对路径；回调消费 state 后恢复该路径，禁止直接信任绝对 URL 或协议相对 URL。
- IAM Server 自身管理登录使用 IAM UI 提供页面，认证提交仍交给 Spring Security Form Login；显式后台 `continue` 只接受 `/` 或 `/#/` 路径，没有 `continue` 时必须恢复 Spring 保存的 OAuth Authorization Request，不能让后台 SPA 跳转覆盖 OAuth 登录流程。

## 命名

- CQRS 模型按语义放入 `command`、`query` 或 `event` 包：`Command` 表示执行意图，`Query` 表示读取意图，`Event` 表示已经发生并被应用接收或发布的事实。`Params`/`Result` 用于跨边界输入输出，`DTO` 用于传输结果；不能仅因对象进入应用服务就统一命名为 `*Cmd`。
- 直接位于 `model/cqrs/dto` 的应用层传输结果统一使用 `*DTO` 后缀，不在同一目录混用 `*Data`、`*View`、裸 `*Overview` 或裸 `*Result`。具有独立业务语义、并非普通数据传输结果的 Result 可以放在对应能力包中，不为满足后缀规则伪装成 DTO。
- `Registry` 表示注册状态读写，`Store` 表示技术状态存储，`Repository` 表示聚合持久化抽象，`Client` 表示外部调用端。
- `Service` 必须表达业务决策或稳定能力，避免给纯委托类套用 Service 后缀。
- 布尔方法使用 `is`、`has`、`can`、`matches` 或 `should`；查询使用 `find`、`list`、`get`，修改使用具体动作词。
- 名称从调用者需要理解的语义出发。实现细节留在内部，不把 `afterCommit`、锁策略或存储方式拼入不需要知道这些细节的公开方法名。
- 类名和属性名表达领域事实，不表达截断、脱敏等内部处理方式。`Summary` 只用于确实表示原内容摘要的模型；仅对完整业务值执行容量限制时，使用 `Description`、`UserAgent` 等实际业务名称。
- 不通过重载、兼容构造器或 wrapper 保留没有调用方的旧 API；确有迁移需要时记录调用方和退出条件。

## 自动检查与 Review

自动规则只覆盖低误报模式，具体矩阵见 [Harness Guide](HARNESS_GUIDE.md)。当前脚本检查显式类型、字段注入、生产测试替身、控制台输出、手动 `ObjectMapper`、成组简单 getter/setter、AppService 标量入参和明显职责混包；ArchUnit 检查稳定依赖方向。

领域服务尺度、值对象建模、Adapter 输入输出、边界包装类型语义、构造器业务逻辑、条件 Bean 是否形成完整能力、方法可读性和注释价值由 Review 判断。不要为了自动化增加“每包最多 N 个类”、禁止所有 getter、禁止所有构造器或方法长度等高误报规则。

确需例外时在代码附近说明原因，并在 Review、Harness 反馈或技术债务中记录退出条件；不得通过关闭规则或扩大排除范围消除反馈。
