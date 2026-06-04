# 矩形 / 椭圆绘制工作方案

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 为 DrawAnywhere 增加矩形和椭圆绘制，作为笔类型与 Pen / StrokeEraser 并列。

**Architecture:** 矩形和椭圆是 `PenType` 的新成员，创建的是同一种 `DrawObject.Stroke`（仅 points 语义不同：2 个点定义边界矩形），渲染时根据 `penType` 画矩形/椭圆轮廓而非自由路径。不需要新的 DrawObject 子类、不需要选中/编辑模式、不需要格式刷。

**Tech Stack:** Kotlin, Android Canvas, Compose toolbar

---

## 现状

```kotlin
enum class PenType { Pen, StrokeEraser }

data class PenConfig(penType, color, width, alpha)

data class Stroke(
    val points: MutableList<Offset>,
    val color: Color, val width: Float, val alpha: Float,
    val transform: ObjectTransform = ObjectTransform(),
)
```

`CanvasTouchHandler` 单指路径始终走自由绘制：DOWN 调 `viewModel.startStroke()`，MOVE 调 `updateStroke()`，UP 调 `finishStroke()`。`DrawController.createStroke()` 创建 Stroke 时将当前 `penConfig` 的属性写入。

渲染：`NativeDrawCanvasView.onDraw()` 遍历 strokeList，每个 stroke 用 `buildAndroidPath()` 转成平滑 Path 后 `drawPath`。

---

## Phase 1: 模型扩展（估计 30min）

### 1.1 PenType 增加 Rectangle、Ellipse

**文件：** `model/PenType.kt`

```kotlin
enum class PenType {
    Pen, StrokeEraser, Rectangle, Ellipse
}
```

### 1.2 Stroke 增加 penType 字段

**文件：** `model/DrawObject.kt`

```kotlin
data class Stroke(
    val points: MutableList<Offset>,
    val color: Color,
    val width: Float,
    val alpha: Float,
    val penType: PenType = PenType.Pen,  // ← 新增
    val transform: ObjectTransform = ObjectTransform(),
) : DrawObject()
```

### 1.3 DrawController.createStroke 传入 penType

**文件：** `DrawController.kt`

`createStroke()` 中创建 Stroke 时，从 `penConfig.penType` 取值写入 `penType` 字段。

现有逻辑已从 `penConfig` 读取 color/width/alpha，penType 同理：

```kotlin
// 改前
DrawObject.Stroke(
    points = mutableListOf(newPoint),
    color = penConfig.color,
    width = penConfig.width,
    alpha = penConfig.alpha
)
// 改后：加 penType = penConfig.penType
```

---

## Phase 2: 矩形 / 椭圆绘制手势（估计 1h）

### 2.1 思路

`CanvasTouchHandler` 不动。形状和自由绘制的差异封装在 `DrawController` 内部——由 `penConfig.penType` 决定 `createStroke` / `updateLatestStroke` 的行为。

当前调用链（不变）：

```
DOWN  → viewModel.startStroke(point, modifier)
MOVE  → viewModel.updateStroke(point)
UP    → viewModel.finishStroke()
```

### 2.2 DrawController.createStroke 按 penType 分支

**文件：** `DrawController.kt`

自由绘制：创建 1 个 point 的 Stroke，后续 `updateLatestStroke` 逐个追加。

矩形/椭圆：创建 2 个 point 的 Stroke（起点和终点初始为同一点），后续 `updateLatestStroke` 替换第二个点。

```kotlin
fun createStroke(newPoint: Offset) {
    if (penConfig.penType == PenType.StrokeEraser) {
        eraseStroke(newPoint)
        return
    }
    val points = when (penConfig.penType) {
        PenType.Pen -> mutableListOf(newPoint)                 // 自由绘制：起始 1 点
        PenType.Rectangle, PenType.Ellipse -> mutableListOf(newPoint, newPoint) // 形状：锚点=终点（初始）
        else -> mutableListOf(newPoint)
    }
    _strokeList.add(DrawObject.Stroke(
        points = points,
        color = penConfig.color,
        width = penConfig.width,
        alpha = penConfig.alpha,
        penType = penConfig.penType,
    ))
}
```

