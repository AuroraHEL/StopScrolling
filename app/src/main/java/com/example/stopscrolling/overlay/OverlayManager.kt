package com.example.stopscrolling.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

private const val TAG = "OverlayManager"

/**
 * Shows a Compose screen on top of every app, from a Service (no Activity needed).
 * All functions must be called on the main thread.
 */
class OverlayManager(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var host: OverlayHost? = null

    /** True if the user granted "Display over other apps". */
    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(appContext)

    /** Shows [content] full screen. Returns false if it could not be shown. */
    fun show(content: @Composable () -> Unit): Boolean {
        if (host != null) return true
        if (!canDrawOverlays()) return false

        val newHost = OverlayHost(appContext)
        newHost.setContent(content)
        return try {
            windowManager.addView(newHost, createLayoutParams())
            newHost.start()
            host = newHost
            true
        } catch (e: RuntimeException) { // BadTokenException, SecurityException...
            Log.e(TAG, "Cannot show the overlay", e)
            newHost.destroy()
            false
        }
    }

    /** Removes the overlay. Safe to call when nothing is shown. */
    fun hide() {
        val current = host ?: return
        host = null
        try {
            windowManager.removeViewImmediate(current)
        } catch (e: RuntimeException) {
            Log.w(TAG, "The overlay was already removed", e)
        }
        current.destroy()
    }

    @Suppress("DEPRECATION")
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }
}

/**
 * Root view of the overlay window. A window made by a Service has no Activity,
 * so this view is its own lifecycle, saved-state and view-model owner for Compose.
 */
internal class OverlayHost(context: Context) :
    FrameLayout(context), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val composeView = ComposeView(context)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        setViewTreeLifecycleOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        setViewTreeViewModelStoreOwner(this)
        addView(composeView)
    }

    fun setContent(content: @Composable () -> Unit) = composeView.setContent(content)

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}