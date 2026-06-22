# 2026-06-22 — 压缩对话框 UI 重叠问题修复记录

## 问题描述

用户报告：点击压缩文件时，压缩选择对话框（`CompressDialog`）中所有文字都重叠错乱在一起，无法正常阅读和操作。

## 根因分析

经过多轮调试，发现问题出在 `CompressDialog` 中下拉选择框的实现方式上：

### 原始错误代码结构

```kotlin
ExposedDropdownMenuBox(
    expanded = formatExpanded,
    onExpandedChange = { formatExpanded = it },
    modifier = Modifier.weight(1f),
) {
    OutlinedTextField(
        // ...
        modifier = Modifier
            .fillMaxWidth()
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),  // ← 问题点 1
    )
    ExposedDropdownMenuBox(  // ← 问题点 2：嵌套了第二个 ExposedDropdownMenuBox
        expanded = formatExpanded,
        onExpandedChange = { formatExpanded = it },
    ) {
        formats.forEach { ... }
    }
}
```

**问题点 1**：`menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)` 与 `ExposedDropdownMenuBox` 的自动 anchor 机制冲突。`ExposedDropdownMenuBox` 会自动将其内容中的第一个可锚定组件（`OutlinedTextField`）作为下拉菜单的 anchor，额外添加 `menuAnchor` 修饰符会导致布局计算错误。

**问题点 2**：下拉菜单区域使用了第二个 `ExposedDropdownMenuBox`，而不是 `ExposedDropdownMenu`。两个 `ExposedDropdownMenuBox` 共享同一个 `expanded` 状态，导致它们的布局相互覆盖，所有文字重叠在一起。

## 修复方案

将下拉菜单的 `ExposedDropdownMenuBox` 替换为 `ExposedDropdownMenu`，并移除 `OutlinedTextField` 上的 `menuAnchor` 修饰符：

```kotlin
ExposedDropdownMenuBox(
    expanded = formatExpanded,
    onExpandedChange = { formatExpanded = it },
    modifier = Modifier.weight(1f),
) {
    OutlinedTextField(
        // ...
        modifier = Modifier.fillMaxWidth(),  // 移除 menuAnchor
    )
    ExposedDropdownMenu(  // ← 使用 ExposedDropdownMenu 而非 ExposedDropdownMenuBox
        expanded = formatExpanded,
        onDismissRequest = { formatExpanded = false },
    ) {
        formats.forEach { ... }
    }
}
```

### 关键区别

- `ExposedDropdownMenuBox`：管理展开/收起状态的容器，负责 anchor 定位
- `ExposedDropdownMenu`：纯粹的下拉菜单内容组件，依赖父级 `ExposedDropdownMenuBox` 提供定位

正确做法是：一个 `ExposedDropdownMenuBox` 内包含一个 `OutlinedTextField`（作为 anchor）和一个 `ExposedDropdownMenu`（作为下拉内容），两者共享同一个 `expanded` 状态。

## 修改的文件

- `app/src/main/kotlin/com/medeide/jh/screens/home/landscape/sidebar/resourcepanel/CompressDialog.kt`

## 给后续开发者的建议

在 Compose 中使用 `ExposedDropdownMenuBox` 时，请遵循以下模式：

1. **不要**在 `OutlinedTextField` 上手动添加 `menuAnchor()` 修饰符 — `ExposedDropdownMenuBox` 会自动处理
2. **不要**在下拉菜单区域嵌套第二个 `ExposedDropdownMenuBox` — 使用 `ExposedDropdownMenu` 替代
3. `ExposedDropdownMenu` 的 `onDismissRequest` 应该将 `expanded` 设为 `false`
4. 所有共享同一个 `expanded` 状态的组件都应该放在同一个 `ExposedDropdownMenuBox` 的 content lambda 中

如果你看到对话框中所有文字重叠在一起，大概率是 `ExposedDropdownMenuBox` 嵌套使用错误。
