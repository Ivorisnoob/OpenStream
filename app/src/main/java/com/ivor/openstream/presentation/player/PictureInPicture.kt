package com.ivor.openstream.presentation.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.ivor.openstream.R
import com.ivor.openstream.data.settings.PipAction

private const val ACTION_TOGGLE_PLAYBACK = "com.ivor.openstream.action.PIP_TOGGLE_PLAYBACK"
private const val ACTION_PIP_BUTTON = "com.ivor.openstream.action.PIP_BUTTON"
private const val EXTRA_PIP_BUTTON = "button"
private val VIDEO_ASPECT_RATIO = Rational(16, 9)

private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}

/** Tracks whether the activity is currently shown as a picture-in-picture window. */
@Composable
fun rememberIsInPictureInPicture(): Boolean {
    val activity = LocalContext.current.findComponentActivity()
    var isInPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> {
            isInPip = it.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return isInPip
}

/**
 * Enters picture-in-picture right away (the player's PiP button), or null when the device has no
 * picture-in-picture support.
 */
@Composable
fun rememberEnterPictureInPicture(): (() -> Unit)? {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return null
    val supported = remember(activity) {
        activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    if (!supported) return null
    return remember(activity) {
        {
            runCatching {
                activity.enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(VIDEO_ASPECT_RATIO).build()
                )
            }
        }
    }
}

/**
 * Lets playback continue in a floating window when the user leaves the app mid-video.
 * Android 12+ enters automatically; older versions enter on the user-leave hint.
 * The window carries play/pause in the middle and the two buttons chosen in Settings either side
 * (Android shows three at most), so it is useful without reopening the app.
 */
@Composable
fun PictureInPictureEffect(
    enabled: Boolean,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    leftAction: PipAction = PipAction.REWIND,
    rightAction: PipAction = PipAction.FORWARD,
    seekStepSeconds: Int = 10,
    /** False greys out "Next" (no next episode). */
    canGoNext: Boolean = false,
    onAction: (PipAction) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findComponentActivity() ?: return
    val latestToggle by rememberUpdatedState(onTogglePlayback)
    val latestAction by rememberUpdatedState(onAction)
    val canEnter = enabled && isPlaying

    val params = remember(enabled, isPlaying, leftAction, rightAction, seekStepSeconds, canGoNext) {
        val maxActions = runCatching { activity.maxNumPictureInPictureActions }.getOrDefault(1)
        val actions = if (maxActions >= 3) {
            listOf(
                buttonAction(context, leftAction, seekStepSeconds, canGoNext),
                playbackAction(context, isPlaying),
                buttonAction(context, rightAction, seekStepSeconds, canGoNext)
            )
        } else {
            listOf(playbackAction(context, isPlaying))
        }
        PictureInPictureParams.Builder()
            .setAspectRatio(VIDEO_ASPECT_RATIO)
            .setActions(actions)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(canEnter)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    DisposableEffect(params) {
        runCatching { activity.setPictureInPictureParams(params) }
        val leaveHintListener = Runnable {
            if (canEnter && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                runCatching { activity.enterPictureInPictureMode(params) }
            }
        }
        activity.addOnUserLeaveHintListener(leaveHintListener)
        onDispose { activity.removeOnUserLeaveHintListener(leaveHintListener) }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_TOGGLE_PLAYBACK -> latestToggle()
                    ACTION_PIP_BUTTON -> PipAction.entries
                        .firstOrNull { it.name == intent.getStringExtra(EXTRA_PIP_BUTTON) }
                        ?.let(latestAction)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_TOGGLE_PLAYBACK).apply { addAction(ACTION_PIP_BUTTON) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
            // Leaving the player must not leave auto-enter armed for other screens.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                    )
                }
            }
        }
    }
}

private fun playbackAction(context: Context, isPlaying: Boolean): RemoteAction {
    val intent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(ACTION_TOGGLE_PLAYBACK).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val (icon, label) = if (isPlaying) {
        R.drawable.ic_pip_pause to "Pause"
    } else {
        R.drawable.ic_pip_play to "Play"
    }
    return RemoteAction(Icon.createWithResource(context, icon), label, label, intent)
}

private fun buttonAction(context: Context, action: PipAction, seekStepSeconds: Int, canGoNext: Boolean): RemoteAction {
    val intent = PendingIntent.getBroadcast(
        context,
        // One request code per button, or the PendingIntents would collapse into one.
        1 + action.ordinal,
        Intent(ACTION_PIP_BUTTON).setPackage(context.packageName).putExtra(EXTRA_PIP_BUTTON, action.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val (icon, label) = when (action) {
        PipAction.REWIND -> R.drawable.ic_pip_rewind to "Back ${seekStepSeconds}s"
        PipAction.FORWARD -> R.drawable.ic_pip_forward to "Forward ${seekStepSeconds}s"
        PipAction.NEXT_EPISODE -> R.drawable.ic_pip_next to "Next episode"
        PipAction.SKIP_INTRO -> R.drawable.ic_pip_skip to "Skip intro"
    }
    return RemoteAction(Icon.createWithResource(context, icon), label, label, intent).apply {
        isEnabled = action != PipAction.NEXT_EPISODE || canGoNext
    }
}
