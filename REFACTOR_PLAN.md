# DrawAnywhere 重构计划

## 当前架构（v2.1-alpha2）

**混合架构**：原生 View 画布 + Compose 工具条。

```
view/canvas/
  NativeDrawCanvasView  — onDraw 渲染 + HUD + viewport 观察
  CanvasTouchHandler    — ①多指 → ②进入 → ③单指 状态机
  CanvasViewport        — pan/zoom/resetAt/zoomLock 纯数据类

controller/
  UndoRedoManager       — undo/redo 栈（DrawController 拆出）

model/
  DrawObject / DrawAction / PenType / PenConfig / StrokeModifier
```

---

## 已完成步骤

### ✅ 步骤 0a-b：penConfig 初始化修复、runBlocking 确认
无 bug 需修。`runBlocking` 保留（DataStore 读 <5ms vs 异步 UI 闪烁）。

### ✅ 步骤 1-2：NativeDrawCanvasView + MainService 接入
原生 View 画布替代 Compose `DrawCanvas`。

### ✅ 步骤 3：删除旧代码，精简生命周期
删除 `DrawCanvas.kt`、`StylusAwareDrawing.kt`。`ToolbarLifecycleOwner` 仅实现 `LifecycleOwner` + `SavedStateRegistryOwner`。

### ✅ 步骤 4：拆分 DrawToolbar.kt（1061 行 → 多个文件）
`ToolbarButton`、`ToolbarCard`、`ToolbarButtons`、`PenTypeSelector`、`ColorPicker`、`PenControls`、`ToolbarSettings`、`ToolbarOrientation`。

### ✅ 步骤 5：PathWrapper → DrawObject.Stroke sealed class
`DrawObject.Stroke(points, color, width, alpha, transform)`。`DrawAction` 密封类（AddPath / ErasePath / ClearPaths）。

### ✅ 步骤 6：DrawController 拆出 UndoRedoManager
`UndoRedoManager(maxDepth=50)`：push/pop/pushRedo/popRedo + StateFlow canUndo/canRedo。

### ✅ 步骤 7：DrawViewModel 部分拆分
`UiState` + `ServiceState` data class。Viewport 状态独立 `_viewport: StateFlow<CanvasViewport>`。ToolbarViewModel / CanvasViewModel 的完整拆分延后（当前 DrawViewModel 未过度膨胀）。

### ✅ Viewport（v2.1-alpha1 新增）
`CanvasViewport`：pan、zoomAt（pivot-anchored）、resetAt（center-preserving）、zoomLock。HUD 显示百分比 + 锁图标。双指 pan/zoom 以手势起始状态为锚点（无漂移）。三指手势禁 pan/zoom。

### ✅ Finger / Stylus 分离（v2.1-alpha1）
手指 50ms debounce，积累 MOVE 点，debounce 到期或抬起时 flush。Stylus 即时起笔，无 debounce。Stylus 打断多指手势走 CANCEL → DOWN 路径，恢复 viewport。

### ✅ CanvasTouchHandler 重构（v2.1-alpha2）
触摸逻辑从 NativeDrawCanvasView 抽出。`TapGesture` 内部类封装每手势状态。`Map<Int, TapGesture>` 实现 finger-count agnostic（加四指手势只需添加一个 map entry）。双击延迟可配置（0=立即，TAP_INTERVAL_MS=延迟等三击）。回调由 View 侧映射到 ViewModel 动作。17 条测试覆盖。

### ✅ 步骤 8：低级问题修复
`previousPenType`/`isStrokeDown` 改为 private、注释修正、`AboutScreen` 改为 private、`InkEraser24Px` dummy path 删除。

---

## 遗留死代码（未删，非阻塞）

| 文件 | 内容 |
|---|---|
| `DrawObject.kt:6-12` | `ObjectTransform` — 无引用 |
| `DrawObject.kt:20` | `Stroke.transform: ObjectTransform` — 存但无读写 |
| `DrawObject.kt:24-27` | `DrawAction.AddPath/ErasePath/ClearPaths` 命名用 "Path"，其余一致用 "Stroke" |

---

## 长期功能愿景（按依赖）

### 第一批（无依赖，任意顺序）

| # | 功能 | 预估 |
|---|---|---|
| 8 | 手指绘画 | 0:30 |
| 14 | 次工具条（垂直） | 1:00 |
| 15 | 取色器（预置调色盘 + 最近颜色 + HSV） | 2:00 |
| 16 | 笔按键环形菜单（中央空心橡皮擦 + 外圈功能扇区） | 3:00 |

### 第二批（依赖 model 扩展）

| # | 功能 |
|---|---|
| 17 | 矩形/星形形状工具 |
| 18 | 选择工具 + 对象级操作 |