### 2.3 DrawController.updateLatestStroke 按 penType 分支

**文件：** `DrawController.kt`

```kotlin
fun updateLatestStroke(newPoint: Offset) {
    if (penConfig.penType == PenType.StrokeEraser) {
        eraseStroke(newPoint)
        return
    }
    val stroke = _strokeList.lastOrNull() ?: return
    when (stroke.penType) {
        PenType.Pen -> stroke.points.add(newPoint)       // 追加
        PenType.Rectangle, PenType.Ellipse -> stroke.points[1] = newPoint  // 替换终点
        else -> stroke.points.add(newPoint)
    }
}
```

### 2.4 DrawController.finishStroke 标准化形状

**文件：** `DrawController.kt`

形状的 2 点可能形成反向矩形（end 在 start 左上方），finish 时标准化并丢弃过小形状。

```kotlin
fun finishStroke() {
    if (penConfig.penType == PenType.StrokeEraser) return
    if (_strokeList.isEmpty()) return
    val latest = _strokeList.last()

    // 标准化形状矩形
    if (latest.penType == PenType.Rectangle || latest.penType == PenType.Ellipse) {
        val p0 = latest.points[0]; val p1 = latest.points[1]
        val left = min(p0.x, p1.x); val top = min(p0.y, p1.y)
        val right = max(p0.x, p1.x); val bottom = max(p0.y, p1.y)
        if (right - left < 4f && bottom - top < 4f) {
            _strokeList.removeAt(_strokeList.lastIndex)  // 太小，丢弃
            return
        }
        latest.points[0] = Offset(left, top)
        latest.points[1] = Offset(right, bottom)
    } else {
        // 原有逻辑：空 points 的 stroke 丢弃
        if (latest.points.isEmpty()) {
            _strokeList.removeAt(_strokeList.lastIndex)
            return
        }
    }

    undoRedo.push(DrawAction.AddPath(latest))
    notifyChanged()
}
```

### 2.5 CanvasTouchHandler 不动

不需要 `when (penType)` 分支、不需要 `shapeStartPoint` / `shapeActive` / `commitShape`。它继续调 `startStroke → updateStroke → finishStroke`，和自由绘制完全一样的路径。

---

## Phase 3: 渲染矩形 / 椭圆（估计 1h）

### 3.1 NativeDrawCanvasView 区分渲染

**文件：** `NativeDrawCanvasView.kt`

在 `onDraw()` 的 stroke 循环中，不再无条件调 `buildAndroidPath()` + `drawPath()`：

```kotlin
for (stroke in drawController.strokeList) {
    if (stroke.points.isEmpty()) continue
    
    pathPaint.strokeWidth = stroke.width
    pathPaint.color = /* 现有颜色/alpha 计算 */...
    
    when (stroke.penType) {
        PenType.Pen, PenType.StrokeEraser -> {
            val path = buildAndroidPath(stroke.points)
            canvas.drawPath(path, pathPaint)
        }
        PenType.Rectangle -> {
            val p0 = stroke.points[0]; val p1 = stroke.points[1]
            canvas.drawRect(p0.x, p0.y, p1.x, p1.y, pathPaint)
        }
        PenType.Ellipse -> {
            val p0 = stroke.points[0]; val p1 = stroke.points[1]
            val rect = RectF(p0.x, p0.y, p1.x, p1.y)
            canvas.drawOval(rect, pathPaint)
        }
    }
}
```

**关键：** `pathPaint.style = Paint.Style.STROKE` 不变——矩形和椭圆的轮廓线，不是填充。

> points 不够 2 个时 fallback 到 `buildAndroidPath`。

---

## Phase 4: 橡皮擦适配（估计 30min）

