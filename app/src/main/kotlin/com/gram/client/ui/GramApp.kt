@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gram.client.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gram.client.GramViewModel
import com.gram.core.media.MediaViewerRepository
import com.gram.core.media.BoundedImageDecoder
import com.gram.core.media.PhotoTransform
import com.gram.core.media.PhotoTransformPolicy
import com.gram.core.tdlib.*
import kotlin.math.absoluteValue

private sealed interface Destination {
    data object Chats : Destination
    data object Downloads : Destination
    data object Settings : Destination
    data class Chat(val chat: ChatSummary) : Destination
    data class Viewer(val chat: ChatSummary, val messages: List<GramMessage>, val initialMessageId: Long) : Destination
}

@Composable
fun GramApp(vm: GramViewModel) {
    val authorization by vm.authorization.collectAsStateWithLifecycle()
    if (authorization !is AuthorizationState.Ready) {
        AuthorizationScreen(authorization, vm)
        return
    }
    var destination by remember { mutableStateOf<Destination>(Destination.Chats) }
    val chats by vm.chats.collectAsStateWithLifecycle()

    AnimatedContent(destination, label = "navigation") { screen ->
        when (screen) {
            Destination.Chats -> HomeScreen(
                chats = chats,
                selected = 0,
                onSelectTab = { destination = if (it == 1) Destination.Downloads else if (it == 2) Destination.Settings else Destination.Chats },
                onChat = { destination = Destination.Chat(it) },
            )
            Destination.Downloads -> DownloadScreen(vm, onTab = { destination = tabDestination(it) })
            Destination.Settings -> SettingsScreen(vm, onTab = { destination = tabDestination(it) })
            is Destination.Chat -> ChatScreen(vm, screen.chat, onBack = { destination = Destination.Chats }) { messages, id ->
                destination = Destination.Viewer(screen.chat, messages, id)
            }
            is Destination.Viewer -> MediaViewerScreen(screen, vm, onBack = { destination = Destination.Chat(screen.chat) })
        }
    }
}

