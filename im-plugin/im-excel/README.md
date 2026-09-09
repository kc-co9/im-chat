# im-excel

基于 Apache Fesod 的通用 Excel 基础插件。当前只提供经过真实业务验证的流式导出能力，不预先定义无调用方的
导入契约。

## 主要内容

- `ExcelTemplate`：创建单工作表流式写出会话；
- `ExcelWriteSession<T>`：以批次追加行数据并可靠完成工作簿；
- `EnumNameConverter`：将枚举稳定写为枚举名称；
- `ImExcelAutoConfiguration`：提供可由应用覆盖的默认 `ExcelTemplate`。

## 使用方式

业务模块保留自己的行模型、列标题和导出规则：

```java
try (ExcelWriteSession<UserExportRow> session = excelTemplate.open(
        outputStream, UserExportRow.class, "用户列表")) {
    session.write(rows);
}
```

`ExcelTemplate` 支持分批调用 `write`。即使没有数据，关闭 Session 仍会生成包含表头的合法工作簿。模板不会关闭
调用方提供的 `OutputStream`，HTTP 响应流等外部资源仍由调用方管理。

## 边界

- 插件拥有 Fesod Writer 生命周期、分批写入和通用转换器；
- 业务模块拥有查询、权限、数量限制、行模型、列标题和导出审计；
- 不在插件中定义特定业务的导入或导出 DTO；
- 出现首个真实导入场景后，再根据实际校验和错误行需求设计读取 API。

```bash
mvn -q -pl im-plugin/im-excel -am test
```
