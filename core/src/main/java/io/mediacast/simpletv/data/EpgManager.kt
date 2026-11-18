package io.mediacast.simpletv.data

interface EpgManager {
    fun interface Progress {
        fun report(text: String)
    }

    val isEmpty: Boolean

    fun epgIdFor(ch: Channel): String?

    fun iconFor(ch: Channel): String?

    fun nowNext(
        ch: Channel,
        now: Long = System.currentTimeMillis(),
    ): List<Programme>

    fun schedule(
        ch: Channel,
        from: Long,
        to: Long,
    ): List<Programme>

    suspend fun update(
        force: Boolean,
        channels: List<Channel>,
        progress: Progress,
    ): Boolean

    suspend fun relink(
        channels: List<Channel>,
        progress: Progress,
    ): Boolean

    fun clear()

    fun needsRelink(channels: List<Channel>): Boolean
}
