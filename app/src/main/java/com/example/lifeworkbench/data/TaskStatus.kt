package com.example.lifeworkbench.data

/**
 * 任务状态兼容性定义（V1.1 阶段 0）。
 *
 * 背景（以真实源码核查为准，见 V1.1_TaskStatus_兼容性设计.md）：
 * - 数据库 `tasks.status` 列是纯 String，Room 直接存中文文本，没有枚举、没有 TypeConverter、
 *   没有常量定义，状态值只出现在 TaskEntity.kt 的注释里。
 * - 全仓库只有一处 SQL（TaskDao.getTasksByDate，按 planDate 查），没有任何 SQL 按 status 过滤；
 *   没有任何代码读写 status，界面也不读数据库。
 * - 仓库中不存在任何历史数据文件，也不存在写入 status 的代码，因此“实际使用的状态值”
 *   只能以代码注释定义的 4 个值为准，未发现第 5 种状态值。
 *
 * 设计原则：
 * 1. 数据库不动：status 列继续存中文字符串，不改英文、不改表结构、不升级数据库版本。
 * 2. 代码里只用英文常量名（Pending / InProgress / Completed / Cancelled），数据库值仍是中文。
 * 3. 未知状态绝不静默转换：任何不在已知 4 个值之内的数据库值（含 null、空串、带空格、
 *   英文、错别字）都原样保留在 [Unknown] 里，界面明确显示“未知状态（原值）”，
 *   统计时单独计数、计入完成率分母，写回时原值不变。
 * 4. 严格匹配：转换时不做 trim、不做大小写折叠、不做模糊匹配，避免把错误数据“修”成别的状态。
 */
sealed class TaskStatus {

    /** 数据库中存储的值。已知状态为中文字符串；[Unknown] 原样保留读到的值（可能为 null）。 */
    abstract val dbValue: String?

    /** 界面显示文案。已知状态与数据库值相同；未知状态显示“未知状态（原值）”。 */
    abstract val displayName: String

    /** 是否为已知 4 种状态之一。 */
    val isKnown: Boolean get() = this !is Unknown

    /** 待开始 */
    object Pending : TaskStatus() {
        override val dbValue: String = "待开始"
        override val displayName: String = "待开始"
    }

    /** 进行中 */
    object InProgress : TaskStatus() {
        override val dbValue: String = "进行中"
        override val displayName: String = "进行中"
    }

    /** 已完成 */
    object Completed : TaskStatus() {
        override val dbValue: String = "已完成"
        override val displayName: String = "已完成"
    }

    /** 已取消 */
    object Cancelled : TaskStatus() {
        override val dbValue: String = "已取消"
        override val displayName: String = "已取消"
    }

    /**
     * 未知状态：数据库里读到的值不在已知 4 个值之内。
     * [rawValue] 原样保留，绝不改写；[toDbValue] 写回时返回同一个值，保证历史数据不丢失。
     */
    data class Unknown(val rawValue: String?) : TaskStatus() {
        override val dbValue: String? = rawValue
        override val displayName: String =
            if (rawValue.isNullOrEmpty()) "未知状态（空）" else "未知状态（$rawValue）"
    }

    /** 写回数据库的值。已知状态返回中文字符串；未知状态返回读到的原值（可能为 null）。 */
    fun toDbValue(): String? = dbValue

    companion object {
        /**
         * 已知 4 种状态，按 TaskEntity 注释定义的顺序。
         *
         * 注意：必须用 get() 每次重新求值，不能写成初始化时求值的 val。
         * 原因（2026-11-14 CI 定位）：KNOWN 在伴生对象初始化时求值会与嵌套 object
         * 产生循环初始化——若首次访问的是 Completed，伴生对象初始化会先跑到
         * listOf(Pending, InProgress, Completed, Cancelled)，此时 Completed
         * 的类初始化尚未完成，其 INSTANCE 字段还是 null，KNOWN 就会把 null
         * 当成第三个元素装进去；后续 `for (status in KNOWN)` 读到 null 再调
         * toDbValue() 就会报 NullPointerException（第 10/11/12 次 CI 均因此失败）。
         * get() 在调用时才求值，此时四个 object 均已初始化完成，不会再拿到 null。
         */
        val KNOWN: List<TaskStatus>
            get() = listOf(Pending, InProgress, Completed, Cancelled)

        /**
         * 数据库值 → 状态。严格按字符串全等匹配，不做任何归一化。
         * null、空串、带空格、英文、错别字等一律返回 [Unknown]，绝不默认成待开始。
         */
        fun fromDbValue(value: String?): TaskStatus = when (value) {
            Pending.dbValue -> Pending
            InProgress.dbValue -> InProgress
            Completed.dbValue -> Completed
            Cancelled.dbValue -> Cancelled
            else -> Unknown(value)
        }

        /**
         * 生成按已知状态过滤的 SQL 片段，保证查询用的就是数据库里实际存的中文值，
         * 而不是手写字符串拼出来的值。示例：
         * `status IN ('待开始', '进行中', '已完成', '已取消')`
         */
        fun knownStatusSqlInClause(column: String = "status"): String =
            KNOWN.joinToString(prefix = "$column IN (", postfix = ")") { "'${it.dbValue}'" }
    }
}
