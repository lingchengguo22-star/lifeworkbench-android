package com.example.lifeworkbench.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 阶段 2.3：底部导航选中状态映射测试（纯函数，无需 Robolectric）。
 */
class NavigationTest {

    @Test
    fun homeRoute_selectsHome() {
        assertEquals(BottomTab.HOME, selectedBottomTab("home"))
    }

    @Test
    fun todayRoute_selectsToday() {
        assertEquals(BottomTab.TODAY, selectedBottomTab("today"))
    }

    @Test
    fun settingsRoute_selectsSettings() {
        assertEquals(BottomTab.SETTINGS, selectedBottomTab("settings"))
    }

    @Test
    fun moduleRoute_selectsNothing() {
        assertNull(selectedBottomTab("module/学习"))
        assertNull(selectedBottomTab("module/睡眠"))
    }

    @Test
    fun nullRoute_selectsNothing() {
        assertNull(selectedBottomTab(null))
    }

    @Test
    fun unknownRoute_selectsNothing() {
        assertNull(selectedBottomTab("unknown"))
        assertNull(selectedBottomTab(""))
    }
}
