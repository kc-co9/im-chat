# im-datasource

数据访问公共插件，提供 MyBatis/MyBatis-Plus 基础类型、事务提交后回调，以及 ShardingSphere 配置模型。

## 主要内容

- `BaseEntity`、`BaseMybatisService`：持久化基础类型；所有 MyBatis Entity 通过 `BaseEntity` 统一复用数据库自增技术主键、`Instant` 创建/更新时间、逻辑删除和乐观锁字段。
- `AfterTransactionCommit` 及其切面/模板：事务成功提交后执行动作。
- `ImShardingSphereProperties`：`im.datasource.sharding` 配置绑定和 ShardingSphere DataSource 创建。

具体数据源地址、表结构、Mapper 和分片规则由业务服务提供，插件不内置业务表配置。`id` 继续由数据库自增生成，业务全局 ID 由各表自己的业务字段承载。加载 `config-location` 后，插件先通过 Spring Environment 解析 YAML 中的 `${...}`，再创建 ShardingSphere DataSource；因此 Nacos、环境变量或 JVM property 可以覆盖连接配置。

## 关键技术点

- `AfterTransactionCommit` 通过 AOP 与 Spring 事务同步机制保证动作只在事务成功后执行。
- MyBatis 基础类型只统一通用持久化行为，不向领域层暴露数据库实体。
- MyBatis-Plus JSON TypeHandler 复用应用统一 `ObjectMapper`，支持 `Instant` 等 Java Time 类型。
- ShardingSphere DataSource 由插件统一创建，实际分片规则必须由拥有数据的服务维护。

默认配置示例：

```yaml
im:
  datasource:
    sharding:
      enabled: true
      config-location: classpath:im-sharding.yml
      jdbc-url: jdbc:mysql://db.example/im_chat
      username: im_chat
      password: ${IM_CHAT_DB_PASSWORD}
```

`jdbc-url`、`username`、`password` 和 `sql-show` 可由当前应用的 Nacos dataId 覆盖。五份业务规则通过 `${im.datasource.sharding.sql-show:true}` 默认开启 SQL 日志便于本地排查，生产应设置 `im.datasource.sharding.sql-show=false`。应用不得再配置 MyBatis `StdOutImpl` 绕过该开关。公共插件只解析 `im.datasource.sharding.*`，不会消费 ShardingSphere 的 `${0..7}` 或 `${user_id % 8}` inline 表达式。生产也可以把 `config-location` 改为 `file:/etc/im-chat/im-sharding.yml`，由部署系统提供完整规则和密钥；不得沿用仓库内的本地 `root/root` 默认值。

```bash
mvn -q -pl im-plugin/im-datasource -am test
```
