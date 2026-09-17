package com.flickbeam.tv.ui.filemanager

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.flickbeam.tv.install.InstallInfo
import com.flickbeam.tv.model.FileEntry
import com.flickbeam.tv.model.formatFileSize
import com.flickbeam.tv.model.formatLastModified
import com.flickbeam.tv.permission.StorageAccessRequest
import com.flickbeam.tv.ui.player.VideoPlayerScreen
import coil.compose.AsyncImage
import com.flickbeam.tv.util.FileOpener
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Keys that open the actions menu for the focused row. We accept several because remotes
// differ. OK stays the normal "open" action.
private val ACTION_MENU_KEYS = setOf(
    Key.Menu,
    Key.ButtonY,
    Key.ButtonX,
)

/**
 * The File Manager screen. It only draws things and sends actions to the ViewModel.
 *
 * Controls:
 * - OK: open a folder / install an APK / open a file
 * - Menu or long-press OK: open the actions menu for that row
 * - Back: go up a folder, or leave to Home from the top
 */
@Composable
fun FileManagerScreen(
    onExitToHome: () -> Unit,
    viewModel: FileManagerViewModel = hiltViewModel(),
) {
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshStorageAccess() }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshStorageAccess() }

    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onInstallPermissionResult() }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // A focus handle per row (by path) so we can put focus back on a specific row.
    val rowFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    val pasteFocusRequester = remember { FocusRequester() }

    suspend fun focusRow(targetPath: String?) {
        val currentEntries = viewModel.entries
        if (currentEntries.isEmpty()) {
            // Nothing in the list to focus — fall back to Paste if there's something
            // waiting, so the D-pad has somewhere to land instead of going nowhere.
            if (viewModel.clipboardItem != null) {
                runCatching { pasteFocusRequester.requestFocus() }
            }
            return
        }
        val index = currentEntries.indexOfFirst { it.absolutePath == targetPath }
            .let { if (it >= 0) it else 0 }
        val path = currentEntries[index].absolutePath
        // Scroll to the row first so it exists, then focus it once it's ready.
        listState.scrollToItem(index)
        val requester = withTimeoutOrNull(1000) {
            snapshotFlow { rowFocusRequesters[path] }.filterNotNull().first()
        }
        runCatching { requester?.requestFocus() }
        // The lazy list restores focus onto the row that was focused in the folder we
        // came from, which lands on a seemingly random row in a long folder. Re-assert
        // our target over the next couple of frames so it wins over that restoration.
        repeat(2) {
            withFrameNanos {}
            runCatching { requester?.requestFocus() }
        }
    }

    // Set focus whenever the folder changes.
    LaunchedEffect(viewModel.currentPath) {
        focusRow(viewModel.focusTargetPath)
    }

    if (!viewModel.hasStorageAccess) {
        StorageAccessRequestScreen(
            onRequestAccess = {
                when (val request = viewModel.storageAccessRequest()) {
                    is StorageAccessRequest.ManageAllFiles -> settingsLauncher.launch(request.intent)
                    is StorageAccessRequest.RuntimePermission ->
                        runtimePermissionLauncher.launch(request.permission)
                }
            },
            onBack = onExitToHome,
        )
        return
    }

    // Which pop-up is open, if any (null = closed), and the row to refocus when it closes.
    var menuTarget by remember { mutableStateOf<FileEntry?>(null) }
    var renameTarget by remember { mutableStateOf<FileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var propertiesTarget by remember { mutableStateOf<FileEntry?>(null) }
    var imageViewerTarget by remember { mutableStateOf<FileEntry?>(null) }
    var mediaViewerTarget by remember { mutableStateOf<FileEntry?>(null) }
    var rowToRestoreFocus by remember { mutableStateOf<FocusRequester?>(null) }
    // True if a long-press opened the menu, so we ignore the button being let go.
    var menuNeedsReleaseGuard by remember { mutableStateOf(false) }

    val anyOverlayOpen = menuTarget != null || renameTarget != null ||
        deleteTarget != null || propertiesTarget != null || imageViewerTarget != null ||
        mediaViewerTarget != null || viewModel.inspectInfo != null ||
        viewModel.copyingItemName != null

    // Hide the status message after a few seconds.
    LaunchedEffect(viewModel.statusMessage) {
        if (viewModel.statusMessage != null) {
            delay(4000)
            viewModel.dismissStatus()
        }
    }

    // When all pop-ups close, put focus back on the row.
    LaunchedEffect(anyOverlayOpen) {
        if (!anyOverlayOpen) {
            rowToRestoreFocus?.let { requester -> runCatching { requester.requestFocus() } }
        }
    }

    // Off while a pop-up is open, so the pop-up handles Back itself. Stays ON during a
    // copy (there's no pop-up to catch it) but does nothing, so Back can't fall through
    // to the system and exit the app while a copy is running.
    BackHandler(enabled = !anyOverlayOpen || viewModel.copyingItemName != null) {
        if (viewModel.copyingItemName == null && !viewModel.navigateUp()) onExitToHome()
    }

    fun performPrimaryAction(entry: FileEntry) {
        viewModel.onEntrySelected(
            entry = entry,
            onOpenImage = { imageEntry -> imageViewerTarget = imageEntry },
            onOpenMedia = { mediaEntry -> mediaViewerTarget = mediaEntry },
            onOpenFile = { fileEntry ->
                val opened = FileOpener.open(context, fileEntry.file)
                if (!opened) viewModel.reportNoAppToOpen(fileEntry)
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            Text(
                text = "File Manager",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
            )
            Text(
                text = viewModel.currentPath,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            viewModel.statusMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            val clipboardItem = viewModel.clipboardItem
            if (clipboardItem != null) {
                ClipboardBanner(
                    name = clipboardItem.name,
                    mode = viewModel.clipboardMode ?: ClipboardMode.COPY,
                    enabled = !anyOverlayOpen,
                    copyingItemName = viewModel.copyingItemName,
                    pasteFocusRequester = pasteFocusRequester,
                    onPaste = {
                        viewModel.paste()
                        // A move empties the clipboard and hides the banner, so send
                        // focus back to the list.
                        if (viewModel.clipboardItem == null) {
                            scope.launch { focusRow(null) }
                        }
                    },
                    onClear = { viewModel.clearClipboard() },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (viewModel.entries.isEmpty()) {
                    Text(
                        text = "This folder is empty",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            viewModel.entries,
                            key = { _, entry -> entry.file.absolutePath },
                        ) { index, entry ->
                            val rowFocusRequester = remember { FocusRequester() }
                            DisposableEffect(entry.absolutePath) {
                                rowFocusRequesters[entry.absolutePath] = rowFocusRequester
                                onDispose { rowFocusRequesters.remove(entry.absolutePath) }
                            }
                            FileRow(
                                entry = entry,
                                focusRequester = rowFocusRequester,
                                // Rows can't be focused while a pop-up is open.
                                enabled = !anyOverlayOpen,
                                onFocused = {
                                    // Keep the focused row on screen as you move down.
                                    scope.launch { listState.bringItemIntoView(index) }
                                },
                                onClick = { performPrimaryAction(entry) },
                                onActions = { fromLongPress ->
                                    rowToRestoreFocus = rowFocusRequester
                                    menuNeedsReleaseGuard = fromLongPress
                                    menuTarget = entry
                                },
                            )
                        }
                    }
                }
            }

            HintBar()
        }

        menuTarget?.let { target ->
            FileActionsMenu(
                entry = target,
                guardInitialRelease = menuNeedsReleaseGuard,
                onOpen = {
                    menuTarget = null
                    performPrimaryAction(target)
                },
                onOpenWith = {
                    menuTarget = null
                    val opened = FileOpener.openWithChooser(context, target.file, "Open ${target.name} with")
                    if (!opened) viewModel.reportNoAppToOpen(target)
                },
                onRename = {
                    menuTarget = null
                    renameTarget = target
                },
                onCopy = {
                    menuTarget = null
                    viewModel.copyToClipboard(target)
                },
                onMove = {
                    menuTarget = null
                    viewModel.moveToClipboard(target)
                },
                onDelete = {
                    menuTarget = null
                    deleteTarget = target
                },
                onProperties = {
                    menuTarget = null
                    propertiesTarget = target
                },
                onDismiss = { menuTarget = null },
            )
        }

        renameTarget?.let { target ->
            RenameDialog(
                entry = target,
                onConfirm = { newName ->
                    renameTarget = null
                    viewModel.rename(target, newName)
                    val renamedPath = File(target.file.parentFile, newName).absolutePath
                    scope.launch { focusRow(renamedPath) }
                },
                onDismiss = { renameTarget = null },
            )
        }

        deleteTarget?.let { target ->
            DeleteConfirmDialog(
                entry = target,
                onConfirm = {
                    // Remember where the deleted row was, so focus can land on the row
                    // that takes its place instead of jumping to the top.
                    val deletedIndex = viewModel.entries
                        .indexOfFirst { it.absolutePath == target.absolutePath }
                    deleteTarget = null
                    viewModel.delete(target)
                    scope.launch {
                        val remaining = viewModel.entries
                        val neighborPath = remaining.getOrNull(deletedIndex)?.absolutePath
                            ?: remaining.lastOrNull()?.absolutePath
                        focusRow(neighborPath)
                    }
                },
                onDismiss = { deleteTarget = null },
            )
        }

        propertiesTarget?.let { target ->
            PropertiesDialog(
                entry = target,
                onDismiss = { propertiesTarget = null },
            )
        }

        imageViewerTarget?.let { target ->
            val images = viewModel.entries.filter { it.isImage }.ifEmpty { listOf(target) }
            val startIndex = images.indexOfFirst { it.absolutePath == target.absolutePath }
                .coerceAtLeast(0)
            ImageViewerScreen(
                images = images,
                startIndex = startIndex,
                onDismiss = { current ->
                    imageViewerTarget = null
                    scope.launch { focusRow(current.absolutePath) }
                },
            )
        }

        mediaViewerTarget?.let { target ->
            VideoPlayerScreen(
                uri = Uri.fromFile(target.file).toString(),
                title = target.name,
                subtitleUri = target.sidecarSubtitle()?.let { Uri.fromFile(it).toString() },
                onExit = {
                    mediaViewerTarget = null
                    scope.launch { focusRow(target.absolutePath) }
                },
                isAudio = target.isAudio,
            )
        }

        viewModel.inspectInfo?.let { info ->
            InspectDialog(
                info = info,
                onInstall = {
                    viewModel.confirmInstall(
                        onNeedInstallPermission = {
                            installPermissionLauncher.launch(viewModel.installPackagesSettingsIntent())
                        },
                    )
                },
                onDismiss = { viewModel.dismissInspect() },
            )
        }
    }
}

/**
 * Full-screen image viewer over all the images in the folder. Left/right (D-pad on a
 * TV, swipe on a phone) moves between them, skipping non-image files. Coil shrinks big
 * photos so they don't run the TV out of memory. Back closes it.
 */
@Composable
private fun ImageViewerScreen(
    images: List<FileEntry>,
    startIndex: Int,
    onDismiss: (FileEntry) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
    ) { images.size }
    val scope = rememberCoroutineScope()
    val current = images.getOrNull(pagerState.currentPage) ?: images.first()
    var slideshowOn by remember { mutableStateOf(false) }

    BackHandler(onBack = { onDismiss(current) })

    // While the slideshow is on, advance every few seconds, wrapping at the end.
    LaunchedEffect(slideshowOn, images.size) {
        if (slideshowOn && images.size > 1) {
            while (true) {
                delay(SLIDESHOW_INTERVAL_MS)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % images.size)
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (pagerState.currentPage < images.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (images.size > 1) slideshowOn = !slideshowOn
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = pagerState,
            key = { images[it].absolutePath },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            AsyncImage(
                model = images[page].file,
                contentDescription = images[page].name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = current.name,
                color = Color.White,
                fontSize = 14.sp,
            )
            if (images.size > 1) {
                Text(
                    text = buildString {
                        append("${pagerState.currentPage + 1} of ${images.size}")
                        if (slideshowOn) append("  •  ▶ Slideshow")
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = if (slideshowOn) "OK to stop slideshow" else "OK to start slideshow",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private const val SLIDESHOW_INTERVAL_MS = 4000L

@Composable
private fun FileRow(
    entry: FileEntry,
    focusRequester: FocusRequester,
    enabled: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onActions: (fromLongPress: Boolean) -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = { onActions(true) },
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = enabled }
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key in ACTION_MENU_KEYS) {
                    onActions(false)
                    true
                } else {
                    false
                }
            },
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = if (entry.isDirectory) "\uD83D\uDCC1 " else "\uD83D\uDCC4 "
            val ext = extensionOf(entry.name)
            // Name takes the leftover space; a long name truncates in the middle so the
            // extension stays readable and the size always keeps its spot on the right.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = icon + entry.name.removeSuffix(ext),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ext.isNotEmpty()) {
                    Text(
                        text = ext,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        maxLines = 1,
                    )
                }
            }
            if (!entry.isDirectory) {
                Text(
                    text = formatFileSize(entry.sizeBytes),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** The file's extension including the dot (e.g. ".mkv"), or "" if it has none. */
private fun extensionOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0) name.substring(dot) else ""
}

/** The actions pop-up for one file. Focus starts on "Open"; Back closes it. */
@Composable
private fun FileActionsMenu(
    entry: FileEntry,
    guardInitialRelease: Boolean,
    onOpen: () -> Unit,
    onOpenWith: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstItemFocus.requestFocus() } }

    // If a long-press opened this menu, the button is still down. Ignore key events until
    // it's released, so letting go doesn't instantly pick "Open".
    var armed by remember { mutableStateOf(!guardInitialRelease) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .onPreviewKeyEvent { event ->
                if (armed) {
                    false
                } else {
                    // Wait for the button to be released, then start responding.
                    if (event.type == KeyEventType.KeyUp) armed = true
                    true
                }
            }
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = entry.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ActionButton(label = "Open", focusRequester = firstItemFocus, onClick = onOpen)
            if (entry.isVideo || entry.isAudio) {
                ActionButton(label = "Open with…", onClick = onOpenWith)
            }
            ActionButton(label = "Rename", onClick = onRename)
            ActionButton(label = "Copy", onClick = onCopy)
            ActionButton(label = "Move", onClick = onMove)
            ActionButton(label = "Delete", onClick = onDelete)
            ActionButton(label = "Properties", onClick = onProperties)
            ActionButton(label = "Cancel", onClick = onDismiss)
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val base = Modifier.fillMaxWidth()
    Button(
        onClick = onClick,
        modifier = if (focusRequester != null) base.focusRequester(focusRequester) else base,
    ) {
        Text(label)
    }
}

/**
 * Rename box. The name is pre-filled with the part before the extension selected, so you
 * can retype without losing the extension. Focusing it opens the TV keyboard.
 */
@Composable
private fun RenameDialog(
    entry: FileEntry,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val selectionEnd = if (!entry.isDirectory && entry.name.contains('.')) {
        entry.name.lastIndexOf('.')
    } else {
        entry.name.length
    }
    var value by remember {
        mutableStateOf(TextFieldValue(entry.name, TextRange(0, selectionEnd)))
    }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    DialogScaffold {
        Text(
            text = "Rename",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    val newName = value.text.trim()
                    if (newName.isNotEmpty()) onConfirm(newName)
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}

/** "Are you sure?" for delete. Cancel is focused first, since delete can't be undone. */
@Composable
private fun DeleteConfirmDialog(
    entry: FileEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    DialogScaffold {
        Text(
            text = "Delete ${entry.name}?",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
        )
        Text(
            text = "This cannot be undone.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Delete") }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(cancelFocus),
            ) { Text("Cancel") }
        }
    }
}

/** Read-only properties view for a file or folder. */
@Composable
private fun PropertiesDialog(entry: FileEntry, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }

    DialogScaffold {
        Text(
            text = "Properties",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        PropertyRow(label = "Name", value = entry.name)
        PropertyRow(label = "Path", value = entry.absolutePath)
        PropertyRow(label = "Type", value = entry.typeLabel)
        PropertyRow(
            label = "Size",
            value = if (entry.isDirectory) "\u2014" else formatFileSize(entry.sizeBytes),
        )
        PropertyRow(label = "Modified", value = formatLastModified(entry.lastModified))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .focusRequester(closeFocus),
        ) { Text("Close") }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.weight(0.3f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            modifier = Modifier.weight(0.7f),
        )
    }
}

/** Shows a package's details before installing. Install starts on the first click. */
@Composable
private fun InspectDialog(
    info: InstallInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val installFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { installFocus.requestFocus() } }

    DialogScaffold {
        Text(
            text = info.appName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        if (info.versionName.isNotBlank()) {
            Text(
                text = "Version ${info.versionName}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        PropertyRow(label = "Package", value = info.packageName)
        PropertyRow(label = "Architecture", value = info.architecture)
        PropertyRow(label = "Splits", value = info.splitCount.toString())
        info.targetSdk?.let { PropertyRow(label = "Target SDK", value = it.toString()) }
        info.minSdk?.let { PropertyRow(label = "Min SDK", value = it.toString()) }

        Text(
            text = "Permissions (${info.permissions.size})",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        if (info.permissions.isEmpty()) {
            Text(
                text = "None requested",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        } else {
            info.permissions.forEach { permission ->
                Text(
                    text = "• ${permission.substringAfterLast('.')}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }

        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(
                onClick = onInstall,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(installFocus),
            ) { Text("Install") }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) { Text("Cancel") }
        }
    }
}

/** The shared dimmed, centered box used by the rename/delete/properties pop-ups. */
@Composable
private fun DialogScaffold(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            content()
        }
    }
}

/** The bar shown while something is copied/cut. "Paste here" pastes into this folder. */
@Composable
private fun ClipboardBanner(
    name: String,
    mode: ClipboardMode,
    enabled: Boolean,
    copyingItemName: String?,
    pasteFocusRequester: FocusRequester,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (copyingItemName != null) {
            Text(
                text = "Copying $copyingItemName… this can take a while for large files",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = "Clipboard: $name (${if (mode == ClipboardMode.COPY) "Copy" else "Move"})",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            // Not focusable while a pop-up is open.
            Button(
                onClick = onPaste,
                modifier = Modifier
                    .focusRequester(pasteFocusRequester)
                    .focusProperties { canFocus = enabled },
            ) { Text("Paste here") }
            Button(
                onClick = onClear,
                modifier = Modifier.focusProperties { canFocus = enabled },
            ) { Text("Clear") }
        }
    }
}

@Composable
private fun HintBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        HintItem(key = "OK", action = "Open")
        HintItem(key = "Menu", action = "Actions")
        HintItem(key = "Back", action = "Up")
    }
}

@Composable
private fun HintItem(key: String, action: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = key,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
        )
        Text(
            text = action,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
    }
}

/** Scrolls the row at [index] into view only if it's off-screen. */
private suspend fun LazyListState.bringItemIntoView(index: Int) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info == null) {
        animateScrollToItem(index)
        return
    }
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    if (info.offset < viewportStart || info.offset + info.size > viewportEnd) {
        animateScrollToItem(index)
    }
}

@Composable
private fun StorageAccessRequestScreen(onRequestAccess: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column {
            Text(
                text = "File Manager",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
            )
            Text(
                text = "FlickBeam needs storage access to browse files on this device.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
            )
            Button(onClick = onRequestAccess) {
                Text("Grant Access")
            }
        }
    }
}
