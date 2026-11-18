# im-architecture-test

`im-architecture-test` 是仓库级架构反馈模块，通过 ArchUnit 检查编译后的项目类，防止模块依赖和分层边界在持续修改中发生漂移。它由 `im-test` 聚合，不包含生产代码。

## 关键技术点

- 使用 ArchUnit 执行确定性的包依赖检查；
- 集中依赖主要运行模块，避免在每个业务模块中复制架构规则；
- 检查 `im-common`、插件、SDK、Facade、业务服务和领域层的依赖方向；
- `im-ops-cli` 通过 test scope 纳入字节码检查，限制为自身、JDK 与 Jackson；POM 非 test 依赖仅允许批准的 JSON 库，不将客户端加入部署服务的 Nacos 依赖清单；
- 通过 `scripts/verify.sh` 纳入本地开发和 CI 的统一验证流程。

## 运行

```bash
./scripts/verify.sh architecture
```

或者直接运行：

```bash
mvn -q -pl im-test/im-architecture-test -am test
```

## 规则维护

架构规则必须表达稳定且可以机械判断的边界。新增规则前先确认当前代码满足该约束，并提供能够证明规则有效的测试场景。

如果规则失败，应优先修复依赖方向。确有合理例外时，应将例外缩小到明确的类或包并记录原因，不能整体关闭规则，也不能使用宽泛白名单掩盖新的违规。

B3 存在性增量进一步要求：默认生产根有 Java 源码的正式模块必须有非空 class 清单，资源目录不算产物；不存在源码根或仅有无注解文档型 package-info 可为空，带注解包文件不可豁免。源根及条目类型／链接失败关闭，源路径集合由 check 重新枚举。这收紧下述身份首片的缺失记空边界，但不证明清除后同轮鲜度。

覆盖插桩的源码所有权由 `scripts/check-java-style.sh` 检查普通生产根及默认 annotation processor 生成根，两者使用相同 package／路径词法规则。既有 reactor evidence 另按 canonical raw POM oracle 枚举正式默认 class 输出，验证 constant pool／this_class 内部名、路径、唯一性与完整文件摘要，`check` 重枚举后拒绝清单或内容变化；隔离 fixture 验证这些分支，不运行 JVM 或 Maven。普通 class 输出缺失目前记空；MapStruct 期待集另从默认生产源码派生，要求当前同包默认 Impl 源码／class 存在、声明对应并绑定摘要。有限语法拒绝歧义与输出定制，不是任意 Java parser；当前配对不代表本轮生成／鲜度或 class／report 完整性。同轮清除与实际 agent／report 测试仍属 T11 B3 后续义务，当前尚未验收。
