# SQL Guide

本规范覆盖各 Server 模块自有 DDL、MyBatis Mapper XML 和 Java MyBatis 注解 SQL。目标是保持数据库结构与持久化代码一致，并用低误报门禁尽早发现确定的危险写法。SQL 语义、执行计划和数据规模仍需通过测试与 Review 判断。

## 覆盖范围与位置

- 服务私有 SQL 脚本放在拥有数据的可部署 Server 模块根目录 `sql/`，只使用该目录的直接子文件；其中包含 `CREATE TABLE` 的文件按 DDL 规则检查。仓库根目录不集中保存服务 DDL。
- 模块 `src/main/resources` 不保存 `.sql` 格式的 DDL 副本；DDL 不随 JAR 自动执行，Mapper XML 仍放在所属服务的 `src/main/resources/mapper`。
- Account、Social、Message、IAM 和 Audit 分别拥有独立 MySQL Schema；服务间只通过业务 ID 和 Facade/SDK 交互，不增加跨 Schema JOIN 或外键。
- MyBatis 注解 SQL 只属于 infrastructure mapper，不进入 domain、facade 或 SDK。
- DDL、Mapper、持久化实体和 schema 测试发生关联变化时同步修改。
- 尚未上线且不存在历史数据兼容需求的结构变更直接更新当前 DDL，不创建假设性的迁移脚本。只有明确存在需要保留的历史数据、已部署 schema 或发布切换需求时才新增迁移，并同时提供验证与回滚方案。

## DDL

- 表、字段和索引名称使用小写 snake_case。
- 普通索引使用 `idx_` 前缀，唯一索引使用 `uk_` 前缀。
- 每张表统一声明 `id`、`create_time`、`update_time`、`is_deleted` 四个标准模板字段，即使关联表、只追加事实表或协议状态表当前不使用更新与逻辑删除能力也不得省略。
- `id` 使用数据库自增主键；`create_time`、`update_time` 使用 `TIMESTAMP(3)`；`is_deleted` 使用 `0` 表示未删除、删除行主键表示已删除，并参与需要支持逻辑删除后重建的唯一索引。
- 每张表声明显式主键、`ENGINE = InnoDB` 和表注释。
- 禁止 `TRUNCATE` 和 `DROP DATABASE`。
- 仅各 Server 模块自有的 `sql/ddl.sql` 可以使用 `DROP TABLE IF EXISTS`，用于当前项目的本地全量重建；其他 SQL 文件和 Mapper 不得删除表。
- 修改字段或索引时检查逻辑删除、唯一键、默认值、空值语义和历史数据兼容性。

## Mapper SQL

- Mapper XML 和 Java 注解 SQL 使用 `#{...}` 绑定值，禁止 `${...}` 字符串替换。
- 静态 `UPDATE` 和 `DELETE` 必须包含明确的 `WHERE` 条件。
- 动态 SQL 必须保证条件为空时不会退化为全表更新或删除，并用测试覆盖空条件。
- 查询必须显式列出返回字段，禁止 `SELECT *` 和 `SELECT table.*`；聚合表达式 `COUNT(*)` 不属于字段通配投影，允许使用。
- 批量写入、分页、锁定读取和复杂查询在调用边界明确数据规模、事务和失败行为。

## Review 检查

以下内容依赖业务语义，不使用正则自动拦截：

- 索引是否覆盖真实查询以及联合索引字段顺序；
- 分页方式和深分页成本；
- 批量操作的数据规模、分批策略和事务边界；
- 悲观锁、乐观锁和锁定顺序；
- 逻辑删除与唯一键的组合语义；
- 敏感字段的存储、查询和日志暴露；
- 动态 SQL 的所有条件组合；
- 是否需要通过 `EXPLAIN` 或真实数据库测试验证执行计划。
- 当前变更是否确有历史数据兼容需求；未上线功能不保留无消费方的迁移脚本。

## 自动检查边界

| SQL 来源 | 自动检查 | Review 补充 |
|---|---|---|
| Server 模块根目录 `sql/*.sql` | 标准模板字段、DDL 结构、命名、危险语句和 `DROP TABLE` 位置 | 数据所有权、迁移兼容性、索引和历史数据 |
| Mapper XML 文本/CDATA | `${...}`、字段通配投影、完整静态无条件写入和禁用 DDL | 动态标签组合和执行计划 |
| MyBatis 注解单字符串/text block | 位置、`${...}`、字段通配投影、完整静态无条件写入和禁用 DDL | 常量、数组、拼接和动态语义 |

`SELECT *` 和 `SELECT table.*` 在 Mapper XML 与受支持的注解 SQL 中都会失败；`COUNT(*)` 是聚合表达式，允许使用。

`scripts/check-sql.sh` 递归发现各模块根目录的 `sql/*.sql`，并只检查能够稳定识别的规则：标准模板字段、DDL 位置与结构、危险 DDL、命名、查询字段通配符、MyBatis 注解位置、SQL 表达式中的 `${...}`，以及完整静态语句中的无条件 `UPDATE`、`DELETE`。Java 注释和非 SQL 字符串不属于 `${...}` 扫描输入。

第一版能够检查 Mapper XML 普通文本或 CDATA 中的完整语句，以及 Java 注解的单个字符串字面量和 text block。Java 注解字符串数组、常量引用、字符串拼接和 MyBatis 动态 XML 的无条件写入风险交给测试与 Review；源码中能直接发现的 `${...}` 仍然禁止。

确需例外时必须限定路径、说明真实场景和退出条件。不要通过扩大排除范围或弱化规则消除反馈。
