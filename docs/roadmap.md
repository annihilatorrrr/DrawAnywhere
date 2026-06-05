# 路线图

## 已完成
- [x] 矩形 / 椭圆笔类型
- [x] Hover 笔触预览（屏幕空间）
- [x] 橡皮颜色 / 透明度不持久化（默认 LightGray）
- [x] 双指 / 三指手势（pan/zoom/tap）
- [x] 手指绘制开关
- [x] 缩放锁定 + HUD
- [x] `TapGestureDetector` 独立
- [x] `drawing/` 包：`StrokeTool` 接口 + `FreehandTool` / `ShapeTool`
- [x] 像素橡皮（`CanvasSnapshot` undo/redo）
- [x] `PenType` 加入 `labelResId` / `isEraser` / `icon`
- [x] 取色器（HSV 色轮 / 预设色板 / 最近颜色 / Hex 输入）
- [x] 橡皮擦禁用调色板 + Alpha 滑块（disabled 而非隐藏）
- [x] `Spacing` 设计常量统一 dp 魔数
- [x] 激光笔（TTL 3s + 淡出 + glow 效果）
- [x] 能力对象 `Renderer` / `HitTester`（`Capabilities.kt`）
- [x] `DrawAction.withoutEphemeral()` — 统一过滤临时笔画
- [x] 109 单元测试

## 待做

### 笔 / 工具
- [ ] 箭头形状
- [ ] 文本框
- [ ] 图像插入
- [ ] 常见几何形状（三角形、菱形、星形、多边形）
- [ ] 形状填充（矩形 / 椭圆内部填色）
- [ ] 笔 profile（颜色 / 宽度 / alpha 预设，可与调色板整合）
- [ ] 套索工具

### 工具栏
- [ ] 自定义工具栏按钮（菜单项可单独拖出，如橡皮）
- [ ] 工具栏 profile（可保存/切换不同按钮布局）
- [ ] 工具栏可调整大小
- [ ] 闲置时工具栏透明度可配置（0%–100% + timeout）
- [ ] 退出按钮
- [ ] 拖拽工具栏到指定位置关闭（关闭时不保存位置）
- [ ] 次工具条（垂直方向）

### 保存 / 恢复
- [ ] 保存画布（含背景 / 不含背景）—— 缩放问题待讨论
- [ ] 分享按钮
- [ ] 保存 / 恢复绘制状态（可编辑，即恢复后继续画）

### 交互
- [ ] 关闭手指绘制时触摸透传，笔正常绘画 —— 可能需 Accessibility Service；定时探测方案可选，大部分平板有 hover
- [ ] remap 手势
- [ ] 笔按键环形菜单（中央橡皮 + 外圈功能扇区）
- [ ] 有些 stylus 的按键无法触发橡皮
- [ ] Palm rejection

### 平台 / 窗口
- [ ] DeX 模式下外接显示器绘制
- [ ] 软件运行时再次打开软件则退出
- [ ] 覆盖层窗口大小适配（取色器、设置等 popup 受 WRAP_CONTENT 限制）

### 像素橡皮扩展
- [ ] 支持 Rectangle / Ellipse（边缘轮廓采样）

### 待修
- [ ] `ToolbarLifecycleOwner` 补齐 ON_START / ON_RESUME 生命周期回调

---

## 自动透传触摸 — 初步方案

### 问题

需求：关闭手指绘制时，若笔不在屏幕上（未 hover），手指触摸应当穿透覆盖层传递给下方应用。

核心矛盾：`FLAG_NOT_TOUCHABLE` 是窗口级标志，启用后覆盖层**完全不再接收任何事件**——包括笔的 hover（`ACTION_HOVER_MOVE`）。这意味着一旦透传，就无法检测笔靠近来恢复触摸接收。

### 方案对比

#### A. 双窗口（一静一动）

笔事件窗口永远接收事件。手指触摸窗口随 `fingerDrawingEnabled` + hover 状态切换 `FLAG_NOT_TOUCHABLE`。

- 优点：笔事件通道永不中断
- 缺点：两窗口同步复杂（坐标、生命周期），z-order 需保证笔窗口在上

#### B. Hover 触发 + 定时探测

默认透传。每 ~300ms 短暂移除 `FLAG_NOT_TOUCHABLE`（1 帧），若有 hover 则保持接收，否则恢复透传。

- 优点：单窗口，实现简单
- 缺点：探测间隙最多 300ms 延迟；频繁 flag 切换可能导致视觉闪烁；**探测期间窗口恢复接收触摸，会打断下方应用的手势操作（如滑动、长按）——非常烦**

#### C. 事件重放（保持接收，转发手指）

永远接收所有事件。手指事件在 `onTouchEvent` 中判断：若手指绘制关闭且无笔 hover → 通过 `Instrumentation.sendPointerSync()` 或 `AccessibilityService` 将事件重新注入系统，传递给下方应用。

- 优点：笔事件通道永不中断，无 flag 切换闪烁
- 缺点：需 `INJECT_EVENTS` 权限（系统应用级别）或开启无障碍服务（用户手动授权，体验差）

#### D. AccessibilityService 代理

覆盖层注册无障碍服务，始终接收事件。手指事件通过 `AccessibilityService.onAccessibilityEvent` + `GestureDescription` 转发。

- 优点：不需要系统权限
- 缺点：无障碍服务有性能开销，用户需手动在设置中开启，"辅助功能"提示不雅观

### 推荐：方案 B（默认）+ 方案 D（可选）

方案 B 零权限、对大部分支持 hover 的设备够用。方案 D 作为高级选项，覆盖无 hover 设备。
