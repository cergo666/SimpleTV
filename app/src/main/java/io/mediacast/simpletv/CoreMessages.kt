package io.mediacast.simpletv

import android.content.Context
import io.mediacast.simpletv.data.Messages

/** Подключает перевод строк :core на ресурсы приложения. */
object CoreMessages {
    @JvmStatic
    fun install(context: Context) {
        val app = context.applicationContext
        Messages.resolve = { key, args ->
            fun string(
                res: Int,
                vararg formatArgs: Any,
            ): String = app.getString(res, *formatArgs)

            when (key) {
                Messages.Key.PLAYLIST_LOADING -> string(R.string.core_playlist_loading, args[0])
                Messages.Key.PLAYLIST_CHANNELS -> {
                    val count = (args[1] as Number).toInt()
                    app.resources.getQuantityString(R.plurals.core_playlist_channels, count, args[0], count)
                }
                Messages.Key.PLAYLIST_ERROR -> string(R.string.core_playlist_error, args[0], args[1])
                Messages.Key.PLAYLIST_STATUS_ERROR -> string(R.string.core_playlist_status_error)
                Messages.Key.PLAYLIST_N_OF_M -> string(R.string.core_playlist_n_of_m, args[0], args[1], args[2])
                Messages.Key.PLAYLIST_UNAVAILABLE -> string(R.string.core_playlist_unavailable)
                Messages.Key.PLAYLIST_CHECKING -> {
                    val working = (args[3] as Number).toInt()
                    app.resources.getQuantityString(R.plurals.core_playlist_checking, working, args[0], args[1], args[2], working)
                }
                Messages.Key.PLAYLIST_RECHECKING -> {
                    val working = (args[3] as Number).toInt()
                    app.resources.getQuantityString(R.plurals.core_playlist_rechecking, working, args[0], args[1], args[2], working)
                }
                Messages.Key.RATE_MBPS -> string(R.string.core_rate_mbps, args[0])
                Messages.Key.RATE_KBPS -> string(R.string.core_rate_kbps, args[0])
                Messages.Key.CHANNELS_MONO -> string(R.string.core_channels_mono)
            }
        }
    }
}
