package io.mediacast.simpletv.ui

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.mediacast.simpletv.R
import io.mediacast.simpletv.StvApp.enforceReleaseDebuggerPolicy

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var inPip by mutableStateOf(false)

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || isTelevision(this) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val player = vm.controller.player
        if (vm.controller.state.value.channel == null || !player.playWhenReady) return
        val size = player.videoSize
        val ratio = if (size.height > 0) size.width.toFloat() / size.height else 16f / 9
        val aspect = Rational((ratio.coerceIn(1f / 2.39f, 2.39f) * 1000).toInt(), 1000)
        vm.closeOverlay()
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(aspect).build())
        }.onFailure { vm.show(getString(R.string.pip_error)) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (effectiveIsTv(this, vm.settings.uiMode)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        handleIntent(intent)
        val canPip = Build.VERSION.SDK_INT >= 26 && !isTelevision(this) &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        setContent {
            StvTheme {
                StvRoot(vm, onExit = { finish() }, inPip = inPip, onPip = if (canPip) ::enterPip else null)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Открытие .m3u из браузера/файлового менеджера — добавляем как свой источник. */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data ?: return
        if (uri.scheme == "content") {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onFailure { vm.show(getString(R.string.source_error, uri.toString(), it.message ?: "")) }
        }
        vm.addSource(displayNameFor(uri), uri.toString(), null)
    }

    private fun displayNameFor(uri: android.net.Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return uri.lastPathSegment ?: ""
    }

    private var resumePlayback = false

    override fun onStop() {
        super.onStop()
        resumePlayback = !inPip && vm.controller.player.playWhenReady
        if (!inPip) vm.controller.player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (resumePlayback && vm.controller.state.value.channel != null) {
            resumePlayback = false
            vm.controller.player.play()
        }
    }

    override fun onResume() {
        super.onResume()
        enforceReleaseDebuggerPolicy()
    }

    @SuppressLint("RestrictedApi") // dispatchKeyEvent is a public Activity API despite the androidx core restriction marker.
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
            val isOverlayOpen = vm.overlay.value != Overlay.NONE
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_GUIDE -> if (effectiveIsTv(this, vm.uiMode.value)) {
                    vm.setOverlay(Overlay.EPG)
                    return true
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                    vm.zapForward()
                    return true
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    vm.zapBackward()
                    return true
                }
                android.view.KeyEvent.KEYCODE_PAGE_UP -> {
                    if (!isOverlayOpen) {
                        vm.zapForward()
                        return true
                    }
                }
                android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                    if (!isOverlayOpen) {
                        vm.zapBackward()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

internal fun isTelevision(context: Context): Boolean {
    val ui = context.getSystemService(UiModeManager::class.java)
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val hasLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        hasLeanback ||
        !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

internal fun effectiveIsTv(context: Context, uiMode: String): Boolean = when (uiMode) {
    "tv" -> true
    "phone" -> false
    else -> isTelevision(context)
}
