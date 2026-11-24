@file:OptIn(ExperimentalMaterial3Api::class)

package com.andy.calculator

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DeviceMedia(val uri: Uri, val isVideo: Boolean, val date: Long)

private fun queryDeviceMedia(ctx: Context): List<DeviceMedia> {
    val out = ArrayList<DeviceMedia>()
    fun query(base: Uri, isVideo: Boolean) {
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        try {
            ctx.contentResolver.query(base, proj, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                while (c.moveToNext()) {
                    out.add(DeviceMedia(ContentUris.withAppendedId(base, c.getLong(0)), isVideo, c.getLong(1)))
                }
            }
        } catch (_: Exception) {
        }
    }
    query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
    out.sortByDescending { it.date }
    return out
}

/** In-app gallery so the originals can be deleted after they're moved in. */
@Composable
fun GalleryPicker(app: AppViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val perms = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var asked by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<List<DeviceMedia>?>(null) }
    val selected = remember { mutableStateListOf<Uri>() }
    val loader = remember {
        ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.crossfade(true).build()
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        Toast.makeText(
            ctx,
            if (r.resultCode == Activity.RESULT_OK) "Moved to your locked folder."
            else "Copied to your locked folder. The originals are still in your gallery.",
            Toast.LENGTH_LONG
        ).show()
        onClose()
    }

    LaunchedEffect(Unit) {
        val granted = perms.all { ctx.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (granted) asked = true else {
            app.externalActivityOpen = true
            permLauncher.launch(perms)
        }
    }
    LaunchedEffect(asked) {
        if (asked) media = withContext(Dispatchers.IO) { queryDeviceMedia(ctx) }
    }
    BackHandler(onBack = onClose)

    fun hide() {
        val uris = selected.toList()
        app.importItems(uris, "media") { ok ->
            if (ok.isEmpty()) {
                onClose()
                return@importItems
            }
            try {
                val sender = MediaStore.createDeleteRequest(ctx.contentResolver, ok).intentSender
                app.externalActivityOpen = true
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) {
                app.externalActivityOpen = false
                Toast.makeText(ctx, "Copied in, but couldn't remove the originals.", Toast.LENGTH_LONG).show()
                onClose()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        TopAppBar(
            title = { Text(if (selected.isEmpty()) "Select photos & videos" else "${selected.size} selected") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close") } },
            actions = {
                Button(
                    onClick = { hide() },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text("Hide") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )
        val list = media
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No photos or videos found. If you didn't allow access, allow Photos and videos for this app in Settings.",
                    color = TextDim
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list, key = { it.uri.toString() }) { m ->
                    val isSel = m.uri in selected
                    Box(
                        Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .background(KeyNum)
                            .clickable { if (isSel) selected.remove(m.uri) else selected.add(m.uri) }
                    ) {
                        AsyncImage(
                            model = m.uri,
                            contentDescription = null,
                            imageLoader = loader,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (m.isVideo) Icon(
                            Icons.Outlined.PlayCircle, null, tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(30.dp)
                        )
                        if (isSel) {
                            Box(Modifier.fillMaxSize().background(Color(0x664FD8C4)))
                            Icon(
                                Icons.Outlined.CheckCircle, null, tint = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaViewer(app: AppViewModel, media: List<VaultItem>, start: Int, onClose: () -> Unit) {
    if (media.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val latest by rememberUpdatedState(media)
    val pager = rememberPagerState(initialPage = start.coerceIn(0, media.size - 1)) { latest.size }
    var playing by remember { mutableStateOf<VaultItem?>(null) }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }

    val current = media.getOrNull(pager.currentPage.coerceIn(0, media.size - 1))

    playing?.let { v ->
        VideoPlayer(app, v, onClose = { playing = null })
        return
    }
    BackHandler(onBack = onClose)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            key = { latest.getOrNull(it)?.id ?: it.toString() },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = latest.getOrNull(page) ?: return@HorizontalPager
            if (item.isVideo) {
                Box(
                    Modifier.fillMaxSize().clickable { chrome = !chrome },
                    contentAlignment = Alignment.Center
                ) {
                    Thumb(app, item, Modifier.fillMaxWidth())
                    IconButton(
                        onClick = { playing = item },
                        modifier = Modifier.size(76.dp).clip(CircleShape).background(Color(0x99000000))
                    ) {
                        Icon(Icons.Outlined.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
            } else {
                ZoomableImage(app, item, onZoom = { zoomed = it }, onTap = { chrome = !chrome })
            }
        }
        if (chrome && current != null) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x88000000)).systemBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White) }
                Text(
                    "${pager.currentPage + 1} / ${media.size}",
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { app.restore(listOf(current)) }) {
                    Icon(Icons.Outlined.Restore, "Restore to gallery", tint = Color.White)
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, "Delete", tint = Color.White)
                }
            }
        }
    }
    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this ${if (current.isVideo) "video" else "photo"}?") },
            text = { Text("It'll be gone for good. To keep it, use Restore instead.") },
            confirmButton = {
                TextButton(onClick = {
                    app.delete(listOf(current))
                    confirmDelete = false
                }) { Text("Delete", color = ClearRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ZoomableImage(app: AppViewModel, item: VaultItem, onZoom: (Boolean) -> Unit, onTap: () -> Unit) {
    val bmp by produceState<ImageBitmap?>(null, item.id) {
        value = app.repo.loadImage(item)?.asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) {
            Thumb(app, item, Modifier.fillMaxWidth())
            CircularProgressIndicator(color = Accent)
        } else {
            Image(
                bitmap = b,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else scale = 2.5f
                                onZoom(scale > 1f)
                            }
                        )
                    }
                    .pointerInput(item.id) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1 || scale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset = if (scale == 1f) Offset.Zero else offset + pan * scale
                                    onZoom(scale > 1f)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
    }
}

@Composable
private fun VideoPlayer(app: AppViewModel, item: VaultItem, onClose: () -> Unit) {
    val file by produceState<File?>(null, item.id) { value = app.repo.decryptToCache(item) }
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val f = file
        if (f == null) {
            CircularProgressIndicator(color = Accent, modifier = Modifier.align(Alignment.Center))
        } else {
            AndroidView(
                factory = { c ->
                    VideoView(c).apply {
                        val controller = MediaController(c)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoPath(f.absolutePath)
                        setOnPreparedListener { it.start() }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.systemBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x66000000))
        ) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
    }
}
