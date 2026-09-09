# Excel 插件边界设计

实施记录见 [Excel Plugin Implementation Plan](../exec-plans/completed/2026-08-28-excel-plugin.md)。

## 1. 背景

`im-audit-server` 当前直接依赖 Apache Fesod，并在 Audit 业务模块内维护 Writer 生命周期、分批写入、空工作簿和
枚举名称转换。上述机制不包含审计业务语义，后续用户、管理员等管理功能也可能需要 Excel 导入或导出，因此应由
可复用插件统一拥有。

## 2. 目标与非目标

### 2.1 目标

- 新增 `im-plugin/im-excel`，统一管理 Fesod 依赖和通用 Excel 写出机制；
- 提供泛型、流式、可分批写入的 `ExcelTemplate` 与 `ExcelWriteSession<T>`；
- 保证空数据也能生成带表头的合法工作簿，并且不擅自关闭调用方提供的输出流；
- 下沉枚举名称等与业务无关的通用转换器；
- 让 Audit 只保留查询、权限、导出限制、导出审计和行模型。

### 2.2 非目标

- 第一期不实现尚无调用方的 Excel 导入 API；
- 不把 `AuditExportQuery`、`AuditExportRow`、`AuditExportResult` 或导出审计迁入插件；
- 不在插件中定义 Audit 工作表名称、列标题、权限或分页策略；
- 不为隐藏 Fesod 注解而新增一套重复的 Excel 注解模型；
- 不保留只做委托的 `AuditExcelExporter` 或 `AuditExcelSession` 兼容层。

## 3. 模块边界

```text
im-plugin/im-excel
├── ImExcelAutoConfiguration
├── core
│   ├── ExcelTemplate
│   └── ExcelWriteSession
└── convert
    └── EnumNameConverter

im-management/im-audit/im-audit-server
├── application/AuditExportAppService
├── model/cqrs/dto/AuditExportRow
└── support/export/AuditExportAuditor
```

依赖方向为：

```text
im-audit-server -> im-excel -> Apache Fesod
im-excel -X-> im-management / im-service / im-gateway / im-broker
```

业务行模型可以使用 Fesod 的声明式列注解。`im-excel` 明确包装该技术，因此允许其公共使用约定包含 Fesod 注解；
Writer、Sheet 和 Converter 等执行期对象不得泄漏到业务应用服务。

## 4. 公共 API

`ExcelTemplate` 提供单一写出入口：

```java
public <T> ExcelWriteSession<T> open(
        OutputStream outputStream,
        Class<T> rowType,
        String sheetName);
```

`ExcelWriteSession<T>` 支持多批次写入并通过 `AutoCloseable` 完成工作簿：

```java
public interface ExcelWriteSession<T> extends AutoCloseable {
    void write(List<T> rows);
    void close();
}
```

模板校验输出流、行类型和工作表名称。Session 复制或只读使用调用方数据，不持有 Audit 语义；关闭时即使没有调用
`write`，也必须写出空数据以生成表头。底层设置 `autoCloseStream(false)`，输出流生命周期由 HTTP 层拥有。

## 5. Audit 迁移

`AuditExportAppService` 直接依赖 `ExcelTemplate`，使用 `AuditExportRow.class` 和 `审计记录` 创建 Session，并保持
现有分页读取与逐页写出流程。删除 Audit 内部的 `AuditExcelExporter`、`AuditExcelSession` 和
`FesodAuditExcelExporter`。

`AuditExportRow` 继续属于 Audit 应用传输模型。通用枚举转换器迁至 `im-excel/convert`，行模型只引用插件提供的
转换器，不再定义 Audit 专属 Fesod 转换逻辑。

## 6. 导入演进

当出现首个真实导入用例时，再基于实际错误处理和校验需求增加 `ExcelReadSession` 或读取回调。第一期不预设导入
返回结构、错误行模型或监听器，避免形成无调用方的公共契约。未来导入仍由 `im-excel` 负责文件解析，业务模块负责
字段业务校验、Command 构建和持久化。

## 7. Harness 与验证

本次未形成新的全局编码规则。现有插件所有权规则已经禁止插件依赖业务模块，并要求公共基础能力保持通用；因此只需
补充模块测试和架构验证，不新增低可靠性的类名扫描。

测试必须覆盖多批次写入、空数据表头、枚举名称、调用方输出流不被关闭和 Audit 分页写出行为。完成后执行：

```bash
mvn -q -pl im-plugin/im-excel,im-management/im-audit/im-audit-server -am test
./scripts/verify.sh quick
./scripts/verify.sh full
```
