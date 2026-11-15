package com.example.lifeworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = { navController.navigate("home") }, icon = { Text("首页") })
                NavigationBarItem(selected = false, onClick = { navController.navigate("today") }, icon = { Text("今日") })
                NavigationBarItem(selected = false, onClick = { navController.navigate("settings") }, icon = { Text("设置") })
            }
        }
    ) { paddingValues ->
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(paddingValues)) {
            composable("home") {
                HomeScreen(
                    onNavigateToToday = { navController.navigate("today") },
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
