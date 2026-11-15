package com.example.lifeworkbench

import android.app.Application
import com.example.lifeworkbench.data.AppDatabase
import com.example.lifeworkbench.data.DietRepository
import com.example.lifeworkbench.data.FitnessRepository
import com.example.lifeworkbench.data.GameRepository
import com.example.lifeworkbench.data.ModuleSummaryRepository
import com.example.lifeworkbench.data.SleepRepository
import com.example.lifeworkbench.data.StudyRepository
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

    /** 五大业务模块只读摘要入口（阶段 2.2）。 */
    val summaryRepository: ModuleSummaryRepository by lazy {
        ModuleSummaryRepository(
            studyDao = database.studyRecordDao(),
            dietDao = database.dietRecordDao(),
            fitnessDao = database.fitnessRecordDao(),
            gameDao = database.gameRecordDao(),
            sleepDao = database.sleepRecordDao()
        )
    }

    /** 学习记录数据统一入口（阶段 3.0）。 */
    val studyRepository: StudyRepository by lazy { StudyRepository(database.studyRecordDao()) }

    /** 健身记录数据统一入口（阶段 3.0）。 */
    val fitnessRepository: FitnessRepository by lazy { FitnessRepository(database.fitnessRecordDao()) }

    /** 饮食记录数据统一入口（阶段 3.0）。 */
    val dietRepository: DietRepository by lazy { DietRepository(database.dietRecordDao()) }

    /** 游戏记录数据统一入口（阶段 3.0）。 */
    val gameRepository: GameRepository by lazy { GameRepository(database.gameRecordDao()) }

    /** 睡眠记录数据统一入口（阶段 3.0）。 */
    val sleepRepository: SleepRepository by lazy { SleepRepository(database.sleepRecordDao()) }
}