橡皮擦目前按 stroke 的点路径做距离检测。对于 Rectangle/Ellipse 的 2 点 stroke，需要在橡皮擦逻辑中加入矩形/椭圆 hit test。

**文件：** `DrawController.kt` 的 `eraseStroke()`

```kotlin
// 现有：遍历每个 stroke 的线段
// 新增：对 Rectangle/Ellipse stroke，检查点是否在矩形轮廓附近
when (stroke.penType) {
    PenType.Rectangle, PenType.Ellipse -> {
        if (hitTestShape(stroke, point, eraserRadius)) {
            indexToErase = i
        }
    }
    else -> { /* 现有线段距离检测 */ }
}
```

`hitTestShape`：对矩形，计算点到四条边的距离；对椭圆，简化做矩形 hit test（够用）。

---

## Phase 5: 工具栏 UI（估计 1h）

### 5.1 PenTypeSelector 扩展

**文件：** `view/toolbar/PenTypeSelector.kt`

现有：Pen 和 StrokeEraser 两个选项。

改为：Row 内四个图标按钮（画笔、橡皮、矩形、椭圆），选中高亮。

图标：用 `Icons.Default` 或自定义 drawable。矩形/椭圆用 `Icons.Outlined.Rectangle` / `Icons.Outlined.Circle`（如果 Material Icons 有），不行就用 `Canvas` 手画小图标。

### 5.2 字符串资源

**文件：** `values/strings.xml`, `values-zh-rCN/strings.xml`

```xml
<string name="pen_type_rectangle">Rectangle</string>
<string name="pen_type_ellipse">Ellipse</string>
```

```xml
<string name="pen_type_rectangle">矩形</string>
<string name="pen_type_ellipse">椭圆</string>
```

---

## Phase 6: 默认配置 & 持久化（估计 30min）

### 6.1 默认 PenConfig

**文件：** `DrawViewModel.kt` 的 `defaultPenConfigs()`

```kotlin
fun defaultPenConfigs(): Map<PenType, PenConfig> = mapOf(
    PenType.Pen to PenConfig(penType = PenType.Pen),
    PenType.StrokeEraser to PenConfig(penType = PenType.StrokeEraser, width = 50f),
    PenType.Rectangle to PenConfig(penType = PenType.Rectangle, width = 5f),
    PenType.Ellipse to PenConfig(penType = PenType.Ellipse, width = 5f),
)
```

### 6.2 PenConfig 持久化

`PreferencesManager` 已有 `saveUiState` / `loadUiState`，`UiState` 中的 `penConfigs: Map<PenType, PenConfig>` 已参与持久化（通过 `debounce(300)` 自动保存）。新增的 Rectangle/Ellipse 的 config 自动跟着存/取，不需要额外工作。

---

## 不改的部分

- `DrawAction`：不加新类型。矩形/椭圆就是 `AddPath` / `ErasePath`。
- `UndoRedoManager`：不动。
- `ObjectTransform`：Stroke 上已有，矩形/椭圆不主动使用（没有选中/操作功能），但字段保留。
- `CanvasViewport`：不动。
- 多指手势（pan/zoom/tap）：不动。

---

## 文件清单

| 文件 | 改动 |
|------|------|
| `model/PenType.kt` | 加 `Rectangle`, `Ellipse` |
| `model/DrawObject.kt` | Stroke 加 `penType` 字段 |
| `DrawController.kt` | `createStroke`/`updateLatestStroke`/`finishStroke` 按 penType 分支 |
| `CanvasTouchHandler.kt` | 不动 |
| `NativeDrawCanvasView.kt` | `onDraw` 按 penType 分支渲染 |
| `DrawViewModel.kt` | `defaultPenConfigs` 加矩形/椭圆默认值 |
| `PenTypeSelector.kt` | 扩展到 4 个工具图标 |
| `values/strings.xml` | 加矩形/椭圆字符串 |
| `values-zh-rCN/strings.xml` | 加矩形/椭圆中文 |
