package com.example.lifeworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lifeworkbench.ui.navigation.BottomTab
import com.example.lifeworkbench.ui.navigation.selectedBottomTab
import com.example.lifeworkbench.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    // 阶段 2.3：选中状态由当前路由派生，不再写死；模块占位页不选中任何一项。
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val selectedTab = selectedBottomTab(navBackStackEntry?.destination?.route)
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == BottomTab.HOME,
                    onClick = { navController.navigateBottomTab("home") },
                    icon = { Text("首页") },
                    modifier = Modifier.testTag("navHome")
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.TODAY,
                    onClick = { navController.navigateBottomTab("today") },
                    icon = { Text("今日") },
                    modifier = Modifier.testTag("navToday")
                )
                NavigationBarItem(
                    selected = selectedTab == BottomTab.SETTINGS,
                    onClick = { navController.navigateBottomTab("settings") },
                    icon = { Text("设置") },
                    modifier = Modifier.testTag("navSettings")
                )
            }
        }
    ) { paddingValues ->
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(paddingValues)) {
            composable("home") {
                HomeScreen(
                    onNavigateToToday = { navController.navigateBottomTab("today") },
                    onNavigateToModule = { name -> navController.navigate("module/$name") }
                )
            }
            composable("today") { TodayPlanScreen() }
            composable("settings") { SettingsScreen() }
            // 五大业务模块尚未开发，点击摘要卡片进入明确的占位页面（阶段 2.2）
            composable("module/{name}") { backStackEntry ->
                ModulePlaceholderScreen(
                    moduleName = backStackEntry.arguments?.getString("name") ?: ""
                )
            }
        }
    }
}

/**
 * 底部导航跳转：回到起始页再进目标页 + 单例，避免重复入栈导致返回栈失控。
 * 模块占位页用普通 navigate（它是压栈的详情页，不是底部入口）。
 */
private fun NavController.navigateBottomTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
