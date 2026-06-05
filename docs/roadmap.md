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
- [x] 97 单元测试

## 待做

### 第一批
- [ ] 关闭手指绘制时自动透传触摸（笔未 hover 时）
- [ ] 次工具条（垂直方向）
- [ ] 笔按键环形菜单（中央橡皮 + 外圈功能扇区）
- [ ] 像素橡皮支持 Rectangle / Ellipse（边缘轮廓采样）
- [ ] Palm rejection

### 第二批
- [ ] 选择工具 + 框选 / 移动 / 缩放
- [ ] 形状填充（矩形 / 椭圆）

### 待修
- [ ] 覆盖层窗口大小适配（取色器、设置等 popup 受 WRAP_CONTENT 限制）
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
- 缺点：探测间隙最多 300ms 延迟；频繁 flag 切换可能导致视觉闪烁

#### C. 事件重放（保持接收，转发手指）

永远接收所有事件。手指事件在 `onTouchEvent` 中判断：若手指绘制关闭且无笔 hover → 通过 `Instrumentation.sendPointerSync()` 或 `AccessibilityService` 将事件重新注入系统，传递给下方应用。

- 优点：笔事件通道永不中断，无 flag 切换闪烁
- 缺点：需 `INJECT_EVENTS` 权限（系统应用级别）或开启无障碍服务（用户手动授权，体验差）

#### D. AccessibilityService 代理

覆盖层注册无障碍服务，始终接收事件。手指事件通过 `AccessibilityService.onAccessibilityEvent` + `GestureDescription` 转发。

- 优点：不需要系统权限
- 缺点：无障碍服务有性能开销，用户需手动在设置中开启，"辅助功能"提示不雅观

### 推荐：方案 B

实现最轻量，无需额外权限。300ms 延迟在"笔靠近→开始书写"场景中影响极小（用户接近屏幕时通常 > 300ms），闪烁可通过只改 flag 不重绘窗口缓解。后续可升级为方案 A。
