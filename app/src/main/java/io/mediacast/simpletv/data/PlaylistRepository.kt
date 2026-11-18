package io.mediacast.simpletv.data

import android.content.Context
import androidx.core.net.toUri
import io.mediacast.simpletv.BuildConfig
import io.mediacast.simpletv.protection.StringVault

class AndroidPlaylistRepository(context: Context, settings: Settings) : PlaylistRepository(
    dataDir = context.filesDir,
    settings = settings,
    contentStreamResolver = { uri -> runCatching { context.contentResolver.openInputStream(uri.toUri()) }.getOrNull() },
    assetStreamResolver = { path ->
        runCatching {
            if (BuildConfig.DEBUG) context.assets.open(path)
            else context.assets.open("$path.stv").bufferedReader().use { reader ->
                StringVault.unpack(reader.readText()).inputStream()
            }
        }.getOrNull()
    }
)