@Composable
private fun AuthorizationScreen(state: AuthorizationState, vm: GramViewModel) {
    var value by remember(state::class) { mutableStateOf("") }
    val title: String
    val hint: String
    val submit: () -> Unit
    when (state) {
        AuthorizationState.Starting -> { title = "Starting TDLib"; hint = "Please wait"; submit = {} }
        AuthorizationState.WaitingForPhoneNumber -> { title = "Your phone number"; hint = "+60…"; submit = { vm.submitPhone(value) } }
        is AuthorizationState.WaitingForCode -> { title = "Telegram code"; hint = "Code sent to ${state.phoneNumber}"; submit = { vm.submitCode(value) } }
        is AuthorizationState.WaitingForPassword -> { title = "Two-step verification"; hint = state.hint ?: "Password"; submit = { vm.submitPassword(value) } }
        AuthorizationState.WaitingForEmailAddress -> { title = "Recovery email"; hint = "Email address"; submit = { vm.submitEmail(value) } }
        is AuthorizationState.WaitingForEmailCode -> { title = "Email verification"; hint = "${state.length}-digit code sent to ${state.addressPattern}"; submit = { vm.submitEmailCode(value) } }
        is AuthorizationState.WaitingForOtherDeviceConfirmation -> { title = "Confirm on another device"; hint = "Open ${state.link} on a device where Telegram is already signed in"; submit = {} }
        is AuthorizationState.Failed -> { title = "Authorization failed"; hint = state.message; submit = {} }
        AuthorizationState.LoggingOut -> { title = "Logging out"; hint = "Please wait"; submit = {} }
        AuthorizationState.Closing -> { title = "Closing session"; hint = "Please wait"; submit = {} }
        AuthorizationState.Closed -> { title = "Session closed"; hint = "Restart Gram to reconnect"; submit = {} }
        AuthorizationState.Ready -> return
    }
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Gram", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall)
            val acceptsInput = state == AuthorizationState.WaitingForPhoneNumber || state is AuthorizationState.WaitingForCode || state is AuthorizationState.WaitingForPassword || state == AuthorizationState.WaitingForEmailAddress || state is AuthorizationState.WaitingForEmailCode
            if (acceptsInput) {
                OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text(hint) }, singleLine = true)
                Button(submit, Modifier.fillMaxWidth(), enabled = value.isNotBlank()) { Text("Continue") }
            } else {
                Text(hint, color = if (state is AuthorizationState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                if (state == AuthorizationState.Starting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

private fun tabDestination(index: Int): Destination = when (index) {
    1 -> Destination.Downloads
    2 -> Destination.Settings
    else -> Destination.Chats
}

@Composable
private fun HomeScreen(chats: List<ChatSummary>, selected: Int, onSelectTab: (Int) -> Unit, onChat: (ChatSummary) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Gram", fontWeight = FontWeight.Bold) }, actions = { IconButton({}) { Icon(Icons.Default.Search, null) } }) },
        bottomBar = { GramNavigation(selected, onSelectTab) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { DemoBanner() }
            items(chats, key = { it.id }) { chat ->
                ListItem(
                    headlineContent = { Text(chat.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(chat.preview, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Avatar(chat.title) },
                    trailingContent = { if (chat.unreadCount > 0) Badge { Text(chat.unreadCount.toString()) } },
                    modifier = Modifier.clickable { onChat(chat) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

@Composable private fun DemoBanner() {
    if (!com.gram.core.tdlib.SessionFactory.isDemo) return
    Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary)
            Column { Text("Offline demo backend", fontWeight = FontWeight.SemiBold); Text("Install the pinned TDLib artifact to connect Telegram.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable private fun Avatar(title: String) {
    val colors = avatarColors(title.hashCode())
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        Text(title.take(1), fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun ChatScreen(vm: GramViewModel, chat: ChatSummary, onBack: () -> Unit, onOpenMedia: (List<GramMessage>, Long) -> Unit) {
    val messages by vm.backend.messages(chat.id).collectAsStateWithLifecycle()
    val uploads by vm.uploads.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        vm.sendMedia(chat.id, uris.map(Uri::toString))
    }
    Scaffold(
        topBar = { TopAppBar(
            title = { Column { Text(chat.title); Text("media-first chat", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) } },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        ) },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) { Icon(Icons.Default.AddPhotoAlternate, "Select photos or videos") }
                    OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 4)
                    IconButton({ vm.sendText(chat.id, draft); draft = "" }, enabled = draft.isNotBlank()) { Icon(Icons.Default.Send, "Send") }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message -> MessageBubble(message, onMedia = { onOpenMedia(messages, message.id) }, onDownload = { id, name -> vm.startDownload(id, name, 128L * 1024 * 1024) }) }
            items(uploads.filter { it.chatId == chat.id && it.phase != TransferPhase.COMPLETED }, key = { "upload-${it.localId}" }) { upload ->
                ElevatedCard { Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sending ${upload.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator({ upload.fraction }, Modifier.fillMaxWidth())
                    Row { Text("${(upload.fraction * 100).toInt()}% · ${upload.phase}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); if (upload.phase == TransferPhase.FAILED || upload.phase == TransferPhase.CANCELED) TextButton({ vm.retryUpload(upload.localId) }) { Text("Retry") } else TextButton({ vm.cancelUpload(upload.localId) }) { Text("Cancel") } }
                } }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: GramMessage, onMedia: () -> Unit, onDownload: (Int, String) -> Unit) {
    val alignment = if (message.outgoing) Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(shape = RoundedCornerShape(18.dp), color = if (message.outgoing) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.widthIn(max = 340.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!message.outgoing) Text(message.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                when (val content = message.content) {
                    is MessageContent.Text -> Text(content.text)
                    is MessageContent.Photo -> MediaTile(content.media, false, content.caption, onMedia, onDownload)
                    is MessageContent.Video -> MediaTile(content.media, true, content.caption, onMedia, onDownload)
                    is MessageContent.Document -> Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null); Spacer(Modifier.width(8.dp)); Text(content.fileName) }
                    is MessageContent.Audio -> Text("♫ ${content.title ?: "Audio"}")
                    is MessageContent.Voice -> Text("Voice message")
                    is MessageContent.Sticker -> Text("${content.emoji ?: "Sticker"}  Sticker", style = MaterialTheme.typography.headlineSmall)
                    is MessageContent.Animation -> Text("Animation · ${content.caption}")
                    is MessageContent.Contact -> Text("Contact · ${content.name}\n${content.phoneNumber}")
                    is MessageContent.Location -> Text("Location · ${content.latitude}, ${content.longitude}")
                    is MessageContent.Poll -> Column { Text(content.question, fontWeight = FontWeight.Bold); content.options.forEach { Text("○  $it") } }
                    is MessageContent.Service -> Text(content.text, color = MaterialTheme.colorScheme.secondary)
                    is MessageContent.Unsupported -> Text("Unsupported Telegram content (${content.tdlibType})", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MediaTile(media: MediaRef, video: Boolean, caption: String, onOpen: () -> Unit, onDownload: (Int, String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(avatarColors(media.fileId))).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
        Icon(if (video) Icons.Default.PlayCircle else Icons.Default.Image, null, Modifier.size(54.dp), tint = Color.White.copy(alpha = .9f))
        IconButton({ onDownload(media.fileId, if (video) "video-${media.fileId}.mp4" else "photo-${media.fileId}.jpg") }, Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Default.Download, "Download") }
    }
    if (caption.isNotBlank()) Text(caption)
}

@Composable
private fun DownloadScreen(vm: GramViewModel, onTab: (Int) -> Unit) {
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Downloads") }) }, bottomBar = { GramNavigation(1, onTab) }) { padding ->
        if (transfers.isEmpty()) Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Downloads started from a message appear here.", color = MaterialTheme.colorScheme.secondary) }
        else LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(transfers, key = { it.fileId }) { transfer ->
                ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row { Text(transfer.displayName, Modifier.weight(1f), maxLines = 1); Text("${(transfer.fraction * 100).toInt()}%") }
                    LinearProgressIndicator({ transfer.fraction }, Modifier.fillMaxWidth())
                    Text("${formatBytes(transfer.downloadedBytes)} / ${formatBytes(transfer.totalBytes)}  ·  ${formatBytes(transfer.bytesPerSecond)}/s", style = MaterialTheme.typography.bodySmall)
                    Row { if (transfer.phase == TransferPhase.ACTIVE || transfer.phase == TransferPhase.QUEUED) TextButton({ vm.pause(transfer.fileId) }) { Text("Pause") } else TextButton({ vm.resume(transfer.fileId) }) { Text("Resume") }; TextButton({ vm.cancel(transfer.fileId) }) { Text("Cancel") } }
                } }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: GramViewModel, onTab: (Int) -> Unit) {
    val concurrency by vm.concurrency.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, bottomBar = { GramNavigation(2, onTab) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Download concurrency", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ConcurrencyMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(selected = concurrency == mode, onClick = { vm.setConcurrency(mode) }, shape = SegmentedButtonDefaults.itemShape(index, ConcurrencyMode.entries.size)) { Text(if (mode == ConcurrencyMode.AUTO) "Auto" else mode.limit.toString()) }
                }
            }
            Text("Auto currently uses two active files until matched device benchmarks establish separate Wi‑Fi and mobile caps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            HorizontalDivider()
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Text("App-private · manual cleanup · warn below 5 GB · pause below 1 GB")
            Text("For reliable long transfers, set Gram to No restrictions and enable autostart in HyperOS.", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MediaViewerScreen(destination: Destination.Viewer, vm: GramViewModel, onBack: () -> Unit) {
    val sequence = remember(destination.messages) { MediaViewerRepository.sequence(destination.messages) }
    val initial = sequence.indexOfFirst { it.message.id == destination.initialMessageId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial, pageCount = { sequence.size })
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { sequence[it].message.id }) { page ->
            val message = sequence[page].message
            when (val content = message.content) {
                is MessageContent.Photo -> ZoomablePhoto(content.media, vm.backend)
                is MessageContent.Video -> GramVideoPlayer(content.media, vm.backend)
                else -> Unit
            }
        }
        IconButton(onBack, Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
        Text("${pager.currentPage + 1} / ${sequence.size}", color = Color.White, modifier = Modifier.navigationBarsPadding().padding(18.dp).align(Alignment.BottomCenter))
    }
    LaunchedEffect(pager.currentPage) {
        val currentMedia = when (val c = sequence[pager.currentPage].message.content) { is MessageContent.Photo -> c.media; is MessageContent.Video -> c.media; else -> null }
        currentMedia?.let { vm.backend.start(it.fileId, "viewed-${it.fileId}", 128L * 1024 * 1024, Priority.VIEWED_FULL) }
        MediaViewerRepository.adjacent(sequence, pager.currentPage).forEach { item ->
            val media = when (val c = item.message.content) { is MessageContent.Photo -> c.media; is MessageContent.Video -> c.media; else -> null }
            media?.let { vm.backend.start(it.fileId, "prefetch-${it.fileId}", 128L * 1024 * 1024, Priority.PREFETCH) }
        }
    }
}

@Composable
private fun ZoomablePhoto(media: MediaRef, backend: GramBackend) {
    var transform by remember(media.fileId) { mutableStateOf(PhotoTransform()) }
    val transformableState = rememberTransformableState { zoom, pan, _ ->
        transform = PhotoTransformPolicy.zoom(transform, zoom, pan.x, pan.y)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val file by backend.file(media.fileId).collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewportWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val viewportHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val path = if (file?.completed == true) file?.localPath else media.localPath ?: media.thumbnailPath
    val bitmap by produceState<android.graphics.Bitmap?>(null, path, viewportWidth, viewportHeight) {
        value = path?.let { BoundedImageDecoder.decode(context, it, viewportWidth, viewportHeight) }
    }
    Box(Modifier.fillMaxSize().transformable(transformableState, canPan = { transform.scale > 1f }).pointerInput(media.fileId) {
        detectTapGestures(onDoubleTap = { transform = PhotoTransformPolicy.doubleTap(transform, it.x - size.width / 2, it.y - size.height / 2) })
    }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().aspectRatio((media.width ?: 4).toFloat() / (media.height ?: 3).toFloat()).graphicsLayer {
            scaleX = transform.scale; scaleY = transform.scale; translationX = transform.offsetX; translationY = transform.offsetY
        }.background(Brush.linearGradient(avatarColors(media.fileId))), contentAlignment = Alignment.Center) {
            Crossfade(bitmap, label = "thumbnail-to-original") { decoded ->
                if (decoded != null) Image(decoded.asImageBitmap(), "Photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Image, null, Modifier.size(72.dp), Color.White); Text("Loading original ${media.fileId}", color = Color.White) }
            }
        }
    }
}

@Composable private fun GramNavigation(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar {
        listOf(Icons.Default.Chat to "Chats", Icons.Default.Download to "Downloads", Icons.Default.Settings to "Settings").forEachIndexed { index, item ->
            NavigationBarItem(selected == index, { onSelect(index) }, { Icon(item.first, null) }, label = { Text(item.second) })
        }
    }
}

private fun avatarColors(seed: Int): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF26547C), Color(0xFF06D6A0)), listOf(Color(0xFF6A4C93), Color(0xFFFF595E)),
        listOf(Color(0xFF003049), Color(0xFFF77F00)), listOf(Color(0xFF1B4332), Color(0xFF74C69D)),
    )
    return palettes[seed.absoluteValue % palettes.size]
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
