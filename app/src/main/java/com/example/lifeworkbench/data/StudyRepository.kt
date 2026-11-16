package com.example.lifeworkbench.data

import com.example.lifeworkbench.data.entities.StudyRecordDao
import com.example.lifeworkbench.data.entities.StudyRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException

/**
 * 学习记录数据访问统一入口（V1.1 阶段 3.0；3.1 接入 StudyValidator）。
 * UI 与 ViewModel 不直接操作 DAO，一律走 Repository。
 * 创建/更新前先跑 [StudyValidator]，校验不通过不写入数据库；
 * 内容先去首尾空格再校验，保存的也是去空格后的内容（与 TaskRepository 的标题处理一致）。
 * 更新/删除按实际影响行数判定：> 0 成功，= 0 返回 NotFound。
 * 异常处理原则：CancellationException 必须原样向上抛出（不得包装为数据库错误）；
 * 普通异常转为 Failure(Database)；不吞异常。
 * Room Flow 的响应式特征保持不变：按日期与全部查询直接透传 DAO 的 Flow。
 */
class StudyRepository(private val dao: StudyRecordDao) {

    /** 创建学习记录：先 trim 内容，再校验，通过后保存 trim 后的内容，返回新记录的行 ID。 */
    suspend fun createRecord(record: StudyRecordEntity): RepoResult<Long> {
        val normalized = record.copy(content = record.content.trim())
        val errors = StudyValidator.validate(normalized)
        if (errors.isNotEmpty()) {
            return RepoResult.Failure(RepoError.ValidationFailed(errors.map { it.message }))
        }
        return try {
            RepoResult.Success(dao.insertRecord(normalized))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Database(e))
        }
    }

    /** 按 ID 获取学习记录，查不到返回 NotFound。 */
    suspend fun getRecordById(id: Int): RepoResult<StudyRecordEntity> {
        return try {
            dao.getRecordById(id)?.let { RepoResult.Success(it) }
                ?: RepoResult.Failure(RepoError.NotFound(id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Database(e))
        }
    }

    /** 查询全部学习记录（实时 Flow，日期倒序、同日按 id 倒序）。 */
    fun getAllRecords(): Flow<List<StudyRecordEntity>> = dao.getAllRecords()

    /** 查询指定日期的学习记录（实时 Flow，按 id 升序）。 */
    fun getRecordsByDate(date: String): Flow<List<StudyRecordEntity>> = dao.getRecordsByDate(date)

    /**
     * 更新学习记录：先读原记录——不存在返回 NotFound；
     * 再 trim 内容并按新建同样校验，不通过不写入；
     * 写入后按实际影响行数判定：> 0 成功，= 0 返回 NotFound。
     */
    suspend fun updateRecord(record: StudyRecordEntity): RepoResult<Unit> {
        val normalized = record.copy(content = record.content.trim())
        return try {
            val existing = dao.getRecordById(normalized.id)
            if (existing == null) {
                RepoResult.Failure(RepoError.NotFound(normalized.id))
            } else {
                val errors = StudyValidator.validate(normalized)
                if (errors.isNotEmpty()) {
                    RepoResult.Failure(RepoError.ValidationFailed(errors.map { it.message }))
                } else {
                    val rows = dao.updateRecord(normalized)
                    if (rows > 0) RepoResult.Success(Unit)
                    else RepoResult.Failure(RepoError.NotFound(normalized.id))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RepoResult.Failure(RepoError.Database(e))
        }
    }

    /** 删除学习记录（按实体）：影响行数 > 0 成功，= 0 返回 NotFound。 */
    suspend fun deleteRecord(record: StudyRecordEntity): RepoResult<Unit> {
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

    /** 删除学习记录（按 ID）：影响行数 > 0 成功，= 0 返回 NotFound。 */
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
