# 对象绘制 (Object Drawing) 工作方案

> **For Hermes:** Use subagent-driven-development skill to implement this plan phase-by-phase.

**Goal:** 为 DrawAnywhere 增加形状绘制（矩形、椭圆）、对象操作（选中/移动/缩放/旋转/删除）、样式编辑（边框色/宽/填充色）和格式刷（批量样式应用）。

**Architecture:** 扩展 `DrawObject` sealed class 增加 shape 子类型，新增 `ToolMode` 枚举驱动 `CanvasTouchHandler` 的 dispatch 分支，`DrawController` 增加对象 CRUD（含 undo/redo），在 `NativeDrawCanvasView` 中渲染 shapes，工具栏增加工具切换和样式控件。

**Tech Stack:** Kotlin, Android Canvas, Compose toolbar, 现有架构（MVVM + DrawController + CanvasTouchHandler）

---

## 现状分析

### 现有模型
```kotlin
// DrawObject.kt — 仅有自由绘制 Stroke
sealed class DrawObject {
    data class Stroke(points, color, width, alpha, transform) : DrawObject()
}
sealed class DrawAction { AddPath, ErasePath, ClearPaths }

// ObjectTransform — 已定义但 Stroke 未使用
data class ObjectTransform(scaleX, scaleY, rotation, offsetX, offsetY)
```

### 现有触摸
`CanvasTouchHandler` 三路 dispatch：①多指手势 ②多指入口 ③单指绘制。无工具模式概念，始终处于自由绘制模式。

### 现有渲染
`NativeDrawCanvasView.onDraw()` 遍历 `strokeList`，将 `Offset` 列表转为 Android `Path` 并用 `Paint.Style.STROKE` 绘制。

### 现有状态
`DrawViewModel.uiState` 有 `currentPenType`（Pen/StrokeEraser），没有工具模式。

---

## Phase 1: 数据模型 + 形状渲染（估计 2h）

**目标：** 能画矩形和椭圆，undo/redo 支持。

### 1.1 定义 ShapeStyle
**文件：** `model/DrawObject.kt`

```kotlin
data class ShapeStyle(
    val borderColor: Color = Color.Red,
    val borderWidth: Float = 4f,
    val fillColor: Color = Color.Transparent,
    val alpha: Float = 1f,
)
```

### 1.2 扩展 DrawObject
```kotlin
sealed class DrawObject {
    // ... Stroke 不变 ...

    data class Rectangle(
        val x: Float, val y: Float,    // 左上角（画布坐标）
        val width: Float, val height: Float,
        val style: ShapeStyle,
        val transform: ObjectTransform = ObjectTransform(),
    ) : DrawObject()

    data class Ellipse(
        val x: Float, val y: Float,
        val width: Float, val height: Float,
        val style: ShapeStyle,
        val transform: ObjectTransform = ObjectTransform(),
    ) : DrawObject()
}
```

> 实现方法：patch 两个新 data class 到 sealed class 内。

### 1.3 扩展 DrawAction
```kotlin
sealed class DrawAction {
    // ... 现有不变 ...
    data class AddShape(val shape: DrawObject) : DrawAction()
    data class RemoveShape(val shape: DrawObject) : DrawAction()
    data class ModifyShapeStyle(val shape: DrawObject, val oldStyle: ShapeStyle, val newStyle: ShapeStyle) : DrawAction()
}
```

### 1.4 DrawController 增加 shape 管理
**文件：** `DrawController.kt`

- 增加 `private val _shapeList = mutableListOf<DrawObject>()`（仅 shapes，与 strokes 分开）
- 或者合并 `_drawObjects: MutableList<DrawObject>` 统一列表
- **建议：** 保持 `_strokeList` 不变，新增 `_shapeList`，`onDraw` 中分别遍历
- `addShape(shape)`, `removeShape(shape)`, `updateShapeStyle(shape, newStyle)`
- 每个操作推入 `undoRedo.push(DrawAction.AddShape/RemoveShape/ModifyShapeStyle)`

### 1.5 形状渲染
**文件：** `NativeDrawCanvasView.kt`

在 `onDraw()` 中，在 stroke 循环之后增加 shape 渲染：

