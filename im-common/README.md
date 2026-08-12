# im-common

`im-common` 保存所有模块都可复用、且不属于某个运行服务的基础类型。

## 主要内容

- `constant`、`model.io`：HTTP/实时帧协议常量与基础请求响应。
- `domain.shared`：通用领域事件、标识和值校验约定。
- `domain.user`、`domain.group`：跨限界上下文共享的少量稳定值对象。
- `exception`：统一异常层次。
- `identity`：Snowflake ID 支持。
- `serializer`、`utils`、`state`：序列化、反射、集合和状态机工具。

## 边界

本模块不依赖业务服务、网关、Broker 或插件实现。新增内容应具有明确的跨模块复用价值，业务模型优先放回所属服务。

## 关键技术点

- `JsonUtils` 统一 Jackson 和 Java Time 配置，跨模块传输避免各自创建 ObjectMapper。
- `ReflectUtils` 支持解析继承层次中的泛型参数，供通用 Handler 推断入参类型。
- `NestedMapUtils` 以原子删除和空 Map 裁剪辅助并发索引维护。
- 公共领域对象必须保持稳定和值语义，禁止引用基础设施类型。

## 验证

```bash
mvn -q -pl im-common -am test
```
