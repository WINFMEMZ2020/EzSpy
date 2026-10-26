package com.udm.ezspy.data

/**
 * How a property value should be rendered. This keeps the adapter free of
 * any knowledge about accessibility APIs.
 */
enum class PropertyValueKind {
    NORMAL,
    TRUE,
    FALSE,
    MUTED,
}

data class PropertyRow(
    val label: String,
    val value: String,
    val kind: PropertyValueKind = PropertyValueKind.NORMAL,
    /** Hide the value behind bullets until the user enables sensitive content. */
    val sensitive: Boolean = false,
)

data class PropertyGroup(
    val title: String,
    val rows: List<PropertyRow>,
)