```kotlin
for (shape in drawController.shapeList) {
    canvas.save()
    val t = shape.transform
    canvas.translate(t.offsetX, t.offsetY)
    canvas.rotate(t.rotation, shape.x + shape.width/2, shape.y + shape.height/2)
    canvas.scale(t.scaleX, t.scaleY, shape.x + shape.width/2, shape.y + shape.height/2)

    val rect = RectF(shape.x, shape.y, shape.x + shape.width, shape.y + shape.height)

    // Fill
    if (shape.style.fillColor != Color.Transparent) {
        fillPaint.color = shape.style.fillColor.toArgb()
        fillPaint.alpha = (shape.style.alpha * 255).toInt()
        canvas.drawRect(rect, fillPaint) // or drawOval for Ellipse
    }

    // Border
    borderPaint.color = shape.style.borderColor.toArgb()
    borderPaint.strokeWidth = shape.style.borderWidth
    borderPaint.alpha = (shape.style.alpha * 255).toInt()
    canvas.drawRect(rect, borderPaint)

    canvas.restore()
}
```

需要增加 `fillPaint`（`Paint.Style.FILL`）和 `borderPaint`（`Paint.Style.STROKE`）。

> 使用 `when (shape)` 区分 Rectangle/Ellipse 的绘制方法。

### 1.6 Undo/Redo 集成
`DrawController.undo()` / `redo()` 中增加 `AddShape` / `RemoveShape` 分支。

---

## Phase 2: 工具模式 + 形状绘制（估计 2h）

**目标：** 工具栏切换工具，拖拽创建矩形/椭圆。

### 2.1 定义 ToolMode
**文件：** `model/PenType.kt`（或新建 `model/ToolMode.kt`）

```kotlin
enum class ToolMode {
    Freehand,       // 当前行为
    Rectangle,
    Ellipse,
    Select,         // 对象选中/操作（Phase 4）
    FormatPainter,  // 格式刷（Phase 6）
}
```

### 2.2 UiState 增加 toolMode
**文件：** `DrawViewModel.kt`

```kotlin
data class UiState(
    // ... 现有字段 ...
    val toolMode: ToolMode = ToolMode.Freehand,
)
```

增加 `fun setToolMode(mode: ToolMode)`。

### 2.3 工具栏 UI
**文件：** `view/toolbar/ToolbarButtons.kt`, `DrawToolbar.kt`

- 在 `PenTypeSelector` 旁边增加工具切换按钮（或复用/改造现有按钮区）
- 按钮图标：画笔（Freehand），矩形，椭圆，选择箭头
- 选中的工具高亮

### 2.4 CanvasTouchHandler 形状绘制手势
**文件：** `CanvasTouchHandler.kt`

增加构造函数参数 `toolMode: () -> ToolMode`。

在单指 DOWN 路径中增加分支：

```kotlin
when (toolMode()) {
    ToolMode.Freehand -> { /* 现有绘制逻辑 */ }
    ToolMode.Rectangle -> startShapeCreation(event)
    ToolMode.Ellipse -> startShapeCreation(event)
    ToolMode.Select -> startSelection(event)   // Phase 4
    ToolMode.FormatPainter -> handleFormatPick(event) // Phase 6
}
```

形状创建状态机（附加在 `CanvasTouchHandler` 内）：
- `DOWN`: 记录 `shapeStartX`, `shapeStartY`，创建临时 shape（不放列表）
- `MOVE`: 更新临时 shape 的 width/height，调用 `onInvalidate()`
- `UP`: 提交 shape 到 `DrawController.addShape()`

需要增加 `onShapePreview` 回调，让 `NativeDrawCanvasView` 在 `onDraw` 末尾渲染预览（虚线矩形框）。

### 2.5 预览渲染
在 `NativeDrawCanvasView.onDraw()` 中，如果有临时 shape（通过 touchHandler 的 getter 获取），用虚线 paint 绘制。

---

## Phase 3: 样式编辑（估计 1.5h）

**目标：** 选中对象后修改边框色、宽、填充色。

