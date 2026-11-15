package com.example.lifeworkbench.ui.navigation

/**
 * 底部导航栏的三个固定入口。
 * 模块占位页（module/{name}）不属于底部导航，不对应任何一项。
 */
enum class BottomTab { HOME, TODAY, SETTINGS }

/**
 * 根据当前导航路由返回应高亮的底部导航项。
 * 纯函数，便于单元测试；Compose 层通过 navController.currentBackStackEntryAsState() 取得 route 后调用。
 * - "home" -> HOME；"today" -> TODAY；"settings" -> SETTINGS
 * - "module/..."、null 或其他未知路由 -> null（不选中任何一项）
 */
fun selectedBottomTab(route: String?): BottomTab? = when (route) {
    "home" -> BottomTab.HOME
    "today" -> BottomTab.TODAY
    "settings" -> BottomTab.SETTINGS
    else -> null
}
