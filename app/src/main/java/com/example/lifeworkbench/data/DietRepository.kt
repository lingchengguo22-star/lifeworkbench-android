package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.DietEntity
import com.example.lifeworkbench.data.entities.DietRecordDao
import kotlinx.coroutines.flow.Flow

/**
 * 饮食记录数据访问统一入口（V1.1 阶段 3.0）。
 * UI 与 ViewModel 不直接操作 DAO，一律走 Repository。
 * 3.0 不做业务校验（DietValidator 在 3.3 建立，届时明确单位、小数精度与空值策略）；
 * 写操作只负责落库与错误处理。
 * 更新/删除按实际影响行数判定：> 0 成功，= 0 返回 NotFound。
 * Room Flow 的响应式特征保持不变：按日期与全部查询直接透传 DAO 的 Flow。
 */
class DietRepository(private val dao: DietRecordDao) {

    /** 创建饮食记录，返回新记录的行 ID。 */
    suspend fun createRecord(record: DietEntity): RepoResult<Long> {
        return runCatching { RepoResult.Success(dao.insertRecord(record)) }
            .getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }

    /** 按 ID 获取饮食记录，查不到返回 NotFound。 */
    suspend fun getRecordById(id: Int): RepoResult<DietEntity> {
        return runCatching {
            dao.getRecordById(id)?.let { RepoResult.Success(it) }
                ?: RepoResult.Failure(RepoError.NotFound(id))
        }.getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }

    /** 查询全部饮食记录（实时 Flow，日期倒序、同日按 id 倒序）。 */
    fun getAllRecords(): Flow<List<DietEntity>> = dao.getAllRecords()

    /** 查询指定日期的饮食记录（实时 Flow，按 id 升序）。 */
    fun getRecordsByDate(date: String): Flow<List<DietEntity>> = dao.getRecordsByDate(date)

    /**
     * 更新饮食记录：先读原记录——不存在返回 NotFound；
     * 写入后按实际影响行数判定：> 0 成功，= 0 返回 NotFound。
     */
    suspend fun updateRecord(record: DietEntity): RepoResult<Unit> {
        return runCatching {
            val existing = dao.getRecordById(record.id)
            if (existing == null) {
                RepoResult.Failure(RepoError.NotFound(record.id))
            } else {
                val rows = dao.updateRecord(record)
                if (rows > 0) RepoResult.Success(Unit)
                else RepoResult.Failure(RepoError.NotFound(record.id))
            }
        }.getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }

    /** 删除饮食记录（按实体）：影响行数 > 0 成功，= 0 返回 NotFound。 */
    suspend fun deleteRecord(record: DietEntity): RepoResult<Unit> {
        return runCatching {
            val rows = dao.deleteRecord(record)
            if (rows > 0) RepoResult.Success(Unit)
            else RepoResult.Failure(RepoError.NotFound(record.id))
        }.getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }

    /** 删除饮食记录（按 ID）：影响行数 > 0 成功，= 0 返回 NotFound。 */
    suspend fun deleteRecordById(id: Int): RepoResult<Unit> {
        return runCatching {
            val rows = dao.deleteRecordById(id)
            if (rows > 0) RepoResult.Success(Unit)
            else RepoResult.Failure(RepoError.NotFound(id))
        }.getOrElse { RepoResult.Failure(RepoError.Database(it)) }
    }
}
