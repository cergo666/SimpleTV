package io.mediacast.simpletv.data

import android.content.Context
import io.mediacast.simpletv.R

/**
 * Display name for a built-in EPG source. The stored [EpgSource.name] is
 * Russian data that must stay stable for cached settings, so the UI-facing
 * name is resolved from resources by source id.
 */
fun Context.epgSourceLabel(source: EpgSource): String = when (source.id) {
    "iptvx_lite" -> getString(R.string.epgsrc_iptvx_lite)
    "epgpw" -> getString(R.string.epgsrc_epgpw)
    "programtv" -> getString(R.string.epgsrc_programtv)
    "iptvx" -> getString(R.string.epgsrc_iptvx)
    "edem" -> getString(R.string.epgsrc_edem)
    "gabba" -> getString(R.string.epgsrc_gabba)
    "glanz" -> getString(R.string.epgsrc_glanz)
    else -> source.name
}
