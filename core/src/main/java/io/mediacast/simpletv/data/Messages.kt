package io.mediacast.simpletv.data

/**
 * Пользовательские сообщения, которые :core отдаёт в интерфейс через обратные
 * вызовы (progress, busy, сообщения об ошибках). В :core нет доступа к ресурсам,
 * поэтому приложение подключает переводчик на строки ресурсов при старте
 * (см. installCoreMessages в :app). Без подключения возвращается имя ключа —
 * так тестам не нужен Android-контекст.
 */
object Messages {
    enum class Key {
        PLAYLIST_LOADING,
        PLAYLIST_CHANNELS,
        PLAYLIST_ERROR,
        PLAYLIST_STATUS_ERROR,
        PLAYLIST_N_OF_M,
        PLAYLIST_UNAVAILABLE,
        PLAYLIST_CHECKING,
        PLAYLIST_RECHECKING,
        RATE_MBPS,
        RATE_KBPS,
        CHANNELS_MONO,
    }

    @Volatile
    var resolve: (Key, List<Any>) -> String = { key, _ -> key.name }

    operator fun get(
        key: Key,
        vararg args: Any,
    ): String = resolve(key, args.toList())
}
