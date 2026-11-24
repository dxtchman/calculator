@file:OptIn(ExperimentalMaterial3Api::class)

package com.andy.calculator

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TABS = listOf("Photos & videos", "Files", "Notes")

@Composable
fun VaultHomeScreen(app: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var picking by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    val media = app.repo.items.filter { it.isMedia }
    val files = app.repo.items.filter { !it.isMedia }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            picking -> GalleryPicker(app, onClose = { picking = false })
            viewerStart != null -> MediaViewer(app, media, viewerStart!!, onClose = { viewerStart = null })
            editing != null -> NoteEditor(app, editing!!, onClose = { editing = null })
            else -> {
                BackHandler {
                    if (selected.isNotEmpty()) selected.clear() else app.lock()
                }
                VaultMain(
                    app, tab, { tab = it; selected.clear() }, media, files, selected,
                    onOpenMedia = { viewerStart = it },
                    onPickGallery = { picking = true },
                    onEditNote = { editing = it },
                    onSettings = { settings = true },
                )
            }
        }
        if (settings) VaultSettingsDialog(app, onClose = { settings = false })
        BusyOverlay(app.busy)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultMain(
    app: AppViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    media: List<VaultItem>,
    files: List<VaultItem>,
    selected: MutableList<String>,
    onOpenMedia: (Int) -> Unit,
    onPickGallery: () -> Unit,
    onEditNote: (Note) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var addMenu by remember { mutableStateOf(false) }
    var askDeleteOriginals by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pickVisual = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        app.importItems(uris, "media") {}
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        app.importItems(uris, "file") { ok -> if (ok.isNotEmpty()) askDeleteOriginals = ok }
    }
    val selecting = selected.isNotEmpty()
    val selectedItems = app.repo.items.filter { it.id in selected }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (selecting) "${selected.size} selected" else "Locked folder") },
                    navigationIcon = {
                        if (selecting) IconButton(onClick = { selected.clear() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Cancel")
                        }
                    },
                    actions = {
                        if (selecting) {
                            IconButton(onClick = {
                                app.restore(selectedItems)
                                selected.clear()
                            }) { Icon(Icons.Outlined.Restore, "Restore") }
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                        } else {
                            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                            IconButton(onClick = { app.lock() }) { Icon(Icons.Outlined.Lock, "Lock") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
                TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Accent) {
                    TABS.forEachIndexed { i, t ->
                        Tab(
                            selected = tab == i,
                            onClick = { onTab(i) },
                            text = { Text(t, color = if (tab == i) Accent else TextDim, maxLines = 1) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selecting) Box {
                FloatingActionButton(
                    onClick = {
                        when (tab) {
                            0 -> addMenu = true
                            1 -> {
                                app.externalActivityOpen = true
                                pickFiles.launch(arrayOf("*/*"))
                            }
                            else -> onEditNote(app.newNote())
                        }
                    },
                    containerColor = Accent,
                    contentColor = OnAccent
                ) { Icon(Icons.Outlined.Add, "Add") }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("From gallery (hides the originals)") },
                        onClick = {
                            addMenu = false
                            onPickGallery()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy from photo picker") },
                        onClick = {
                            addMenu = false
                            app.externalActivityOpen = true
                            pickVisual.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> if (media.isEmpty()) Empty("No photos or videos yet.\nTap + to hide some.") else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(108.dp),
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(media, key = { _, item -> item.id }) { i, item ->
                            val isSel = item.id in selected
                            Box(
                                Modifier
                                    .padding(2.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KeyNum)
                                    .combinedClickable(
                                        onClick = {
                                            if (selecting) toggle(selected, item.id) else onOpenMedia(i)
                                        },
                                        onLongClick = { toggle(selected, item.id) }
                                    )
                            ) {
                                Thumb(app, item, Modifier.fillMaxSize())
                                if (item.isVideo) Icon(
                                    Icons.Outlined.PlayCircle, null, tint = Color.White,
                                    modifier = Modifier.align(Alignment.Center).size(34.dp)
                                )
                                if (isSel) {
                                    Box(Modifier.fillMaxSize().background(Color(0x664FD8C4)))
                                    Icon(
                                        Icons.Outlined.CheckCircle, null, tint = Color.White,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> if (files.isEmpty()) Empty("No files yet.\nTap + to add some.") else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(files, key = { it.id }) { item ->
                            FileRow(app, item, item.id in selected, selecting,
                                onToggle = { toggle(selected, item.id) },
                                onOpen = { app.openFile(context, item) })
                        }
                    }
                }
                else -> if (app.repo.notes.isEmpty()) Empty("No notes yet.\nTap + to write one.") else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                        items(app.repo.notes.toList(), key = { it.id }) { n ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(KeyNum)
                                    .clickable { onEditNote(n) }
                                    .padding(16.dp)
                            ) {
                                Text(n.title.ifBlank { "Untitled" }, color = TextMain, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (n.body.isNotBlank()) {
                                    Text(n.body, color = TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                }
                                Text(formatDate(n.updated), color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    askDeleteOriginals?.let { uris ->
        AlertDialog(
            onDismissRequest = { askDeleteOriginals = null },
            title = { Text("Delete the originals?") },
            text = { Text("The files are now in your locked folder. Delete the original copies so they're only here?") },
            confirmButton = {
                TextButton(onClick = {
                    app.deleteDocuments(uris)
                    askDeleteOriginals = null
                }) { Text("Delete", color = ClearRed) }
            },
            dismissButton = { TextButton(onClick = { askDeleteOriginals = null }) { Text("Keep") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} item${if (selected.size == 1) "" else "s"}?") },
            text = { Text("They'll be gone for good. To keep them, use Restore instead.") },
            confirmButton = {
                TextButton(onClick = {
                    app.delete(selectedItems)
                    selected.clear()
                    confirmDelete = false
                }) { Text("Delete", color = ClearRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun toggle(list: MutableList<String>, id: String) {
    if (id in list) list.remove(id) else list.add(id)
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = TextDim, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun Thumb(app: AppViewModel, item: VaultItem, modifier: Modifier) {
    val bmp by produceState<ImageBitmap?>(null, item.id) {
        value = app.repo.thumbnail(item)?.asImageBitmap()
    }
    bmp?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = modifier) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    app: AppViewModel,
    item: VaultItem,
    isSelected: Boolean,
    selecting: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) KeyFn else Color.Transparent)
            .combinedClickable(onClick = { if (selecting) onToggle() else onOpen() }, onLongClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(KeyNum), contentAlignment = Alignment.Center) {
            if (item.hasThumb) Thumb(app, item, Modifier.fillMaxSize())
            else Icon(Icons.Outlined.Description, null, tint = Accent)
            if (isSelected) Icon(Icons.Outlined.CheckCircle, null, tint = Accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formatter.formatShortFileSize(context, item.size)} · ${formatDate(item.added)}",
                color = TextDim, fontSize = 13.sp
            )
        }
        if (!selecting) Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = TextDim) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Save a copy to Downloads") }, onClick = {
                    menu = false
                    app.restore(listOf(item), removeAfter = false)
                })
                DropdownMenuItem(text = { Text("Move out to Downloads") }, onClick = {
                    menu = false
                    app.restore(listOf(item))
                })
                DropdownMenuItem(text = { Text("Delete", color = ClearRed) }, onClick = {
                    menu = false
                    confirm = true
                })
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${item.name}?") },
            text = { Text("It'll be gone for good.") },
            confirmButton = {
                TextButton(onClick = { app.delete(listOf(item)); confirm = false }) { Text("Delete", color = ClearRed) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

fun formatDate(ms: Long): String = DateFormat.format("MMM d, yyyy", ms).toString()

@Composable
private fun NoteEditor(app: AppViewModel, note: Note, onClose: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    var confirm by remember { mutableStateOf(false) }

    fun saveAndClose() {
        val changed = title != note.title || body != note.body
        if (changed && (title.isNotBlank() || body.isNotBlank())) {
            app.saveNote(note.copy(title = title, body = body, updated = System.currentTimeMillis()))
        }
        onClose()
    }
    BackHandler { saveAndClose() }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        cursorColor = Accent,
    )
    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        TopAppBar(
            title = { Text("Note") },
            navigationIcon = {
                IconButton(onClick = { saveAndClose() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            },
            actions = {
                IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.Delete, "Delete") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
        )
        TextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("Title", fontSize = 22.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, color = TextMain),
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider(color = Divider, modifier = Modifier.padding(horizontal = 16.dp))
        TextField(
            value = body,
            onValueChange = { body = it },
            placeholder = { Text("Write something") },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = TextMain),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this note?") },
            confirmButton = {
                TextButton(onClick = {
                    app.deleteNote(note)
                    confirm = false
                    onClose()
                }) { Text("Delete", color = ClearRed) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultSettingsDialog(app: AppViewModel, onClose: () -> Unit) {
    var changing by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(app.repo.usesPin) }
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    when {
        changing -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Change ${if (pin) "PIN" else "password"}") },
            text = {
                Column {
                    Row {
                        TextButton(onClick = { pin = true; pw = ""; confirm = "" }) {
                            Text("PIN", color = if (pin) Accent else TextDim)
                        }
                        TextButton(onClick = { pin = false; pw = ""; confirm = "" }) {
                            Text("Password", color = if (!pin) Accent else TextDim)
                        }
                    }
                    PasswordField(pw, { pw = it; error = null }, if (pin) "New PIN" else "New password", pin)
                    Spacer(Modifier.height(8.dp))
                    PasswordField(confirm, { confirm = it; error = null }, "Confirm", pin)
                    error?.let { Text(it, color = ClearRed, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    error = when {
                        pw.length < 4 -> "Use at least 4 ${if (pin) "digits" else "characters"}."
                        pw != confirm -> "They don't match."
                        else -> null
                    }
                    if (error == null) app.changePassword(pw, pin) { onClose() }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
        )
        wiping -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Delete locked folder?") },
            text = { Text("Everything inside — ${app.repo.items.size} items and ${app.repo.notes.size} notes — will be erased for good. Restore anything you want to keep first.") },
            confirmButton = {
                TextButton(onClick = { onClose(); app.wipe() }) { Text("Delete everything", color = ClearRed) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
        )
        else -> AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Locked folder settings") },
            text = {
                Column {
                    Text(
                        "Open it by typing sin666 on the calculator and pressing =. It locks itself whenever you leave the app.",
                        color = TextDim
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { changing = true }) { Text("Change PIN or password") }
                    TextButton(onClick = { wiping = true }) { Text("Delete locked folder", color = ClearRed) }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
        )
    }
}
