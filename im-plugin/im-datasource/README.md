# im-datasource

数据访问公共插件，提供 MyBatis/MyBatis-Plus 基础类型、事务提交后回调，以及 ShardingSphere 配置模型。

## 主要内容

- `BaseEntity`、`BaseMybatisService`：持久化基础类型。
- `AfterTransactionCommit` 及其切面/模板：事务成功提交后执行动作。
- `ImShardingSphereProperties`：`im.sharding-sphere` 配置绑定。

具体数据源地址、表结构、Mapper 和分片规则由业务服务提供，插件不内置业务数据库配置。

## 关键技术点

- `AfterTransactionCommit` 通过 AOP 与 Spring 事务同步机制保证动作只在事务成功后执行。
- MyBatis 基础类型只统一通用持久化行为，不向领域层暴露数据库实体。
- ShardingSphere 只提供配置绑定，实际分片规则必须由拥有数据的服务维护。

```bash
mvn -q -pl im-plugin/im-datasource -am test
```
