package cz.autoskola.app.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import cz.autoskola.app.R
import cz.autoskola.domain.MediaReference
import java.io.File

internal enum class LocalMediaKind { IMAGE, ANIMATED_IMAGE, VIDEO, UNSUPPORTED }

internal fun localMediaKind(mimeType: String): LocalMediaKind = when {
    mimeType == "image/gif" -> LocalMediaKind.ANIMATED_IMAGE
    mimeType.startsWith("image/") -> LocalMediaKind.IMAGE
    mimeType == "video/mp4" -> LocalMediaKind.VIDEO
    else -> LocalMediaKind.UNSUPPORTED
}

private fun resolveLocalMediaFile(root: File, media: MediaReference): File? = runCatching {
    val canonicalRoot = root.canonicalFile
    val file = File(canonicalRoot, media.path).canonicalFile
    file.takeIf {
        it.isFile &&
            it.path.startsWith(canonicalRoot.path + File.separator)
    }
}.getOrNull()

/**
 * Renders checksum-verified content that was already imported into local app storage.
 * No remote media fetching happens here.
 *
 * Static images and GIFs are decoded from the local file by Coil. MP4 is played by
 * Media3 ExoPlayer with user controls and never auto-starts.
 */
@Composable
fun LocalMedia(media: MediaReference) {
    val context = LocalContext.current
    val root = remember(context.filesDir) { File(context.filesDir, "content") }
    val file = remember(root, media.path) { resolveLocalMediaFile(root, media) }

    if (file == null) {
        Note(text(R.string.lesson_media_pending))
        return
    }

    when (localMediaKind(media.mimeType)) {
        LocalMediaKind.IMAGE,
        LocalMediaKind.ANIMATED_IMAGE -> {
            AsyncImage(
                model = file,
                contentDescription = text(R.string.lesson_situation),
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }

        LocalMediaKind.VIDEO -> LocalVideo(file)

        LocalMediaKind.UNSUPPORTED -> Note(text(R.string.lesson_media_pending))
    }
}

@Composable
private fun LocalVideo(file: File) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(file.path) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = true
                controllerAutoShow = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                keepContentOnPlayerReset = true
                this.player = player
            }
        },
        update = { it.player = player }
    )
}
