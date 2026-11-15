package com.example.lifeworkbench

import android.app.Application
import com.example.lifeworkbench.data.AppDatabase
import com.example.lifeworkbench.data.TaskRepository

/**
 * 应用入口（V1.1 阶段 2.0：数据库生产环境接入）。
 *
 * 持有全应用唯一的 Room 数据库实例与 Repository，各页面共享同一实例，
 * 不允许任何页面各自创建独立数据库实例。
 *
 * 依赖提供方式：Application 持有单例，ViewModel 通过 Factory 接收 Repository
 *（Factory 见各 ViewModel 的伴生对象），不引入 Dagger/Hilt 等新框架。
 */
class LifeWorkbenchApp : Application() {

    /** 全应用唯一的数据库实例（V2，含 V1 → V2 Migration，禁止 destructive migration）。 */
    val database: AppDatabase by lazy { AppDatabase.build(this) }

    /** 任务数据统一入口。 */
    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
}
