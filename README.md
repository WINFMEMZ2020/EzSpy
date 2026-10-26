# EzSpy

一款受 Spy++ 启发的 Android 窗口检查工具：读取无障碍节点树，提取控件属性，并提供悬浮准星抓取模式。

- 语言：Kotlin
- UI：Material Design 2（黑灰主体 + 紫色强调，跟随系统浅色/深色）
- `minSdk` 26（Android 8.0），`targetSdk` 36（Android 16），`compileSdk` 37
- 包名：`com.udm.ezspy`
- 无网络权限；**不截图、不采集、不上传任何数据**，全部信息只在内存中处理

## 功能

### 抓取器
空状态页，只提供一个「启用抓取器」按钮和权限状态提示。权限不足时引导前往系统设置；权限就绪后：
1. 主界面自动最小化（`moveTaskToBack`）；
2. 显示悬浮球、准星、灰色选中框和可拖动的预览面板；
3. 按住悬浮球拖动准星，实时高亮并预览准星下的控件。

### 悬浮模式
- 球位置固定，只有准星跟随手指；
- 拖动过程中实时命中测试，松手保留选中框和预览，准星淡出消失；
- 命中的是准星下方的目标窗口，自动排除 EzSpy 自己的悬浮窗（穿透预览面板）；
- 预览面板只显示选中节点的摘要（类名、文本、ID、边界、包名），可滚动、可按住标题栏拖动；
- 预览面板可以拖到屏幕外（甚至完全移出）；移出后**点按悬浮球**即可把面板拉回默认位置；
- 返回键会保留当前选中信息并回到主界面；未选中任何控件时空白返回。

### 节点树
- 返回主界面后，显示所选控件所属窗口的**完整节点树**（先序遍历、全部节点，而不是只显示一条祖先链）；
- 顶部显示目标包名与节点总数；选中节点自动定位并居中，附带上下文与兄弟节点；
- 行高紧凑，层级缩进 20dp/层，带代码编辑器风格的竖向对齐线；
- 节点树页可水平平移查看深层节点；该页不再用左右滑动切换标签，改为点击标签切换（其他页仍可滑动）；
- 点击任意节点会选中它并跳转到属性栏。

### 属性栏
- 显示选中节点的全部可读取属性：基本信息、窗口、布局、状态、支持的操作、附加数据等；
- 「文本」一栏始终存在，没有文本时显示“无”；
- 「获取文本」：从当前节点开始遍历其整棵子树，把每个控件的文本/内容描述按先序、以单个换行连接，弹出一个带标题的可编辑文本框，可复制或继续编辑；
- 支持点击任意属性行复制其值；
- 未选中节点时为空状态。

## 权限

| 权限 | 用途 |
| --- | --- |
| 无障碍服务 (`BIND_ACCESSIBILITY_SERVICE`) | 读取窗口节点树和属性 |
| 悬浮窗 (`SYSTEM_ALERT_WINDOW`) | 悬浮球与预览面板 |
| `QUERY_ALL_PACKAGES` | 把包名解析为应用名 |

> 注意：应用被重新安装/覆盖安装后，系统可能会关闭无障碍服务，需要在系统设置中重新开启。

## 构建

直接用 Android Studio 打开项目构建即可；也可以：

```bash
./gradlew :app:assembleDebug
```

## 代码结构

```
com.udm.ezspy
├── MainActivity.kt              // 标题栏 + TabLayout + ViewPager2
├── data/                        // NodeSnapshot、Capture、属性模型、结果仓库
├── service/
│   ├── EzSpyAccessibilityService.kt
│   ├── OverlayController.kt     // 悬浮球/准星/高亮/预览面板
│   ├── NodeHitTester.kt         // 按屏幕坐标命中节点
│   ├── NodeSnapshotFactory.kt   // 节点快照 + 完整窗口树
│   └── overlay/                 // 自绘 Ball / Crosshair / Highlight
├── ui/
│   ├── grabber/                 // 抓取器页
│   ├── tree/                    // 完整节点树、对齐线、水平平移
│   ├── properties/              // 属性栏、文本提取
│   └── SpyViewModel.kt
└── util/                        // 权限、dp/屏幕尺寸
```

## 已知的 IDE 提示

- Android Studio 的 **Layout Validation** 对 `item_node_tree.xml` 可能提示 “title is covered by System UI”。该布局是 RecyclerView 的单行，并不是全屏页面；真实界面由 `MainActivity` 统一处理系统栏 insets，因此此提示属于单行预览的误报。
- `lint` 结果为 0 errors；仅保留一条 Gradle 版本更新提示（模板使用 Gradle 9.6.0，提示可升级到 9.8.2）。
