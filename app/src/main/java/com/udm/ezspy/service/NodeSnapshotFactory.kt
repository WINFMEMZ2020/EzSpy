@file:Suppress("DEPRECATION")

package com.udm.ezspy.service

import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.udm.ezspy.data.Capture
import com.udm.ezspy.data.NodeSnapshot
import com.udm.ezspy.data.PropertyGroup
import com.udm.ezspy.data.PropertyRow
import com.udm.ezspy.data.PropertyValueKind

/**
 * Copies everything interesting out of a live [AccessibilityNodeInfo].
 *
 * Node objects are only valid for a short time and must never be kept by the
 * UI, so each snapshot is a plain, immutable value object.
 */
class NodeSnapshotFactory(
    private val packageManager: PackageManager,
) {

    private val appLabelCache = HashMap<String, String?>()

    /** Only the ancestor chain, used by the floating preview panel. */
    fun buildChain(node: AccessibilityNodeInfo): List<NodeSnapshot> {
        val chain = ArrayList<AccessibilityNodeInfo>(12)
        var current: AccessibilityNodeInfo? = node
        var guard = 0
        while (true) {
            val item = current ?: break
            chain.add(item)
            current = safe { item.parent }
            guard++
            if (guard >= MAX_CHAIN_LENGTH) break
        }
        chain.reverse()
        return chain.mapIndexed { index, item -> snapshot(item, index) }
    }

    private fun snapshot(node: AccessibilityNodeInfo, depth: Int): NodeSnapshot {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val className = safe { node.className?.toString() }
        val text = safe { node.text?.toString() }
        val contentDescription = safe { node.contentDescription?.toString() }
        val viewId = safe { node.viewIdResourceName }
        val packageName = safe { node.packageName?.toString() }
        val appLabel = resolveAppLabel(packageName)

        return NodeSnapshot(
            className = className,
            text = text,
            contentDescription = contentDescription,
            viewId = viewId,
            packageName = packageName,
            appLabel = appLabel,
            bounds = bounds,
            depth = depth,
            propertyGroups = buildPropertyGroups(
                node = node,
                depth = depth,
                className = className,
                text = text,
                contentDescription = contentDescription,
                viewId = viewId,
                packageName = packageName,
                appLabel = appLabel,
            ),
        )
    }

    /**
     * Captures the complete hierarchy of the window that contains [pickedNode].
     * Nodes are stored in pre-order (root first, then each child subtree), with
     * their depth, so the UI can render the whole tree with indentation.
     */
    fun buildCapture(pickedNode: AccessibilityNodeInfo): Capture? {
        val root = findRoot(pickedNode)
        val nodes = ArrayList<NodeSnapshot>(256)
        var pickedIndex = -1
        var truncated = false
        var visited = 0

        val stack = ArrayDeque<NodeAtDepth>()
        stack.addLast(NodeAtDepth(root, 0))

        while (stack.isNotEmpty()) {
            if (visited >= MAX_TREE_NODES) {
                truncated = true
                break
            }
            val current = stack.removeLast()
            visited++

            if (pickedIndex < 0 && isSameNode(current.node, pickedNode)) {
                pickedIndex = nodes.size
            }
            nodes.add(snapshot(current.node, current.depth))

            if (current.depth >= MAX_TREE_DEPTH) continue
            val childCount = safe { current.node.childCount } ?: 0
            // Push in reverse so the first child's subtree is visited first,
            // yielding a pre-order flat list.
            for (index in childCount - 1 downTo 0) {
                val child = safe { current.node.getChild(index) } ?: continue
                stack.addLast(NodeAtDepth(child, current.depth + 1))
            }
        }

        if (nodes.isEmpty()) return null
        val index = if (pickedIndex in nodes.indices) pickedIndex else 0
        val picked = nodes[index]

        return Capture(
            nodes = nodes,
            pickedIndex = index,
            targetPackage = picked.packageName,
            targetLabel = picked.appLabel,
            windowTitle = safe { pickedNode.window?.title?.toString() },
            truncated = truncated,
        )
    }

    private fun findRoot(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var root = node
        var guard = 0
        while (guard++ < MAX_CHAIN_LENGTH) {
            val parent = safe { root.parent } ?: break
            root = parent
        }
        return root
    }

    private fun isSameNode(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean {
        if (a == b) return true
        return safe { a.windowId } == safe { b.windowId } && a.hashCode() == b.hashCode()
    }

    private data class NodeAtDepth(
        val node: AccessibilityNodeInfo,
        val depth: Int,
    )

    private fun buildPropertyGroups(
        node: AccessibilityNodeInfo,
        depth: Int,
        className: String?,
        text: String?,
        contentDescription: String?,
        viewId: String?,
        packageName: String?,
        appLabel: String?,
    ): List<PropertyGroup> {
        val groups = ArrayList<PropertyGroup>(6)

        val basic = ArrayList<PropertyRow>(14)
        basic.textRow("类名", className ?: "未知节点")
        val displayText = text?.trim()?.takeIf { it.isNotEmpty() }
        basic.add(
            PropertyRow(
                label = "文本",
                value = displayText ?: "无",
                kind = if (displayText == null) PropertyValueKind.MUTED else PropertyValueKind.NORMAL,
            )
        )
        basic.textRow("内容描述", contentDescription)
        basic.textRow("提示文本", safe { node.hintText?.toString() })
        basic.textRow("错误信息", safe { node.error?.toString() })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            basic.textRow("工具提示", safe { node.tooltipText?.toString() })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            basic.textRow("状态描述", safe { node.stateDescription?.toString() })
        }
        basic.textRow("View ID", viewId)
        basic.textRow("包名", packageName)
        basic.textRow("应用", appLabel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            basic.textRow("唯一 ID", safe { node.uniqueId })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            basic.textRow("面板标题", safe { node.paneTitle?.toString() })
        }
        if (Build.VERSION.SDK_INT >= 34) {
            basic.textRow("容器标题", safe { node.containerTitle?.toString() })
        }
        if (Build.VERSION.SDK_INT >= 36) {
            basic.textRow("补充描述", safe { node.supplementalDescription?.toString() })
        }
        if (basic.isNotEmpty()) groups.add(PropertyGroup("基本信息", basic))

        val window = safe { node.window }
        val windowRows = ArrayList<PropertyRow>(6)
        windowRows.valueRow("窗口 ID", node.windowId.toString())
        if (window != null) {
            windowRows.textRow("窗口标题", safe { window.title?.toString() })
            windowRows.valueRow("窗口类型", windowTypeName(window.type))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowRows.valueRow("窗口层级", safe { window.layer.toString() } ?: "—")
            }
            val windowBounds = Rect().also { window.getBoundsInScreen(it) }
            windowRows.valueRow("窗口边界", formatRect(windowBounds))
        }
        groups.add(PropertyGroup("窗口", windowRows))

        val nodeBounds = boundsOf(node)
        val layout = ArrayList<PropertyRow>(14)
        layout.valueRow("屏幕边界", formatRect(nodeBounds))
        layout.valueRow("尺寸", "${nodeBounds.width()} × ${nodeBounds.height()}")
        layout.flagRow("可见", safe { node.isVisibleToUser } ?: false)
        layout.valueRow("子节点数", node.childCount.toString())
        layout.valueRow("绘制顺序", safe { node.drawingOrder.toString() } ?: "—")
        layout.valueRow("层级深度", depth.toString())
        layout.flagRow("重要节点", safe { node.isImportantForAccessibility } ?: false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layout.flagRow("屏幕阅读器焦点", safe { node.isScreenReaderFocusable } ?: false)
        }
        layout.flagRow("无障碍焦点", safe { node.isAccessibilityFocused } ?: false)

        val collection = safe { node.collectionInfo }
        if (collection != null) {
            layout.valueRow("集合行数", collection.rowCount.toString())
            layout.valueRow("集合列数", collection.columnCount.toString())
            layout.flagRow("集合分层", collection.isHierarchical)
            layout.valueRow("集合选择模式", collection.selectionMode.toString())
        }
        val collectionItem = safe { node.collectionItemInfo }
        if (collectionItem != null) {
            layout.valueRow("行 / 列", "${collectionItem.rowIndex} / ${collectionItem.columnIndex}")
            layout.valueRow("跨行 / 跨列", "${collectionItem.rowSpan} / ${collectionItem.columnSpan}")
            layout.flagRow("集合项标题", collectionItem.isHeading)
            layout.flagRow("集合项选中", collectionItem.isSelected)
        }
        val range = safe { node.rangeInfo }
        if (range != null) {
            layout.valueRow("范围当前值", formatFloat(range.current))
            layout.valueRow("范围最小值", formatFloat(range.min))
            layout.valueRow("范围最大值", formatFloat(range.max))
            layout.valueRow("范围类型", rangeTypeName(range.type))
        }
        groups.add(PropertyGroup("布局", layout))

        val state = ArrayList<PropertyRow>(30)
        state.flagRow("可点击", node.isClickable)
        state.flagRow("可长按", node.isLongClickable)
        state.flagRow("可上下文点击", safe { node.isContextClickable } ?: false)
        state.flagRow("可聚焦", node.isFocusable)
        state.flagRow("已聚焦", node.isFocused)
        state.flagRow("可用", node.isEnabled)
        state.flagRow("已选中", node.isSelected)
        state.flagRow("可勾选", node.isCheckable)
        state.flagRow("已勾选", safe { node.isChecked } ?: false)
        state.flagRow("可滚动", node.isScrollable)
        state.flagRow("可编辑", node.isEditable)
        state.flagRow("密码", node.isPassword)
        state.flagRow("多行", node.isMultiLine)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            state.flagRow("文本可选", safe { node.isTextSelectable } ?: false)
        }
        if (Build.VERSION.SDK_INT >= 36) {
            state.flagRow("必须填写", safe { node.isFieldRequired } ?: false)
        }
        state.flagRow("可关闭", node.isDismissable)
        state.flagRow("内容无效", node.isContentInvalid)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            state.flagRow("标题", safe { node.isHeading } ?: false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            state.flagRow("文本输入键", safe { node.isTextEntryKey } ?: false)
        }
        if (Build.VERSION.SDK_INT >= 35) {
            state.flagRow("支持粒度滚动", safe { node.isGranularScrollingSupported } ?: false)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            state.flagRow("无障碍数据敏感", safe { node.isAccessibilityDataSensitive } ?: false)
        }
        state.flagRow("显示提示文本", safe { node.isShowingHintText } ?: false)
        if (Build.VERSION.SDK_INT >= 36) {
            state.valueRow("展开状态", expandedStateName(safe { node.expandedState } ?: 0))
        }
        val selectionStart = safe { node.textSelectionStart } ?: -1
        val selectionEnd = safe { node.textSelectionEnd } ?: -1
        if (selectionStart >= 0 || selectionEnd >= 0) {
            state.valueRow("文本选择", "$selectionStart – $selectionEnd")
        }
        val maxTextLength = safe { node.maxTextLength } ?: -1
        if (maxTextLength >= 0) {
            state.valueRow("最大文本长度", maxTextLength.toString())
        }
        val inputType = safe { node.inputType } ?: 0
        if (inputType != 0) {
            state.valueRow("输入类型", inputTypeName(inputType))
        }
        val liveRegion = safe { node.liveRegion } ?: 0
        if (liveRegion != 0) {
            state.valueRow("实时区域", liveRegionName(liveRegion))
        }
        val granularities = safe { node.movementGranularities } ?: 0
        if (granularities != 0) {
            state.valueRow("移动粒度", granularitiesName(granularities))
        }
        groups.add(PropertyGroup("状态", state))

        val actions = ArrayList<PropertyRow>(2)
        val actionList = safe { node.actionList }.orEmpty()
        if (actionList.isNotEmpty()) {
            val names = actionList.map { action ->
                actionNames[action.id]
                    ?: action.label?.toString()?.takeIf { it.isNotBlank() }
                    ?: "ID ${action.id}"
            }.distinct()
            actions.valueRow("支持的操作", names.joinToString(" · "))
        }
        val extraKeys = safe { node.availableExtraData }.orEmpty()
        if (extraKeys.isNotEmpty()) {
            actions.valueRow("额外数据键", extraKeys.joinToString(", "))
        }
        if (actions.isNotEmpty()) groups.add(PropertyGroup("操作", actions))

        val extrasRows = buildExtrasRows(node)
        if (extrasRows.isNotEmpty()) groups.add(PropertyGroup("附加数据", extrasRows))

        return groups
    }

    private fun buildExtrasRows(node: AccessibilityNodeInfo): List<PropertyRow> {
        val extras = safe { node.extras } ?: return emptyList()
        val keys = extras.keySet().sorted()
        if (keys.isEmpty()) return emptyList()

        val rows = ArrayList<PropertyRow>(keys.size)
        keys.take(MAX_EXTRAS).forEach { key ->
            val value = safe { extras.get(key) }?.toString()?.take(MAX_EXTRA_VALUE_LENGTH)
            rows.add(PropertyRow(key, value ?: "—", PropertyValueKind.MUTED))
        }
        if (keys.size > MAX_EXTRAS) {
            rows.add(
                PropertyRow(
                    "…",
                    "另有 ${keys.size - MAX_EXTRAS} 项未显示",
                    PropertyValueKind.MUTED,
                )
            )
        }
        return rows
    }

    private fun resolveAppLabel(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        if (appLabelCache.containsKey(packageName)) return appLabelCache[packageName]
        val label = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
        appLabelCache[packageName] = label
        return label
    }

    private fun boundsOf(node: AccessibilityNodeInfo): Rect =
        Rect().also { node.getBoundsInScreen(it) }

    private inline fun <T> safe(block: () -> T?): T? = runCatching { block() }.getOrNull()

    private fun MutableList<PropertyRow>.textRow(label: String, value: String?) {
        val clean = value?.trim()
        if (!clean.isNullOrEmpty()) {
            add(PropertyRow(label, clean))
        }
    }

    private fun MutableList<PropertyRow>.valueRow(label: String, value: String) {
        add(PropertyRow(label, value))
    }

    private fun MutableList<PropertyRow>.flagRow(label: String, value: Boolean) {
        add(
            PropertyRow(
                label = label,
                value = if (value) "是" else "否",
                kind = if (value) PropertyValueKind.TRUE else PropertyValueKind.FALSE,
            )
        )
    }

    private fun formatRect(rect: Rect): String =
        "${rect.left}, ${rect.top} – ${rect.right}, ${rect.bottom} · ${rect.width()}×${rect.height()}"

    private fun formatFloat(value: Float): String =
        if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()

    private fun windowTypeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "应用窗口"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "输入法窗口"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "系统窗口"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "无障碍遮罩"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "分屏分隔条"
        AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> "放大镜遮罩"
        else -> "未知 ($type)"
    }

    private fun rangeTypeName(type: Int): String = when (type) {
        AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT -> "整数"
        AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT -> "浮点数"
        AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT -> "百分比"
        else -> "未知 ($type)"
    }

    private fun expandedStateName(state: Int): String = when (state) {
        AccessibilityNodeInfo.EXPANDED_STATE_FULL -> "完全展开"
        AccessibilityNodeInfo.EXPANDED_STATE_COLLAPSED -> "已折叠"
        AccessibilityNodeInfo.EXPANDED_STATE_PARTIAL -> "部分展开"
        else -> "未定义"
    }

    private fun liveRegionName(region: Int): String = when (region) {
        1 -> "礼貌"
        2 -> "强提醒"
        else -> "无 ($region)"
    }

    private fun granularitiesName(granularities: Int): String {
        val names = ArrayList<String>()
        if (granularities and AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER != 0) names.add("字符")
        if (granularities and AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD != 0) names.add("单词")
        if (granularities and AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE != 0) names.add("行")
        if (granularities and AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH != 0) names.add("段落")
        if (granularities and AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE != 0) names.add("页")
        return if (names.isEmpty()) "0x${Integer.toHexString(granularities)}" else names.joinToString(" · ")
    }

    private fun inputTypeName(inputType: Int): String {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val base = when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> "文本"
            InputType.TYPE_CLASS_NUMBER -> "数字"
            InputType.TYPE_CLASS_PHONE -> "电话"
            InputType.TYPE_CLASS_DATETIME -> "日期时间"
            else -> "类别 0x${Integer.toHexString(inputClass)}"
        }

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val flags = ArrayList<String>()
        if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) flags.add("多行")
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) flags.add("无联想")
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) flags.add("全大写")
        if (inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0) flags.add("单词大写")
        when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_NUMBER_VARIATION_PASSWORD -> flags.add("密码")
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> flags.add("邮箱")
            InputType.TYPE_TEXT_VARIATION_URI -> flags.add("URI")
        }

        return buildString {
            append(base)
            if (flags.isNotEmpty()) {
                append(" · ")
                append(flags.joinToString(" · "))
            }
            append(" (0x")
            append(Integer.toHexString(inputType))
            append(')')
        }
    }

    private companion object {
        const val MAX_CHAIN_LENGTH = 120
        const val MAX_TREE_NODES = 2500
        const val MAX_TREE_DEPTH = 80
        const val MAX_EXTRAS = 20
        const val MAX_EXTRA_VALUE_LENGTH = 400

        val actionNames: Map<Int, String> = mapOf(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_FOCUS.id to "聚焦",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_FOCUS.id to "清除焦点",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SELECT.id to "选择",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_SELECTION.id to "清除选择",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id to "点击",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK.id to "长按",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS.id to "无障碍聚焦",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS.id to "清除无障碍焦点",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD.id to "向前滚动",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD.id to "向后滚动",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id to "向上滚动",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id to "向下滚动",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id to "向左滚动",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id to "向右滚动",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY.id to "复制",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_PASTE.id to "粘贴",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CUT.id to "剪切",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_SELECTION.id to "设置选区",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id to "展开",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id to "折叠",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id to "关闭",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id to "设置文本",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK.id to "上下文点击",
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id to "设置进度",
        )
    }
}
