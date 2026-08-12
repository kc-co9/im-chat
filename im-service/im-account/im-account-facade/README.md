# im-account-facade

账号服务跨模块契约，不包含运行实现。

- `AccountService`：Token 校验、用户资料和用户状态查询。
- `AccountSessionService`：进入/退出聊天及在线、聊天状态查询。
- `params`、`dto`：Dubbo 调用参数和返回模型。

Message、Social 等调用方只依赖本 Facade。对象应保持可序列化、稳定且不泄露账号服务内部实体。

## 关键技术点

- Facade 是跨进程兼容边界，字段变更优先采用向后兼容的新增方式。
- Params 表达调用命令，DTO 表达结果，不复用 HTTP Request 或领域实体。
- 接口版本由 Dubbo Provider/Consumer 共同约束，发布前需要验证序列化兼容性。

```bash
mvn -q -pl im-service/im-account/im-account-facade -am test
```
