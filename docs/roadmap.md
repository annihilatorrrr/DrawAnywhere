# 路线图

## 已完成
- [x] 矩形 / 椭圆笔类型
- [x] Hover 笔触预览（屏幕空间）
- [x] 橡皮颜色 / 透明度不持久化（默认 LightGray）
- [x] 双指 / 三指手势（pan/zoom/tap）
- [x] 手指绘制开关
- [x] 缩放锁定 + HUD
- [x] `TapGestureDetector` 独立
- [x] `drawing/` 包：`StrokeTool` 接口 + 四种工具
- [x] 像素橡皮（`CanvasSnapshot` undo/redo）
- [x] `PenType` 加入 `labelResId` / `isEraser`

## 待做

### 第一批
- [ ] 次工具条（垂直方向）
- [ ] 取色器（HSL / 预设色板 / 最近颜色）
- [ ] 笔按键环形菜单（中央橡皮 + 外圈功能扇区）
- [ ] 像素橡皮擦形状（边缘细分 / 插值）
- [ ] Palm rejection

### 第二批
- [ ] 选择工具 + 框选 / 移动 / 缩放
- [ ] 形状填充（矩形 / 椭圆）

### 待修
- [ ] 覆盖层窗口大小适配（取色器、设置等 popup 受 WRAP_CONTENT 限制）
- [ ] `ToolbarLifecycleOwner` 补齐 ON_START / ON_RESUME 生命周期回调
- [ ] `InkEraser24Px` SVG 冗余路径命令清理