### 3.1 当前选中的对象
在 `DrawViewModel` 中增加 `selectedObjectIndex: Int?`。

### 3.2 样式编辑面板
当有选中对象时，工具栏显示：
- 边框颜色选择器（复用 `ColorPicker`）
- 边框宽度滑块（`PenControls` 模式）
- 填充颜色选择器（含"无填充"/透明选项）
- Alpha 滑块

修改立即通过 `DrawController.updateShapeStyle()` 生效，undo 可逆。

---

## Phase 4: 对象选中与操作（估计 3h）

**目标：** 点击选中、拖拽移动、调整大小、旋转、删除。

这是最复杂的阶段。

### 4.1 Hit testing
```kotlin
fun hitTest(x: Float, y: Float, shape: DrawObject): Boolean {
    return when (shape) {
        is Rectangle -> x in shape.x..(shape.x + shape.width) &&
                        y in shape.y..(shape.y + shape.height)
        is Ellipse -> /* ellipse hit test */ 
        is Stroke -> false
    }
}
```

### 4.2 选择渲染
选中对象绘制蓝色边框 + 4 角 + 4 边中点的操作手柄（小方块）。

### 4.3 拖拽移动
- DOWN 在对象区域内 → 记录偏移，进入移动模式
- MOVE → 更新 `ObjectTransform.offsetX/Y`
- UP → 提交 `ModifyShapeTransform` action

### 4.4 缩放手柄
- DOWN 在手柄上 → 记录初始尺寸和触摸点
- MOVE → 计算新的宽高
- UP → 提交

### 4.5 旋转手柄
- 顶部中间手柄为旋转手柄
- DOWN → 记录初始角度
- MOVE → 计算旋转角度（atan2）
- UP → 提交

### 4.6 删除
- 选中状态下，工具栏出现删除按钮
- 或：双击选中对象删除

### 4.7 点击空白取消选中

---

## Phase 5: 格式刷（估计 1.5h）

**目标：** 从源对象拾取样式，批量应用到目标对象。

### 5.1 状态机
`FormatPainterState`: Idle → Picked(sourceStyle) → Idle

### 5.2 交互
- 点击格式刷按钮 → 进入"拾取"模式
- 点击源对象 → 存储其 style，进入"应用"模式（光标变为格式刷图标）
- 点击目标对象 → 应用存储的 style
- 可连续点击多个目标应用同一样式
- 再次点击格式刷按钮或点空白 → 退出

### 5.3 DrawAction
```kotlin
data class BatchModifyStyles(
    val modifications: List<Pair<DrawObject, ShapeStyle>> // object + old style
) : DrawAction()
```

这样 undo 一次性还原所有修改。

---

## Phase 6: 清理与优化（估计 1h）

### 6.1 死代码清理
- `ObjectTransform` 确认已在 Stroke 和 Shape 中使用
- 移除 `DrawAction` 中 "Path" 命名不一致（`AddPath` → `AddStroke`？）— 低优先级

### 6.2 Preferences 持久化
- 当前 `ShapeStyle`（作为新 shape 的默认样式）
- 最近使用的填充/边框色

### 6.3 字符串资源
- `values/strings.xml` 和 `values-zh-rCN/strings.xml`
- 工具名称：Rectangle→矩形, Ellipse→椭圆, Select→选择, Format Painter→格式刷

---

## 风险评估

| 风险 | 缓解 |
|------|------|
| 触摸状态机复杂度爆炸 | `CanvasTouchHandler` 已三路 dispatch，新增 tool mode 仅增加分支，不改变现有手势逻辑 |
| 旋转/缩放交互体验差 | 先做拖拽移动 + 删除，再迭代旋转/缩放 |
| 渲染性能 | shapes 数量通常远少于 strokes，逐个 `canvas.save/restore` 可接受 |
| 与自由绘制模式的交互 | tool mode 切换后完全不同的手势语义，由 `when(toolMode())` 分流 |

## 可分期交付

- **MVP（Phase 1+2）:** 矩形/椭圆绘制 + undo/redo，可用
- **v2（Phase 3+4）:** 样式编辑 + 选中操作，完整体验
- **v3（Phase 5）:** 格式刷，锦上添花
