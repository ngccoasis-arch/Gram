@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.gram.client.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.gram.core.media.Direction
import com.gram.core.media.PlayerGestureMath
import com.gram.core.media.RepositoryFileReader
import com.gram.core.media.TdlibMediaDataSource
import com.gram.core.tdlib.GramBackend
import com.gram.core.tdlib.MediaRef
import com.gram.core.tdlib.SessionFactory
import kotlinx.coroutines.delay
import java.io.File

private sealed interface GestureOverlay {
    data object None : GestureOverlay
    data class Seek(val direction: Direction, val targetMs: Long) : GestureOverlay
    data class Forward(val rate: Float) : GestureOverlay
    data class Rewind(val rate: Float, val targetMs: Long) : GestureOverlay
}

@Composable
fun GramVideoPlayer(media: MediaRef, backend: GramBackend) {
    val context = LocalContext.current
    val player = remember(media.fileId) {
        val sourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(TdlibMediaDataSource.Factory(RepositoryFileReader(backend, backend)))
        ExoPlayer.Builder(context).setMediaSourceFactory(sourceFactory).build()
    }
    var controls by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var overlay by remember { mutableStateOf<GestureOverlay>(GestureOverlay.None) }
    val duration = (player.duration.takeIf { it > 0 } ?: media.durationMs ?: 0L).coerceAtLeast(1L)

    DisposableEffect(player, media.localPath) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        media.localPath?.let { path ->
            val uri = if (path.startsWith("content:") || path.startsWith("http")) Uri.parse(path) else Uri.fromFile(File(path))
            player.setMediaItem(MediaItem.fromUri(uri)); player.prepare()
        }
        if (media.localPath == null && !SessionFactory.isDemo) {
            player.setMediaItem(MediaItem.fromUri("gram-tdlib://file/${media.fileId}")); player.prepare()
        }
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(player) {
        while (true) { position = player.currentPosition.coerceAtLeast(0); delay(100) }
    }
    LaunchedEffect(overlay) {
        val rewind = overlay as? GestureOverlay.Rewind ?: return@LaunchedEffect
        while (true) {
            val target = (player.currentPosition - PlayerGestureMath.rewindStepMs(rewind.rate, 100)).coerceAtLeast(0)
            player.seekTo(target); position = target; overlay = rewind.copy(targetMs = target); delay(100)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(media.fileId) {
            detectTapGestures(
                onTap = { if (it.x in size.width / 3f..size.width * 2f / 3f) controls = !controls },
                onDoubleTap = { tap ->
                    val direction = if (tap.x < size.width / 2f) Direction.BACKWARD else Direction.FORWARD
                    val target = PlayerGestureMath.exactSeek(player.currentPosition, duration, direction)
                    player.seekTo(target); position = target; overlay = GestureOverlay.Seek(direction, target)
                },
            )
        }
        .pointerInput(media.fileId) {
            var rightSide = true
            var startX = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    startX = offset.x; rightSide = offset.x >= size.width / 2f
                    if (rightSide) { player.playbackParameters = PlaybackParameters(2f); player.play(); overlay = GestureOverlay.Forward(2f) }
                    else { player.pause(); overlay = GestureOverlay.Rewind(2f, player.currentPosition) }
                },
                onDrag = { change, _ ->
                    val rate = PlayerGestureMath.holdRate(change.position.x - startX, size.width.toFloat())
                    if (rightSide) { player.playbackParameters = PlaybackParameters(rate); overlay = GestureOverlay.Forward(player.playbackParameters.speed) }
                    else overlay = GestureOverlay.Rewind(rate, player.currentPosition)
                },
                onDragEnd = { player.playbackParameters = PlaybackParameters(1f); player.play(); overlay = GestureOverlay.None },
                onDragCancel = { player.playbackParameters = PlaybackParameters(1f); player.play(); overlay = GestureOverlay.None },
            )
        }) {
        AndroidView(factory = { PlayerView(it).apply { useController = false; this.player = player } }, modifier = Modifier.fillMaxSize())

        if (media.localPath == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayCircle, null, Modifier.size(86.dp), Color.White)
                Text("Demo video ${media.fileId}", color = Color.White)
                Text("The TDLib range source supplies frames in production", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }

        val feedback = overlayText(overlay, position)
        if (feedback != null) Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(alpha = .65f), shape = MaterialTheme.shapes.large) {
            Text(feedback, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        if (controls) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .55f)).navigationBarsPadding().padding(12.dp)) {
            Slider(value = position.coerceAtMost(duration).toFloat(), onValueChange = { position = it.toLong(); player.seekTo(position) }, valueRange = 0f..duration.toFloat())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ if (playing) player.pause() else player.play() }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.White) }
                Text("${clock(position)} / −${clock((duration - position).coerceAtLeast(0))}", color = Color.White, modifier = Modifier.weight(1f))
                IconButton({ val next = !muted; muted = next; player.volume = if (next) 0f else 1f }) { Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Mute", tint = Color.White) }
                IconButton({ context.activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }) { Icon(Icons.Default.Fullscreen, "Fullscreen", tint = Color.White) }
            }
        }
    }
}

private fun overlayText(overlay: GestureOverlay, position: Long): String? = when (overlay) {
    GestureOverlay.None -> null
    is GestureOverlay.Seek -> "${if (overlay.direction == Direction.FORWARD) "+5s" else "−5s"}  ${clock(overlay.targetMs)}"
    is GestureOverlay.Forward -> "»  %.1f×  %s".format(overlay.rate, clock(position))
    is GestureOverlay.Rewind -> "«  %.1f×  %s".format(overlay.rate, clock(overlay.targetMs))
}

private fun clock(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
