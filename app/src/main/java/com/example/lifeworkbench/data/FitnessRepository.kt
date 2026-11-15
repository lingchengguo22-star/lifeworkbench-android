package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.FitnessEntity
import com.example.lifeworkbench.data.entities.FitnessRecordDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException

/**
 * 健身记录数据访问统一入口（V1.1 阶段 3.0）。
 * UI 与 ViewModel 不直接操作 DAO，一律走 Repository。
 * 3.0 不做业务校验（FitnessValidator 在 3.2 建立，届时区分力量/有氧/拉伸的字段规则）；
 * 写操作只负责落库与错误处理。
 * 更新/删除按实际影响行数判定：> 0 成功，= 0 返回 NotFound。
 * 异常处理原则：CancellationException 必须原样向上抛出（不得包装为数据库错误）；
 * 普通异常转为 Failure(Database)；不吞异常。
 * Room Flow 的响应式特征保持不变：按日期与全部查询直接透传 DAO 的 Flow。
 */
class FitnessRepository(private val dao: FitnessRecordDao) {

    /** 创建健身记录，返回新记录的行 ID。 */
    suspend fun createRecord(record: FitnessEntity): RepoResult<Long> {
        return try {
            RepoResult.Success(dao.insertRecord(record))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Database(e))
        }
    }

    /** 按 ID 获取健身记录，查不到返回 NotFound。 */
    suspend fun getRecordById(id: Int): RepoResult<FitnessEntity> {
        return try {
            dao.getRecordById(id)?.let { RepoResult.Success(it) }
                ?: RepoResult.Failure(RepoError.NotFound(id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Database(e))
        }
    }

    /** 查询全部健身记录（实时 Flow，日期倒序、同日按 id 倒序）。 */
    fun getAllRecords(): Flow<List<FitnessEntity>> = dao.getAllRecords()

    /** 查询指定日期的健身记录（实时 Flow，按 id 升序）。 */
    fun getRecordsByDate(date: String): Flow<List<FitnessEntity>> = dao.getRecordsByDate(date)

    /**
     * 更新健身记录：先读原记录——不存在返回 NotFound；
     * 写入后按实际影响行数判定：> 0 成功，= 0 返回 NotFound。
     */
    suspend fun updateRecord(record: FitnessEntity): RepoResult<Unit> {
        return try {
            val existing = dao.getRecordById(record.id)
            if (existing == null) {
                RepoResult.Failure(RepoError.NotFound(record.id))
            } else {
                val rows = dao.updateRecord(record)
                if (rows > 0) RepoResult.Success(Unit)
                else RepoResult.Failure(RepoError.NotFound(record.id))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Database(e))
        }
    }

    /** 删除健身记录（按实体）：影响行数 > 0 成功，= 0 返回 NotFound。 */
    suspend fun deleteRecord(record: FitnessEntity): RepoResult<Unit> {
        return try {
            val rows = dao.deleteRecord(record)
            if (rows > 0) RepoResult.Success(Unit)
            else RepoResult.Failure(RepoError.NotFound(record.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Database(e))
        }
    }

    /** 删除健身记录（按 ID）：影响行数 > 0 成功，= 0 返回 NotFound。 */
    suspend fun deleteRecordById(id: Int): RepoResult<Unit> {
        return try {
            val rows = dao.deleteRecordById(id)
            if (rows > 0) RepoResult.Success(Unit)
            else RepoResult.Failure(RepoError.NotFound(id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Database(e))
        }
    }
}
