package com.rork.cipher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.DeliveryState
import com.rork.cipher.data.MENTION_ALL
import com.rork.cipher.data.mentionSpans
import com.rork.cipher.data.Message
import com.rork.cipher.data.PhotoDraft
import com.rork.cipher.data.PhotoRef
import com.rork.cipher.data.ScheduledMessage
import com.rork.cipher.data.Thread
import com.rork.cipher.data.VoicePlayback
import com.rork.cipher.data.VoiceRecorder
import com.rork.cipher.data.FileRef
import com.rork.cipher.data.VoiceRef
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.burnLabel
import com.rork.cipher.ui.burnRemaining
import com.rork.cipher.ui.clockTime
import com.rork.cipher.ui.components.ConnectionBanner
import com.rork.cipher.ui.components.DayDivider
import com.rork.cipher.ui.components.DayPill
import com.rork.cipher.ui.components.DeliveryTicks
import com.rork.cipher.ui.components.EncryptedPhoto
import com.rork.cipher.ui.components.FileCard
import com.rork.cipher.ui.components.IdenticonAvatar
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.components.PhotoViewer
import com.rork.cipher.ui.components.RecordingBar
import com.rork.cipher.ui.components.TypingDots
import com.rork.cipher.ui.components.UnreadDivider
import com.rork.cipher.ui.components.UploadingBadge
import com.rork.cipher.ui.components.VoiceNote
import com.rork.cipher.ui.dayBucket
import com.rork.cipher.ui.daySeparator
import com.rork.cipher.ui.exactStamp
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt

private val BURN_OPTIONS = listOf<Int?>(null, 5, 60, 60 * 24, 60 * 24 * 7)

/** Self-destruct timers offered for a single message, in minutes. */
private val MESSAGE_BURN_OPTIONS = listOf(1, 5, 60, 60 * 24)

/** How long a burn-after-reading message survives once it has been opened. */
private const val READ_BURN_MS = 30_000L

/** How long a doomed bubble spends shredding itself before it is gone. */
private const val SHRED_MS = 900L

/** Glyphs a burning message dissolves into. */
private const val SHRED_GLYPHS = "\u2593\u2592\u2591#%&@$*/\\|"
private val REACTIONS = listOf("\uD83D\uDD25", "\u2705", "\uD83D\uDC4D", "\uD83D\uDE02", "\u2764\uFE0F", "\uD83D\uDC40")

/** Messages this close together from the same author are drawn as one stack. */
private const val GROUP_WINDOW_MS = 5 * 60_000L

/** Bubble corner radii: round by default, tight where a stack joins. */
private val ROUND = 24.dp
private val TIGHT = 8.dp

/** The strip a dragged bubble uncovers, holding the clock time. */
private val GUTTER = 62.dp

/** How far the gutter stays open after a short pull, and for how long. */
private val PEEK = 52.dp
private const val PEEK_HOLD_MS = 1_500L

/** A voice message stops itself here rather than recording without end. */
private const val MAX_VOICE_MS = 120_000L

/** How many images one send preview holds, so a batch cannot exhaust memory. */
private const val MAX_PHOTO_BATCH = 8

/** How far the finger has to slide away from the microphone to cancel. */
private val CANCEL_SLIDE = 96.dp

/** One rendered row of the conversation: the header, a day divider or a message. */
private sealed interface ChatRow {
    data object Header : ChatRow
    data class Day(val label: String, val id: String) : ChatRow
    data class Unread(val count: Int) : ChatRow

    /**
     * @param continues the bubble above is from the same author, so this one
     *   tucks under it
     * @param continued the bubble below is from the same author
     * @param showStatus this is my newest message, so it carries the receipt word
     */
    data class Msg(
        val message: Message,
        val continues: Boolean,
        val continued: Boolean,
        val showStatus: Boolean
    ) : ChatRow
}

/** True when two neighbouring messages belong in the same visual stack. */
private fun stacks(above: Message?, below: Message?): Boolean {
    if (above == null || below == null) return false
    if (above.system || below.system) return false
    if (above.outgoing != below.outgoing) return false
    if (above.from.orEmpty() != below.from.orEmpty()) return false
    if (dayBucket(above.at) != dayBucket(below.at)) return false
    return below.at - above.at in 0..GROUP_WINDOW_MS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    peer: String,
    viewModel: CipherViewModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val typingPeers by viewModel.typingPeers.collectAsStateWithLifecycle()
    val onlinePeers by viewModel.onlinePeers.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val hasNetwork by viewModel.hasNetwork.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val allScheduled by viewModel.scheduled.collectAsStateWithLifecycle()
    val playback by viewModel.voicePlayback.collectAsStateWithLifecycle()
    val scheduled = remember(allScheduled, peer) {
        allScheduled.filter { it.peer == peer }.sortedBy { it.at }
    }

    val thread = remember(threads, peer) {
        threads.firstOrNull { it.peer == peer } ?: Thread(peer = peer, messages = emptyList())
    }
    val room = thread.group
    val me = remember { viewModel.username().orEmpty() }
    // The thread addressed to yourself is a notebook, not a correspondence:
    // nobody is typing, nobody is online and there is no key to verify.
    val isSelf = room == null && peer == me && me.isNotEmpty()
    val isBlocked = room == null && blocked.contains(peer)
    val isTyping = room == null && !isSelf && typingPeers.contains(peer) && !isBlocked
    val isOnline = room == null && !isSelf && onlinePeers.contains(peer) && !isBlocked
    val verifiedPeers by viewModel.verified.collectAsStateWithLifecycle()
    val alarms by viewModel.keyAlarms.collectAsStateWithLifecycle()
    val keyAlarm = room == null && !isSelf && alarms.contains(peer)
    val isVerified = room == null && !isSelf && !keyAlarm && verifiedPeers.contains(peer)

    val listState = rememberLazyListState()
    val snackbarHost = remember { SnackbarHostState() }
    // The composer is held as a text field value, not a plain string, because
    // the caret position is what decides whether an "@" is being typed.
    var field by remember(peer) { mutableStateOf(TextFieldValue(thread.draft)) }
    val draft = field.text
    var menuOpen by remember { mutableStateOf(false) }
    var showVerify by remember { mutableStateOf(false) }
    var showBurn by remember { mutableStateOf(false) }
    var replyTo by remember(peer) { mutableStateOf<Message?>(null) }
    var actionFor by remember { mutableStateOf<Message?>(null) }
    var highlightId by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAttach by remember { mutableStateOf(false) }
    var secretMode by remember(peer) { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var editing by remember(peer) { mutableStateOf<Message?>(null) }
    var historyFor by remember { mutableStateOf<Message?>(null) }
    var viewing by remember { mutableStateOf<Message?>(null) }
    var forwarding by remember { mutableStateOf<Message?>(null) }
    var cameraTarget by remember { mutableStateOf<Uri?>(null) }
    var searching by remember(peer) { mutableStateOf(false) }
    var query by remember(peer) { mutableStateOf("") }

    // Hold-to-record state. The recorder is a device resource, so it is owned
    // by this screen and released the moment the screen goes away.
    val recorder = remember { VoiceRecorder(context) }
    val cancelSlidePx = with(LocalDensity.current) { CANCEL_SLIDE.toPx() }
    var recording by remember { mutableStateOf(false) }
    var recordElapsed by remember { mutableLongStateOf(0L) }
    var recordLevel by remember { mutableIntStateOf(0) }
    var cancelling by remember { mutableStateOf(false) }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var matchIndex by remember(peer) { mutableIntStateOf(0) }
    var focusId by remember(peer) { mutableStateOf<String?>(null) }
    var didInitialScroll by remember(peer) { mutableStateOf(false) }

    /** Only bubbles that land after the screen opened get the entrance animation. */
    val openedAt = remember(peer) { System.currentTimeMillis() }
    val animatedIds = remember(peer) { mutableSetOf<String>() }

    /** Snapshot of the unread count at entry, so the divider does not move. */
    val unreadStartId = remember(peer) {
        val incoming = thread.messages.filter { !it.outgoing }
        val count = thread.unread.coerceAtMost(incoming.size)
        if (count <= 0) null else incoming[incoming.size - count].id
    }
    val unreadStartCount = remember(peer) { thread.unread }

    val matches = remember(thread.messages, query) {
        val needle = query.trim()
        if (needle.length < 2) emptyList()
        else thread.messages
            .filter { it.preview.contains(needle, ignoreCase = true) }
            .map { it.id }
    }

    // Picked images do not leave for the hub on the way out of the picker:
    // they land in the send preview first, where they are captioned, edited,
    // reordered and (optionally) locked before anything is encrypted.
    var photoDrafts by remember(peer) { mutableStateOf<List<PhotoDraft>>(emptyList()) }
    var photoCaption by remember(peer) { mutableStateOf("") }

    val stageForSend: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val known = photoDrafts.mapTo(mutableSetOf()) { it.uri }
            val locked = photoDrafts.firstOrNull()?.locked == true
            val added = uris.filterNot { known.contains(it) }
                .map { PhotoDraft(uri = it, locked = locked) }
            if (photoDrafts.isEmpty() && photoCaption.isEmpty()) photoCaption = draft
            photoDrafts = (photoDrafts + added).take(MAX_PHOTO_BATCH)
        }
    }
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTO_BATCH)
    ) { uris -> stageForSend(uris) }
    val cameraPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = cameraTarget
        if (saved && uri != null) stageForSend(listOf(uri))
        cameraTarget = null
    }
    // Which file is being fetched right now, so only its own card spins.
    var fileBusy by remember { mutableStateOf<String?>(null) }

    /** Downloads a file if needed, then hands it to whatever app can open it. */
    val openFile: (FileRef) -> Unit = { ref ->
        if (fileBusy == null) {
            fileBusy = ref.blob
            scope.launch {
                val uri = viewModel.openFile(ref)
                fileBusy = null
                if (uri == null) return@launch
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, ref.mime.ifEmpty { "*/*" })
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (error: ActivityNotFoundException) {
                    snackbarHost.showSnackbar("No app on this phone opens ${ref.kind} files.")
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // The picker's grant dies with the activity, so the file is read
            // through it right away rather than remembered as a URI.
            viewModel.sendFile(peer, uri, replyTo)
            replyTo = null
        }
    }
    val micPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (!granted) {
            scope.launch {
                snackbarHost.showSnackbar("Cipher needs the microphone to record a voice message.")
            }
        }
    }

    /** Starts capture, or asks for the microphone the first time. */
    val startHold: () -> Unit = {
        if (!micGranted) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else if (recorder.start()) {
            recording = true
            cancelling = false
            recordElapsed = 0L
            recordLevel = 0
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            scope.launch {
                snackbarHost.showSnackbar("The microphone is not available right now.")
            }
        }
    }
    val releaseHold: () -> Unit = {
        if (recording) {
            recording = false
            val discard = cancelling
            cancelling = false
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (discard) {
                recorder.cancel()
            } else {
                val captured = recorder.stop()
                if (captured == null) {
                    scope.launch {
                        snackbarHost.showSnackbar("Hold the microphone to record.")
                    }
                } else {
                    viewModel.sendVoice(peer, captured, replyTo)
                    replyTo = null
                }
            }
            recordElapsed = 0L
            recordLevel = 0
        }
    }

    val rows = remember(thread.messages, unreadStartId, unreadStartCount) {
        val lastMine = thread.messages.lastOrNull { it.outgoing && !it.system }?.id
        buildList {
            add(ChatRow.Header)
            thread.messages.forEachIndexed { index, message ->
                val previous = thread.messages.getOrNull(index - 1)
                val next = thread.messages.getOrNull(index + 1)
                val newDay = previous == null || dayBucket(previous.at) != dayBucket(message.at)
                if (newDay) {
                    add(ChatRow.Day(daySeparator(message.at), "day-${message.id}"))
                }
                val breaksHere = message.id == unreadStartId && unreadStartCount > 0
                if (breaksHere) {
                    add(ChatRow.Unread(unreadStartCount))
                }
                val breaksBelow = next != null &&
                    next.id == unreadStartId && unreadStartCount > 0
                add(
                    ChatRow.Msg(
                        message = message,
                        continues = !breaksHere && stacks(previous, message),
                        continued = !breaksBelow && stacks(message, next),
                        showStatus = message.id == lastMine
                    )
                )
            }
        }
    }

    val jumpTo: (String) -> Unit = { id ->
        val index = rows.indexOfFirst { it is ChatRow.Msg && it.message.id == id }
        if (index >= 0) {
            focusId = id
            scope.launch { listState.animateScrollToItem(index) }
        }
    }
    val showJump by remember { derivedStateOf { listState.canScrollForward } }
    // The day the top of the list currently sits in, floated while scrolling so
    // you always know which day you are reading.
    val floatingDay by remember(rows) {
        derivedStateOf {
            val top = listState.firstVisibleItemIndex
            (top downTo 0).firstNotNullOfOrNull { index ->
                (rows.getOrNull(index) as? ChatRow.Day)?.label
            }
        }
    }
    var dayFloating by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            dayFloating = true
        } else {
            delay(1_000L)
            dayFloating = false
        }
    }
    val hasEphemeral = remember(thread.messages) { thread.messages.any { it.ephemeral } }

    DisposableEffect(peer) {
        viewModel.openThread(peer)
        onDispose {
            viewModel.setDraft(peer, draft)
            viewModel.closeThread(peer)
            // Leaving the thread never leaves the microphone open or a note
            // playing into the next screen.
            recorder.cancel()
            viewModel.stopVoice()
        }
    }

    // Drives the live meter and the mono clock while a finger is held down,
    // and stops on its own at the length cap instead of recording forever.
    LaunchedEffect(recording) {
        while (recording) {
            recordLevel = recorder.sample()
            recordElapsed = recorder.elapsedMs
            if (recordElapsed >= MAX_VOICE_MS) {
                releaseHold()
                return@LaunchedEffect
            }
            delay(80L)
        }
    }
    LaunchedEffect(hasEphemeral) {
        while (hasEphemeral) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(thread.messages.size, isTyping) {
        if (rows.size > 1) {
            val dividerIndex = rows.indexOfFirst { it is ChatRow.Unread }
            if (!didInitialScroll && dividerIndex > 0) {
                listState.animateScrollToItem((dividerIndex - 1).coerceAtLeast(0))
            } else if (!searching) {
                listState.animateScrollToItem(rows.lastIndex)
            }
            didInitialScroll = true
        }
        viewModel.markRead(peer)
    }
    LaunchedEffect(matches) {
        if (matches.isEmpty()) {
            focusId = null
            return@LaunchedEffect
        }
        matchIndex = matches.lastIndex
        jumpTo(matches[matchIndex])
    }
    LaunchedEffect(highlightId) {
        if (highlightId != null) {
            delay(1_400L)
            highlightId = null
        }
    }
    LaunchedEffect(lastError) {
        val message = lastError ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message)
        viewModel.clearError()
    }

    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = Canvas,
        snackbarHost = {
            SnackbarHost(snackbarHost) { data ->
                Snackbar(
                    containerColor = SurfaceElevated,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        topBar = {
            Column {
                if (searching) {
                    ChatSearchBar(
                        query = query,
                        onQuery = { query = it },
                        index = matchIndex,
                        total = matches.size,
                        onOlder = {
                            if (matchIndex > 0) {
                                matchIndex -= 1
                                jumpTo(matches[matchIndex])
                            }
                        },
                        onNewer = {
                            if (matchIndex < matches.lastIndex) {
                                matchIndex += 1
                                jumpTo(matches[matchIndex])
                            }
                        },
                        onClose = {
                            searching = false
                            query = ""
                            focusId = null
                        }
                    )
                } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Canvas,
                        titleContentColor = TextPrimary
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenProfile)
                                .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Box {
                                if (room != null) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceElevated)
                                            .border(
                                                1.dp,
                                                SignalGreen.copy(alpha = 0.45f),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            Icons.Outlined.Groups,
                                            contentDescription = null,
                                            tint = SignalGreen,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                } else {
                                    IdenticonAvatar(username = peer, size = 38.dp)
                                }
                                if (isOnline) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(11.dp)
                                            .clip(CircleShape)
                                            .background(Canvas)
                                            .padding(1.dp)
                                            .clip(CircleShape)
                                            .background(SignalGreen)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isSelf) "Note to self" else thread.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                when {
                                    isSelf -> MonoKeyText(text = "only you · synced encrypted")
                                    keyAlarm -> Text(
                                        text = "security code changed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WarningRed
                                    )
                                    room != null -> MonoKeyText(
                                        text = room.members
                                            .sorted()
                                            .joinToString(", ") { if (it == me) "you" else it }
                                            .take(46)
                                    )
                                    isBlocked -> Text(
                                        text = "blocked",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WarningRed
                                    )
                                    isTyping -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "typing",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SignalGreen
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        TypingDots()
                                    }
                                    isOnline -> Text(
                                        text = "online",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalGreen
                                    )
                                    isVerified -> Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Verified,
                                            contentDescription = null,
                                            tint = SignalGreen,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = "verified",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SignalGreen
                                        )
                                    }
                                    else -> MonoKeyText(
                                        text = "key · ${viewModel.keyFingerprint(peer) ?: "not fetched"}"
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (thread.burnMinutes != null) {
                            BurnPill(
                                minutes = thread.burnMinutes,
                                onClick = { showBurn = true }
                            )
                        }
                        IconButton(onClick = { searching = true }) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search this chat",
                                tint = TextPrimary
                            )
                        }
                        if (room == null && !isSelf) {
                            IconButton(onClick = { showVerify = true }) {
                                Icon(
                                    imageVector = when {
                                        keyAlarm -> Icons.Outlined.WarningAmber
                                        isVerified -> Icons.Outlined.Verified
                                        else -> Icons.Outlined.Lock
                                    },
                                    contentDescription = "Verify key",
                                    tint = when {
                                        keyAlarm -> WarningRed
                                        isVerified -> SignalGreen
                                        viewModel.knowsKey(peer) -> TextPrimary
                                        else -> TextSecondary
                                    }
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = "Chat options",
                                    tint = TextPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                containerColor = SurfaceElevated
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (thread.pinned) "Unpin chat" else "Pin chat") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.PushPin, contentDescription = null)
                                    },
                                    onClick = {
                                        viewModel.togglePin(peer)
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (thread.muted) "Unmute" else "Mute") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.NotificationsOff,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        viewModel.toggleMute(peer)
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Disappearing messages") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Timer, contentDescription = null)
                                    },
                                    onClick = {
                                        showBurn = true
                                        menuOpen = false
                                    }
                                )
                                if (room != null) {
                                    DropdownMenuItem(
                                        text = { Text("Room details") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Groups, contentDescription = null)
                                        },
                                        onClick = {
                                            menuOpen = false
                                            onOpenProfile()
                                        }
                                    )
                                } else if (!isSelf) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isBlocked) "Unblock @$peer"
                                                else "Block @$peer",
                                                color = if (isBlocked) TextPrimary else WarningRed
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isBlocked) Icons.Outlined.LockOpen
                                                else Icons.Outlined.Block,
                                                contentDescription = null,
                                                tint = if (isBlocked) TextPrimary else WarningRed
                                            )
                                        },
                                        onClick = {
                                            viewModel.setBlocked(peer, !isBlocked)
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
                                            menuOpen = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Clear messages", color = WarningRed) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = WarningRed
                                        )
                                    },
                                    onClick = {
                                        viewModel.clearMessages(peer)
                                        menuOpen = false
                                    }
                                )
                            }
                        }
                    }
                )
                }
                val pinnedMessage = thread.pinnedMessage
                if (pinnedMessage != null && !searching) {
                    PinnedBar(
                        message = pinnedMessage,
                        onClick = { jumpTo(pinnedMessage.id) },
                        onUnpin = { viewModel.pinMessage(peer, null) }
                    )
                }
                if (keyAlarm && !searching) {
                    KeyChangedBar(
                        peer = peer,
                        onVerify = { showVerify = true },
                        onDismiss = { viewModel.dismissKeyAlarm(peer) }
                    )
                }
                ConnectionBanner(state = connection, hasNetwork = hasNetwork)
            }
        },
        bottomBar = {
            if (isBlocked) {
                BlockedBar(peer = peer, onUnblock = { viewModel.setBlocked(peer, false) })
            } else {
                Composer(
                    value = field,
                    mentionable = remember(room, me) {
                        room?.members?.filterNot { it == me }.orEmpty()
                    },
                    replyTo = replyTo,
                    editing = editing,
                    threadBurnMinutes = thread.burnMinutes,
                    scheduled = scheduled,
                    now = now,
                    onClearEdit = {
                        editing = null
                        field = TextFieldValue()
                    },
                    onClearReply = { replyTo = null },
                    onAttach = { showAttach = true },
                    onValueChange = { next ->
                        field = next
                        viewModel.setTyping(peer, next.text.isNotBlank())
                    },
                    secret = secretMode,
                    onToggleSecret = { secretMode = !secretMode },
                    onSchedule = { showSchedule = true },
                    onOpenScheduled = { showQueue = true },
                    recording = recording,
                    recordElapsedMs = recordElapsed,
                    recordLevel = recordLevel,
                    cancelling = cancelling,
                    onHoldStart = startHold,
                    onHoldSlide = { travelled -> cancelling = travelled < -cancelSlidePx },
                    onHoldRelease = releaseHold,
                    onSend = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val target = editing
                        if (target != null) {
                            viewModel.editMessage(peer, target.id, draft)
                            editing = null
                        } else {
                            viewModel.send(peer, draft, replyTo, secretMode)
                        }
                        field = TextFieldValue()
                        replyTo = null
                        secretMode = false
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row ->
                        when (row) {
                            ChatRow.Header -> "header"
                            is ChatRow.Day -> row.id
                            is ChatRow.Unread -> "unread-divider"
                            is ChatRow.Msg -> row.message.id
                        }
                    }
                ) { _, row ->
                    when (row) {
                        ChatRow.Header -> Column {
                            EncryptionNotice(
                                fingerprint = when {
                                    room != null -> "${room.members.size} members"
                                    isSelf -> "your own key"
                                    else -> viewModel.keyFingerprint(peer) ?: "key not fetched"
                                },
                                room = room != null
                            )
                            if (thread.burnMinutes != null) BurnNotice(minutes = thread.burnMinutes)
                            if (thread.messages.isEmpty()) {
                                EmptyThread(peer = peer, roomName = room?.name)
                            }
                        }
                        is ChatRow.Day -> DayDivider(label = row.label)
                        is ChatRow.Unread -> UnreadDivider(count = row.count)
                        is ChatRow.Msg -> if (row.message.system) {
                            SystemNote(
                                text = row.message.text,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    fadeOutSpec = null
                                )
                            )
                        } else MessageBubble(
                            avatarFor = row.message.from ?: peer,
                            senderName = row.message.from
                                ?.takeIf { room != null && !row.message.outgoing && !row.continues },
                            continues = row.continues,
                            continued = row.continued,
                            showStatus = row.showStatus,
                            me = me,
                            roster = room?.members.orEmpty(),
                            message = row.message,
                            now = now,
                            entrance = remember(row.message.id) {
                                row.message.at >= openedAt && animatedIds.add(row.message.id)
                            },
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null
                            ),
                            highlighted = highlightId == row.message.id ||
                                focusId == row.message.id,
                            highlight = if (searching) query.trim() else "",
                            loadPhoto = viewModel::photoBytes,
                            playback = playback,
                            onVoiceToggle = viewModel::toggleVoice,
                            onVoiceSeek = viewModel::seekVoice,
                            fileReady = viewModel::fileReady,
                            fileBusy = fileBusy,
                            onFileOpen = { ref -> openFile(ref) },
                            onPhotoClick = { viewing = row.message },
                            onReply = { replyTo = row.message },
                            onShowEdits = { historyFor = row.message },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                actionFor = row.message
                            },
                            onQuoteClick = { quotedId ->
                                val index = rows.indexOfFirst {
                                    it is ChatRow.Msg && it.message.id == quotedId
                                }
                                if (index >= 0) {
                                    highlightId = quotedId
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                            },
                            onDoubleTap = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.react(peer, row.message.id, REACTIONS[4])
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = dayFloating && floatingDay != null,
                enter = fadeIn(tween(140)) + slideInVertically { height -> -height },
                exit = fadeOut(tween(260)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
            ) {
                DayPill(label = floatingDay.orEmpty())
            }

            AnimatedVisibility(
                visible = showJump,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                JumpToLatest(
                    unread = thread.unread,
                    onClick = { scope.launch { listState.animateScrollToItem(rows.lastIndex) } }
                )
            }
        }
    }

    val target = actionFor
    if (target != null) {
        MessageActionSheet(
            message = target,
            me = me,
            pinned = thread.pinnedId == target.id,
            // A locked photo cannot be passed on: forwarding it would re-seal
            // it for somebody its sender never chose.
            canForward = threads.any { it.peer != peer } && target.photo?.locked != true,
            now = now,
            onDismiss = { actionFor = null },
            onReact = { emoji ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.react(peer, target.id, emoji)
                actionFor = null
            },
            onReply = {
                replyTo = target
                actionFor = null
            },
            onCopy = {
                copyKey(context, target.text)
                actionFor = null
            },
            onForward = {
                forwarding = target
                actionFor = null
            },
            onSaveFile = {
                val ref = target.file
                actionFor = null
                if (ref != null) {
                    scope.launch {
                        if (viewModel.saveFile(ref)) {
                            snackbarHost.showSnackbar("Saved ${ref.name} to Downloads.")
                        }
                    }
                }
            },
            onPin = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.pinMessage(
                    peer,
                    if (thread.pinnedId == target.id) null else target.id
                )
                actionFor = null
            },
            onBurn = { minutes ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.burnMessage(peer, target.id, minutes)
                actionFor = null
                scope.launch {
                    snackbarHost.showSnackbar("Burns in ${burnLabel(minutes)} on both devices.")
                }
            },
            onBurnOnRead = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.burnOnRead(peer, target.id, READ_BURN_MS)
                actionFor = null
                scope.launch {
                    snackbarHost.showSnackbar("Armed. It burns 30s after they open it.")
                }
            },
            onEdit = {
                editing = target
                field = TextFieldValue(target.text, TextRange(target.text.length))
                replyTo = null
                secretMode = false
                actionFor = null
            },
            onHistory = {
                historyFor = target
                actionFor = null
            },
            onOpenPhoto = {
                viewing = target
                actionFor = null
            },
            onResend = {
                viewModel.resend(peer, target.id)
                actionFor = null
            },
            onUnsend = {
                viewModel.unsend(peer, target.id)
                actionFor = null
            },
            onDelete = {
                viewModel.deleteMessage(peer, target.id)
                actionFor = null
            }
        )
    }

    val history = historyFor
    if (history != null) {
        EditHistorySheet(message = history, onDismiss = { historyFor = null })
    }

    val forwarded = forwarding
    if (forwarded != null) {
        ForwardSheet(
            threads = threads.filter { it.peer != peer },
            onDismiss = { forwarding = null },
            onPick = { destination ->
                viewModel.forwardMessage(peer, forwarded.id, destination.peer)
                forwarding = null
                scope.launch {
                    snackbarHost.showSnackbar("Forwarded to ${destination.title}")
                }
            }
        )
    }

    val viewed = viewing
    val viewedPhoto = viewed?.photo
    if (viewed != null && viewedPhoto != null) {
        PhotoViewer(
            ref = viewedPhoto,
            caption = viewed.text,
            load = viewModel::photoBytes,
            onSave = { viewModel.savePhoto(viewedPhoto) },
            onDismiss = { viewing = null }
        )
    }

    if (photoDrafts.isNotEmpty()) {
        PhotoComposer(
            drafts = photoDrafts,
            caption = photoCaption,
            peerLabel = thread.title.removePrefix("@"),
            loadPreview = { uri, edit, edge -> viewModel.photoPreview(uri, edit, edge) },
            onDraftsChange = { photoDrafts = it },
            onCaptionChange = { photoCaption = it },
            onAddMore = {
                galleryPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onCancel = {
                photoDrafts = emptyList()
                photoCaption = ""
            },
            onSend = {
                viewModel.sendPhotos(peer, photoDrafts, photoCaption.trim(), replyTo)
                photoDrafts = emptyList()
                photoCaption = ""
                field = TextFieldValue()
                replyTo = null
            }
        )
    }

    if (showSchedule) {
        ScheduleSheet(
            preview = if (secretMode) "Hidden message" else draft,
            now = now,
            onDismiss = { showSchedule = false },
            onPick = { at ->
                val queued = viewModel.scheduleMessage(
                    peer = peer,
                    text = draft,
                    at = at,
                    secret = secretMode,
                    burnMinutes = null,
                    replyTo = replyTo
                )
                showSchedule = false
                if (queued) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    field = TextFieldValue()
                    replyTo = null
                    secretMode = false
                    scope.launch {
                        snackbarHost.showSnackbar("Queued · leaves ${scheduleStamp(at, now)}")
                    }
                } else {
                    scope.launch { snackbarHost.showSnackbar("Pick a moment still ahead.") }
                }
            }
        )
    }

    if (showQueue) {
        if (scheduled.isEmpty()) showQueue = false
        else ScheduledSheet(
            items = scheduled,
            now = now,
            onDismiss = { showQueue = false },
            onSendNow = { id ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.sendScheduledNow(id)
                if (scheduled.size <= 1) showQueue = false
            },
            onCancel = { id ->
                viewModel.cancelScheduled(id)
                if (scheduled.size <= 1) showQueue = false
            }
        )
    }

    if (showAttach) {
        AttachSheet(
            onDismiss = { showAttach = false },
            onCamera = {
                showAttach = false
                val uri = newCameraTarget(context)
                cameraTarget = uri
                try {
                    cameraPicker.launch(uri)
                } catch (error: ActivityNotFoundException) {
                    cameraTarget = null
                    scope.launch { snackbarHost.showSnackbar("No camera on this device.") }
                }
            },
            onGallery = {
                showAttach = false
                galleryPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onFile = {
                showAttach = false
                try {
                    filePicker.launch(arrayOf("*/*"))
                } catch (error: ActivityNotFoundException) {
                    scope.launch { snackbarHost.showSnackbar("No file browser on this device.") }
                }
            }
        )
    }

    if (showVerify) {
        VerifySheet(peer = peer, viewModel = viewModel, onDismiss = { showVerify = false })
    }

    if (showBurn) {
        ModalBottomSheet(
            onDismissRequest = { showBurn = false },
            containerColor = SurfaceElevated
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 40.dp)) {
                Text(
                    text = "Disappearing messages",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "The hub deletes them on schedule — nothing to trust, nothing left to seize.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                BURN_OPTIONS.forEach { option ->
                    val selected = thread.burnMinutes == option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setBurn(peer, option)
                                showBurn = false
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = option?.let { "After ${burnLabel(it)}" } ?: "Off",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) SignalGreen else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                Icons.Outlined.DoneAll,
                                contentDescription = null,
                                tint = SignalGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: Message,
    me: String,
    pinned: Boolean,
    canForward: Boolean,
    now: Long,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onPin: () -> Unit,
    onBurn: (Int) -> Unit,
    onBurnOnRead: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onOpenPhoto: () -> Unit,
    onSaveFile: () -> Unit,
    onResend: () -> Unit,
    onUnsend: () -> Unit,
    onDelete: () -> Unit
) {
    var burnOpen by remember(message.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            if (message.outgoing) {
                ReceiptCard(message = message)
                Spacer(Modifier.height(8.dp))
            } else {
                ArrivalCard(message = message, now = now)
                Spacer(Modifier.height(8.dp))
            }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                REACTIONS.forEach { emoji ->
                    val mine = message.reactions[me] == emoji
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (mine) SignalGreen.copy(alpha = 0.18f) else Canvas)
                            .border(
                                width = 1.dp,
                                color = if (mine) SignalGreen else Hairline,
                                shape = CircleShape
                            )
                            .clickable { onReact(emoji) }
                    ) {
                        Text(text = emoji, fontSize = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SheetAction(
                label = "Reply",
                icon = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = TextPrimary) },
                onClick = onReply
            )
            if (message.photo != null) {
                SheetAction(
                    label = "View photo",
                    icon = {
                        Icon(Icons.Outlined.Image, contentDescription = null, tint = TextPrimary)
                    },
                    onClick = onOpenPhoto
                )
            }
            if (message.file != null) {
                SheetAction(
                    label = "Save to Downloads",
                    icon = {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    },
                    onClick = onSaveFile
                )
            }
            if (message.text.isNotBlank()) {
                SheetAction(
                    label = "Copy text",
                    icon = {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    },
                    onClick = onCopy
                )
            }
            if (canForward) {
                SheetAction(
                    label = "Forward to another chat",
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    },
                    onClick = onForward
                )
            }
            SheetAction(
                label = if (pinned) "Unpin from top" else "Pin to top",
                tint = if (pinned) SoftMint else TextPrimary,
                icon = {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (pinned) SoftMint else TextPrimary
                    )
                },
                onClick = onPin
            )
            val armed = message.ephemeral || message.burnsOnRead
            SheetAction(
                label = when {
                    message.ephemeral ->
                        "Self-destruct · burns in ${burnRemaining(message.expiresAt, now)}"
                    message.burnsOnRead -> "Self-destruct · burns once they read it"
                    else -> "Self-destruct"
                },
                tint = if (armed) SoftMint else TextPrimary,
                icon = {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = if (armed) SoftMint else TextPrimary
                    )
                },
                onClick = { burnOpen = !burnOpen }
            )
            AnimatedVisibility(visible = burnOpen) {
                Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
                    Text(
                        text = "Both devices delete it when the clock runs out. A timer can " +
                            "be shortened, never extended.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MESSAGE_BURN_OPTIONS.forEach { minutes ->
                            Text(
                                text = burnLabel(minutes),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = SignalGreen,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(SignalGreen.copy(alpha = 0.12f))
                                    .border(1.dp, SignalGreen.copy(alpha = 0.45f), CircleShape)
                                    .clickable { onBurn(minutes) }
                                    .padding(horizontal = 18.dp, vertical = 9.dp)
                            )
                        }
                    }
                    // Burn after reading only makes sense while the message is
                    // still unread: after that there is nothing left to trigger.
                    if (message.outgoing && message.readAt == 0L && !message.ephemeral) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SoftMint.copy(alpha = 0.10f))
                                .border(1.dp, SoftMint.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .clickable(onClick = onBurnOnRead)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                Icons.Outlined.LocalFireDepartment,
                                contentDescription = null,
                                tint = SoftMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Burn after reading",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SoftMint
                                )
                                Text(
                                    text = "No clock until they open it — then 30 seconds, " +
                                        "on both phones.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
            if (message.editable) {
                SheetAction(
                    label = "Edit message",
                    icon = {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = TextPrimary)
                    },
                    onClick = onEdit
                )
            }
            if (message.edited) {
                SheetAction(
                    label = "Edit history · ${message.edits.size + 1} versions",
                    icon = {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = SoftMint)
                    },
                    onClick = onHistory
                )
            }
            if (message.outgoing && message.state == DeliveryState.PENDING && message.photo == null) {
                SheetAction(
                    label = "Send again",
                    icon = {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = SignalGreen)
                    },
                    onClick = onResend
                )
            }
            if (message.outgoing) {
                SheetAction(
                    label = "Unsend for everyone",
                    tint = WarningRed,
                    icon = {
                        Icon(Icons.Outlined.Undo, contentDescription = null, tint = WarningRed)
                    },
                    onClick = onUnsend
                )
            }
            SheetAction(
                label = "Delete on this device",
                tint = WarningRed,
                icon = {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = WarningRed)
                },
                onClick = onDelete
            )
        }
    }
}

/** Picks the thread a message is copied into. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForwardSheet(
    threads: List<Thread>,
    onDismiss: () -> Unit,
    onPick: (Thread) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Forward to",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The copy is sealed again for whoever you pick — nothing is relayed " +
                    "in the clear.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                threads.sortedByDescending { it.lastActivity }.forEach { destination ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(destination) }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        if (destination.isGroup) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Canvas)
                                    .border(1.dp, SignalGreen.copy(alpha = 0.45f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Outlined.Groups,
                                    contentDescription = null,
                                    tint = SignalGreen,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        } else {
                            IdenticonAvatar(username = destination.peer, size = 38.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = destination.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            MonoKeyText(
                                text = if (destination.isGroup) {
                                    "${destination.group?.members?.size ?: 0} members"
                                } else {
                                    "key · ${destination.fingerprint}"
                                }
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** The message kept at the top of a thread, tappable to jump back to it. */
@Composable
private fun PinnedBar(
    message: Message,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Canvas)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(26.dp)
                .clip(CircleShape)
                .background(SoftMint)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = SoftMint,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "PINNED",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = SoftMint
                )
            }
            Text(
                text = message.preview,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onUnpin) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Unpin",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Every version of an edited message, oldest first. The history is kept on both
 * devices, so a quiet rewrite cannot pass for the original.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHistorySheet(
    message: Message,
    onDismiss: () -> Unit
) {
    val versions = remember(message) {
        val previous = message.edits.mapIndexed { index, edit ->
            val writtenAt = if (index == 0) message.at else message.edits[index - 1].at
            Triple(edit.text, writtenAt, edit.at)
        }
        previous + Triple(message.text, message.editedAt, 0L)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = SoftMint)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Edit history",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Rewrites are recorded on both devices — nothing changes silently.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(18.dp))
            versions.forEachIndexed { index, (text, writtenAt, replacedAt) ->
                val current = index == versions.lastIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Canvas)
                        .border(
                            width = 1.dp,
                            color = if (current) SignalGreen.copy(alpha = 0.5f) else Hairline,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                current -> "current"
                                index == 0 -> "original"
                                else -> "version ${index + 1}"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (current) SignalGreen else TextSecondary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (current) clockTime(writtenAt)
                            else "until ${clockTime(replacedAt)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current) TextPrimary else TextSecondary
                    )
                }
            }
        }
    }
}

/** When an incoming message landed, who wrote it, and when it burns. */
@Composable
private fun ArrivalCard(message: Message, now: Long, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Canvas)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = SignalGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Received",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            MonoKeyText(
                text = buildString {
                    append(exactStamp(message.at))
                    message.from?.let { append(" · @$it") }
                    if (message.ephemeral) {
                        append(" · burns in ${burnRemaining(message.expiresAt, now)}")
                    }
                }
            )
        }
    }
}

/** Exact receipt clocks for one of my own messages. */
@Composable
private fun ReceiptCard(message: Message, modifier: Modifier = Modifier) {
    val title = when (message.state) {
        DeliveryState.PENDING -> "Sending"
        DeliveryState.SENT -> "Sent"
        DeliveryState.DELIVERED -> "Delivered"
        DeliveryState.READ -> "Read"
    }
    val detail = when (message.state) {
        DeliveryState.PENDING -> "sealed on this device · not on the hub yet"
        DeliveryState.SENT -> "sent ${clockTime(message.at)} · not on their device yet"
        DeliveryState.DELIVERED -> "sent ${clockTime(message.at)} · delivered " +
            clockTime(if (message.deliveredAt > 0L) message.deliveredAt else message.at)
        DeliveryState.READ -> if (message.readAt > 0L) {
            "delivered ${clockTime(
                if (message.deliveredAt > 0L) message.deliveredAt else message.at
            )} · read ${clockTime(message.readAt)}"
        } else {
            "they opened this chat"
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Canvas)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        DeliveryTicks(state = message.state, pendingSince = message.at)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            MonoKeyText(text = detail)
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = TextPrimary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** Camera destination inside the app cache, shared through the app's FileProvider. */
private fun newCameraTarget(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "shot-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Send an attachment",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Whatever you attach is encrypted on this device with a key only this chat holds. The hub stores unreadable bytes.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp)
            )
            Spacer(Modifier.height(16.dp))
            SheetAction(
                label = "Take photo",
                icon = {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = SignalGreen
                    )
                },
                onClick = onCamera
            )
            SheetAction(
                label = "Choose from gallery",
                icon = {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = SignalGreen)
                },
                onClick = onGallery
            )
            SheetAction(
                label = "Send a file",
                icon = {
                    Icon(
                        Icons.Outlined.InsertDriveFile,
                        contentDescription = null,
                        tint = SignalGreen
                    )
                },
                onClick = onFile
            )
        }
    }
}

@Composable
private fun JumpToLatest(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceElevated)
            .border(1.dp, Hairline, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = "Jump to latest",
            tint = SignalGreen,
            modifier = Modifier.size(18.dp)
        )
        if (unread > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$unread new",
                style = MaterialTheme.typography.labelMedium,
                color = SignalGreen
            )
        }
    }
}

@Composable
private fun BurnPill(minutes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SoftMint.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = "Disappearing messages",
            tint = SoftMint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = burnLabel(minutes),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = SoftMint
        )
    }
}

@Composable
private fun BlockedBar(peer: String, onUnblock: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Canvas)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            Icons.Outlined.Block,
            contentDescription = null,
            tint = WarningRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "You blocked @$peer",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Unblock",
            style = MaterialTheme.typography.labelLarge,
            color = SignalGreen,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onUnblock)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/**
 * The peer's key is not the one it was.
 *
 * This is the only banner in Cipher that speaks before anything has gone
 * wrong, because by the time it has gone wrong it is too late to warn.
 */
@Composable
private fun KeyChangedBar(
    peer: String,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(WarningRed.copy(alpha = 0.14f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = WarningRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "@$peer's security code changed",
                style = MaterialTheme.typography.labelLarge,
                color = WarningRed
            )
            Text(
                text = "Verify before sending anything private",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Text(
            text = "Verify",
            style = MaterialTheme.typography.labelLarge,
            color = SignalGreen,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onVerify)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Dismiss",
            tint = TextSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDismiss)
                .padding(6.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun EmptyThread(
    peer: String,
    roomName: String?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 56.dp, start = 24.dp, end = 24.dp)
    ) {
        if (roomName == null) {
            IdenticonAvatar(username = peer, size = 72.dp)
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(1.dp, SignalGreen.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    tint = SignalGreen,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = roomName?.let { "Open $it" } ?: "Say something to @$peer",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (roomName != null) {
                "Everyone here gets their own sealed copy of what you write. Long-press any message to react, reply, edit or unsend."
            } else {
                "Only the two of you can read this thread. Long-press any message to react, reply, edit or unsend."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

/** A centred room event: someone joined, left, or the room was renamed. */
@Composable
private fun SystemNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun EncryptionNotice(
    fingerprint: String,
    room: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceElevated)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = SignalGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (room) {
                        "Sealed once per member · the hub never sees a room"
                    } else {
                        "End-to-end encrypted · the server stores only ciphertext"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                MonoKeyText(text = if (room) fingerprint else "Verified $fingerprint")
            }
        }
    }
}

@Composable
private fun BurnNotice(minutes: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = null,
            tint = SoftMint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "messages disappear after ${burnLabel(minutes)}",
            style = MaterialTheme.typography.labelMedium,
            color = SoftMint
        )
    }
}

/**
 * The last minute of a self-destructing message, drawn as a ring that empties.
 * Longer timers keep it full: the ring is about the part worth watching.
 */
@Composable
private fun FuseRing(
    expiresAt: Long,
    now: Long,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val remaining = (expiresAt - now).coerceAtLeast(0L)
    val target = (remaining / 60_000f).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "fuse"
    )
    val urgent = remaining in 1..10_000L
    val pulse = rememberInfiniteTransition(label = "fusePulse")
    val glow by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fuseGlow"
    )
    val alpha = if (urgent) glow else 1f
    DrawCanvas(modifier = modifier.size(11.dp)) {
        val stroke = 1.6.dp.toPx()
        drawCircle(
            color = tint.copy(alpha = 0.22f * alpha),
            radius = (size.minDimension - stroke) / 2f,
            style = Stroke(width = stroke)
        )
        drawArc(
            color = tint.copy(alpha = alpha),
            startAngle = -90f,
            sweepAngle = -360f * progress,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/** Replaces a growing share of a doomed message with noise. */
/**
 * Tints every occurrence of a search term inside a message body.
 *
 * Returns the plain text untouched when there is nothing to look for, so an
 * ordinary bubble pays nothing for the feature.
 */
private fun markMatches(text: String, needle: String, tint: Color): AnnotatedString {
    val term = needle.trim()
    if (term.length < 2 || !text.contains(term, ignoreCase = true)) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var cursor = 0
        while (cursor <= text.length) {
            val at = text.indexOf(term, cursor, ignoreCase = true)
            if (at < 0) {
                append(text.substring(cursor))
                return@buildAnnotatedString
            }
            append(text.substring(cursor, at))
            withStyle(SpanStyle(background = tint)) {
                append(text.substring(at, at + term.length))
            }
            cursor = at + term.length
        }
    }
}

/**
 * The words of a bubble, with search hits marked and "@names" lifted out.
 *
 * Your own name is drawn on a tinted plate rather than merely coloured, so
 * being addressed is visible at a glance while scrolling past a busy room.
 */
private fun paintBody(
    text: String,
    needle: String,
    marked: Color,
    roster: List<String>,
    me: String,
    tint: Color
): AnnotatedString {
    val spans = mentionSpans(text, roster)
    val base = markMatches(text, needle, marked)
    if (spans.isEmpty()) return base
    val mine = me.lowercase()
    return buildAnnotatedString {
        append(base)
        spans.forEach { span ->
            val callsMe = span.name == mine || span.name == MENTION_ALL
            addStyle(
                SpanStyle(
                    color = tint,
                    fontWeight = FontWeight.SemiBold,
                    background = if (callsMe) tint.copy(alpha = 0.18f) else Color.Transparent
                ),
                span.start,
                span.end.coerceAtMost(text.length)
            )
        }
    }
}

private fun shredText(text: String, progress: Float, step: Int): String {
    val random = kotlin.random.Random(step * 7919)
    return buildString(text.length) {
        text.forEach { char ->
            if (char.isWhitespace() || random.nextFloat() > progress) append(char)
            else append(SHRED_GLYPHS[random.nextInt(SHRED_GLYPHS.length)])
        }
    }
}

@Composable
private fun MessageBubble(
    avatarFor: String,
    senderName: String?,
    me: String,
    /** Who is in this room, so an "@name" can be told from an email address. */
    roster: List<String>,
    message: Message,
    now: Long,
    highlighted: Boolean,
    /** The search term to mark inside this bubble's text, or empty. */
    highlight: String,
    entrance: Boolean,
    continues: Boolean,
    continued: Boolean,
    showStatus: Boolean,
    loadPhoto: suspend (PhotoRef) -> ByteArray?,
    playback: VoicePlayback?,
    onVoiceToggle: (VoiceRef) -> Unit,
    onVoiceSeek: (Float) -> Unit,
    fileReady: (FileRef) -> Boolean,
    onFileOpen: (FileRef) -> Unit,
    fileBusy: String?,
    onPhotoClick: () -> Unit,
    onReply: () -> Unit,
    onShowEdits: () -> Unit,
    onLongPress: () -> Unit,
    onQuoteClick: (String) -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outgoing = message.outgoing
    val media = message.photo != null || message.voice != null ||
        message.file != null || message.uploading
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { GUTTER.toPx() }
    val peek = with(LocalDensity.current) { PEEK.toPx() }
    val swipe = remember(message.id) { Animatable(0f) }
    var armed by remember(message.id) { mutableStateOf(false) }
    var peekJob by remember(message.id) { mutableStateOf<Job?>(null) }
    val armProgress by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "arm"
    )
    val shape = RoundedCornerShape(
        topStart = if (!outgoing && continues) TIGHT else ROUND,
        topEnd = if (outgoing && continues) TIGHT else ROUND,
        bottomStart = if (!outgoing && continued) TIGHT else ROUND,
        bottomEnd = if (outgoing && continued) TIGHT else ROUND
    )
    // A bubble that lands while you are watching rises into place instead of
    // blinking in. Anything already on screen when the thread opened starts
    // settled, so opening a chat never replays the whole history.
    val appear = remember(message.id) { Animatable(if (entrance) 0f else 1f) }
    LaunchedEffect(message.id) {
        if (appear.value < 1f) {
            appear.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.74f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }
    val rise = with(LocalDensity.current) { 26.dp.toPx() }
    // A message with a deadline does not blink out of existence: it shreds,
    // dissolving its own characters into noise as the last second runs down.
    val shred = remember(message.id) { Animatable(0f) }
    LaunchedEffect(message.id, message.expiresAt) {
        if (message.expiresAt <= 0L) {
            shred.snapTo(0f)
            return@LaunchedEffect
        }
        val startAt = message.expiresAt - SHRED_MS
        val wait = startAt - System.currentTimeMillis()
        if (wait > 0L) {
            shred.snapTo(0f)
            delay(wait)
        }
        shred.animateTo(1f, tween(durationMillis = SHRED_MS.toInt(), easing = LinearEasing))
    }
    val shredding = shred.value
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val progress = appear.value
                alpha = progress.coerceIn(0f, 1f) * (1f - shredding)
                translationY = (1f - progress) * rise
                val grow = 0.92f + 0.08f * progress
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(if (outgoing) 1f else 0f, 1f)
            }
    ) {
        // Two gutters, one per direction. Pulling the bubble left uncovers the
        // clock on its right; pulling it right uncovers the reply arrow on its
        // left, in the direction the quote will travel.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(GUTTER)
                .graphicsLayer { alpha = (-swipe.value / (peek * 0.7f)).coerceIn(0f, 1f) }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clockTime(message.at),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                if (outgoing) {
                    Spacer(Modifier.height(4.dp))
                    DeliveryTicks(state = message.state, pendingSince = message.at)
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(GUTTER)
                .graphicsLayer { alpha = (swipe.value / (threshold * 0.7f)).coerceIn(0f, 1f) }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = 0.62f + 0.38f * armProgress
                        scaleY = 0.62f + 0.38f * armProgress
                    }
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    tint = SignalGreen.copy(alpha = 0.45f + 0.55f * armProgress),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(swipe.value.roundToInt(), 0) }
            .padding(top = if (continues) 1.dp else 5.dp, bottom = 1.dp)
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        peekJob?.cancel()
                        peekJob = null
                    },
                    onDragEnd = {
                        val travelled = swipe.value
                        armed = false
                        when {
                            // Right: far enough to reply.
                            travelled >= threshold -> {
                                onReply()
                                scope.launch { swipe.animateTo(0f, spring()) }
                            }
                            // Left: a request to read the clock, so hold the
                            // gutter open long enough to actually read it.
                            travelled < -peek * 0.3f -> {
                                peekJob = scope.launch {
                                    swipe.animateTo(
                                        -peek,
                                        spring(dampingRatio = 0.72f)
                                    )
                                    delay(PEEK_HOLD_MS)
                                    swipe.animateTo(0f, spring())
                                }
                            }
                            else -> scope.launch { swipe.animateTo(0f, spring()) }
                        }
                    },
                    onDragCancel = {
                        armed = false
                        scope.launch { swipe.animateTo(0f, spring()) }
                    }
                ) { _, delta ->
                    val next = (swipe.value + delta * 0.75f)
                        .coerceIn(-peek * 1.15f, threshold * 1.3f)
                    scope.launch { swipe.snapTo(next) }
                    if (!armed && next >= threshold) {
                        armed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (armed && next < threshold) {
                        armed = false
                    }
                }
            },
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!outgoing) {
            // Only the last bubble of a stack wears the avatar, so a run of
            // messages reads as one block instead of a column of faces.
            if (continued) Spacer(Modifier.width(30.dp))
            else IdenticonAvatar(username = avatarFor, size = 30.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start) {
            if (senderName != null) {
                Text(
                    text = "@$senderName",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = SoftMint,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 288.dp)
                    .clip(shape)
                    .background(if (outgoing) SignalGreen else SurfaceElevated)
                    .border(
                        width = if (highlighted) 2.dp else 0.dp,
                        color = if (highlighted) SoftMint else androidx.compose.ui.graphics.Color.Transparent,
                        shape = shape
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                        onDoubleClick = onDoubleTap
                    )
                    .padding(
                        horizontal = if (media) 6.dp else 14.dp,
                        vertical = if (media) 6.dp else 10.dp
                    )
            ) {
                val inset = if (media) 8.dp else 0.dp
                val quote = message.replyText
                if (quote != null) {
                    QuoteBlock(
                        text = quote,
                        outgoing = outgoing,
                        onClick = { message.replyTo?.let(onQuoteClick) },
                        modifier = Modifier.padding(start = inset, end = inset, top = inset)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val photo = message.photo
                val voice = message.voice
                val attachment = message.file
                if (attachment != null) {
                    FileCard(
                        file = attachment,
                        outgoing = outgoing,
                        ready = fileReady(attachment),
                        loading = fileBusy == attachment.blob,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .combinedClickable(
                                onClick = { onFileOpen(attachment) },
                                onLongClick = onLongPress
                            )
                    )
                } else if (voice != null) {
                    VoiceNote(
                        ref = voice,
                        outgoing = outgoing,
                        playback = playback?.takeIf { it.blob == voice.blob },
                        onToggle = { onVoiceToggle(voice) },
                        onSeek = onVoiceSeek,
                        onLongPress = onLongPress,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                } else if (photo != null) {
                    EncryptedPhoto(
                        ref = photo,
                        load = loadPhoto,
                        onClick = onPhotoClick,
                        onLongClick = onLongPress
                    )
                } else if (message.uploading) {
                    SealingPlaceholder()
                }
                if (message.text.isNotBlank()) {
                    if (media) Spacer(Modifier.height(8.dp))
                    if (message.secret) {
                        HiddenText(
                            text = message.text,
                            outgoing = outgoing,
                            onLongPress = onLongPress,
                            modifier = Modifier.padding(horizontal = inset)
                        )
                    } else {
                        val step = (shredding * 12f).toInt()
                        val body = remember(message.text, step) {
                            if (step == 0) message.text
                            else shredText(message.text, shredding, step)
                        }
                        // A search hit is marked in the words themselves, not
                        // just by scrolling the bubble into view.
                        val marked = if (outgoing) OnSignal.copy(alpha = 0.26f)
                        else SignalGreen.copy(alpha = 0.3f)
                        val mentionTint = if (outgoing) OnSignal else SignalGreen
                        val painted = remember(body, highlight, marked, roster, me) {
                            paintBody(
                                text = body,
                                needle = highlight,
                                marked = marked,
                                roster = roster,
                                me = me,
                                tint = mentionTint
                            )
                        }
                        Text(
                            text = painted,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (outgoing) OnSignal else TextPrimary,
                            modifier = Modifier.padding(horizontal = inset)
                        )
                    }
                }
                // No clock lives in the bubble: the time belongs to the gutter
                // you pull open. Only a countdown or an edit trail earns a line.
                if (message.ephemeral || message.burnsOnRead || message.edited) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(
                            start = inset,
                            end = inset,
                            bottom = if (media) 4.dp else 0.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.ephemeral) {
                        val fuseTint = if (outgoing) OnSignal.copy(alpha = 0.8f) else SoftMint
                        FuseRing(
                            expiresAt = message.expiresAt,
                            now = now,
                            tint = fuseTint
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = burnRemaining(message.expiresAt, now),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = fuseTint
                        )
                        Spacer(Modifier.width(8.dp))
                    } else if (message.burnsOnRead) {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = "burns after reading",
                            tint = if (outgoing) OnSignal.copy(alpha = 0.75f) else SoftMint,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (outgoing) "burns when read" else "burns when you read it",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (outgoing) OnSignal.copy(alpha = 0.75f) else SoftMint
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (message.edited) {
                        Text(
                            text = "edited",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (outgoing) OnSignal.copy(alpha = 0.75f) else SoftMint,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onShowEdits)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                }
            }
            if (message.reactions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                ReactionRow(reactions = message.reactions, me = me)
            }
            // Only my newest message states where it got to, the way a receipt
            // line does — the rest keep their ticks in the gutter.
            if (outgoing && showStatus) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when (message.state) {
                        DeliveryState.PENDING -> "sending"
                        DeliveryState.SENT -> "sent"
                        DeliveryState.DELIVERED -> "delivered"
                        DeliveryState.READ -> "seen"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (message.state == DeliveryState.READ) SignalGreen
                    else TextSecondary,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
        }
    }
}

private const val SCRAMBLE = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789#%&/\\|+*"

/** How long a hidden message stays readable after it is tapped. */
private const val REVEAL_MS = 6_000L

/**
 * A hidden message: ciphertext-looking noise that resolves character by
 * character when tapped, then scrambles itself again after a few seconds.
 * The real text is never rendered while hidden, so a screenshot or a glance
 * over the shoulder gets nothing — the blur alone would not be enough.
 *
 * The churning glyphs say "hidden" on their own, so there is no caption under
 * them. [onLongPress] is carried here rather than left to the bubble behind:
 * an inner tap handler swallows the gesture, which is what used to make a
 * hidden message impossible to delete.
 */
@Composable
private fun HiddenText(
    text: String,
    outgoing: Boolean,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var revealed by remember(text) { mutableStateOf(false) }
    var shown by remember(text) { mutableStateOf(noiseFor(text, 0)) }

    LaunchedEffect(revealed, text) {
        if (revealed) {
            for (resolved in 0..text.length) {
                shown = noiseFor(text, resolved)
                delay(16)
            }
            shown = text
            delay(REVEAL_MS)
            revealed = false
        } else {
            while (true) {
                shown = noiseFor(text, 0)
                delay(110)
            }
        }
    }

    val tint = if (outgoing) OnSignal else TextPrimary
    Text(
        text = shown,
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Monospace,
        color = if (revealed) tint else tint.copy(alpha = 0.75f),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = { revealed = !revealed },
                onLongClick = onLongPress
            )
            .blur(if (revealed) 0.dp else 2.dp)
    )
}

/** Keeps layout stable: same length, same spaces, only the glyphs churn. */
private fun noiseFor(text: String, resolved: Int): String = buildString {
    text.forEachIndexed { index, char ->
        when {
            index < resolved || char.isWhitespace() -> append(char)
            else -> append(SCRAMBLE[(0 until SCRAMBLE.length).random()])
        }
    }
}

/** In-chat search: a mono field plus older / newer match steppers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    index: Int,
    total: Int,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Built as a real app bar rather than a hand-rolled row: it stands in for the
    // chat header, so it has to inherit the same status bar inset and height, and
    // owning those by hand is exactly how it ended up under the clock.
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Canvas,
            titleContentColor = TextPrimary
        ),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Close search",
                    tint = TextPrimary
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, Hairline, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search this chat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush = SolidColor(SignalGreen),
                        keyboardOptions = KeyboardOptions.Default,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                    )
                }
                if (query.trim().length >= 2) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (total == 0) "none" else "${index + 1}/$total",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (total == 0) TextSecondary else SoftMint
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOlder, enabled = total > 0 && index > 0) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Older match",
                    tint = if (total > 0 && index > 0) SignalGreen else TextSecondary
                )
            }
            IconButton(onClick = onNewer, enabled = total > 0 && index < total - 1) {
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Newer match",
                    tint = if (total > 0 && index < total - 1) SignalGreen else TextSecondary
                )
            }
        }
    )
}

@Composable
private fun ReactionRow(
    reactions: Map<String, String>,
    me: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        reactions.entries.take(6).forEach { (user, emoji) ->
            val mine = user == me
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (mine) SignalGreen.copy(alpha = 0.2f) else SurfaceElevated)
                    .border(
                        width = 1.dp,
                        color = if (mine) SignalGreen else Hairline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(text = emoji, fontSize = 12.sp)
            }
        }
    }
}

/** Shown while a picked image is being compressed, encrypted and uploaded. */
@Composable
private fun SealingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(248.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Canvas.copy(alpha = 0.35f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            UploadingBadge()
            Spacer(Modifier.height(8.dp))
            MonoKeyText(text = "sealing photo")
        }
    }
}

@Composable
private fun QuoteBlock(
    text: String,
    outgoing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (outgoing) OnSignal.copy(alpha = 0.55f) else SoftMint
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (outgoing) OnSignal.copy(alpha = 0.12f) else Canvas.copy(alpha = 0.7f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (outgoing) OnSignal.copy(alpha = 0.85f) else TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Composer(
    value: TextFieldValue,
    /** Room-mates who can be called out with an "@". Empty in a private chat. */
    mentionable: List<String>,
    replyTo: Message?,
    editing: Message?,
    threadBurnMinutes: Int?,
    secret: Boolean,
    scheduled: List<ScheduledMessage>,
    now: Long,
    onValueChange: (TextFieldValue) -> Unit,
    onClearReply: () -> Unit,
    onClearEdit: () -> Unit,
    onAttach: () -> Unit,
    onToggleSecret: () -> Unit,
    onSchedule: () -> Unit,
    onOpenScheduled: () -> Unit,
    onSend: () -> Unit,
    recording: Boolean,
    recordElapsedMs: Long,
    recordLevel: Int,
    cancelling: Boolean,
    onHoldStart: () -> Unit,
    onHoldSlide: (Float) -> Unit,
    onHoldRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = value.text
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sendScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(),
        label = "send"
    )
    val canSend = draft.isNotBlank()
    // An empty composer offers the microphone instead of a dead send button.
    val holdToTalk = !canSend && editing == null
    val cancelPx = with(LocalDensity.current) { CANCEL_SLIDE.toPx() }
    val micScale by animateFloatAsState(
        targetValue = if (recording) 1.22f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "mic"
    )
    val secretFill by animateColorAsState(
        targetValue = if (secret) SignalGreen else Color.Transparent,
        animationSpec = tween(180),
        label = "secretFill"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Canvas)
            .navigationBarsPadding()
    ) {
        AnimatedVisibility(visible = editing != null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = SoftMint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Editing message",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoftMint
                    )
                    Text(
                        text = editing?.text.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClearEdit) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Cancel edit",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = replyTo != null && editing == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(SignalGreen)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (replyTo?.outgoing == true) "Replying to yourself"
                        else "Replying",
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalGreen
                    )
                    Text(
                        text = replyTo?.preview.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClearReply) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Cancel reply",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = scheduled.isNotEmpty(),
            enter = fadeIn(tween(160)) + expandVertically(spring(dampingRatio = 0.85f)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
        ) {
            ScheduledStrip(items = scheduled, now = now, onClick = onOpenScheduled)
        }
        // Typing "@" in a room offers the people in it. The strip exists only
        // while a name is genuinely being written, so it never sits between the
        // conversation and the keyboard uninvited.
        val mention = remember(value, mentionable, recording) {
            if (mentionable.isEmpty() || recording) null
            else mentionCandidates(value, mentionable)
        }
        AnimatedVisibility(
            visible = mention != null,
            enter = fadeIn(tween(120)) + expandVertically(spring(dampingRatio = 0.86f)),
            exit = fadeOut(tween(100)) + shrinkVertically(tween(140))
        ) {
            MentionStrip(
                names = mention?.names.orEmpty(),
                onPick = { name ->
                    val query = mention ?: return@MentionStrip
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onValueChange(applyMention(value, query.at, query.caret, name))
                }
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 148.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(SurfaceElevated)
                    .border(
                        1.dp,
                        if (recording) WarningRed.copy(alpha = 0.5f) else Hairline,
                        RoundedCornerShape(26.dp)
                    )
                    .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp)
            ) {
                if (recording) {
                    RecordingBar(
                        elapsedMs = recordElapsedMs,
                        level = recordLevel,
                        cancelling = cancelling
                    )
                    return@Row
                }
                if (editing == null) {
                    IconButton(onClick = onAttach, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "Send a photo",
                            tint = SignalGreen,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                if (editing == null) IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSecret()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(secretFill)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = if (secret) "Send this one openly"
                            else "Hide this message until they tap it",
                            tint = if (secret) OnSignal else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(
                            text = when {
                                editing != null -> "Rewrite this message"
                                secret -> "Hidden until they tap it"
                                threadBurnMinutes != null ->
                                    "Disappears after ${burnLabel(threadBurnMinutes)}"
                                else -> "Encrypted message"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (secret) SoftMint else TextSecondary
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                        cursorBrush = SolidColor(SignalGreen),
                        keyboardOptions = KeyboardOptions.Default,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (holdToTalk) {
                // Press and hold to talk, release to send, slide away to bin it.
                // The whole gesture lives on one button so a voice message never
                // needs a second tap to confirm.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .background(
                            when {
                                cancelling -> WarningRed
                                recording -> SignalGreen
                                else -> SurfaceElevated
                            }
                        )
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                onHoldStart()
                                var travelled = 0f
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes
                                        .firstOrNull { it.id == down.id } ?: break
                                    travelled = change.position.x - down.position.x
                                    onHoldSlide(travelled)
                                    if (change.changedToUp() || !change.pressed) break
                                }
                                onHoldRelease()
                            }
                        }
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = "Hold to record a voice message",
                        tint = when {
                            cancelling -> OnSignal
                            recording -> OnSignal
                            else -> SignalGreen
                        },
                        modifier = Modifier.size(21.dp)
                    )
                }
            } else {
                // Hold the send button to choose when it leaves instead of now.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .scale(sendScale)
                        .clip(CircleShape)
                        .background(if (canSend) SignalGreen else SurfaceElevated)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = ripple(),
                            enabled = canSend,
                            onLongClickLabel = "Schedule this message",
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSchedule()
                            },
                            onClick = onSend
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) OnSignal else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** An unfinished "@name" under the caret, and who it could still become. */
private data class MentionQuery(val at: Int, val caret: Int, val names: List<String>)

/**
 * Reads the "@" token the caret is sitting in, if there is one.
 *
 * Only a token being written counts: the caret has to be inside it and the "@"
 * cannot be glued to a preceding word, so an email address is left alone. A
 * finished name followed by a space closes the strip on its own.
 */
private fun mentionCandidates(value: TextFieldValue, members: List<String>): MentionQuery? {
    if (!value.selection.collapsed) return null
    val caret = value.selection.start
    val text = value.text
    if (caret == 0 || caret > text.length) return null
    var start = caret - 1
    while (start >= 0 && text[start] != '@') {
        val char = text[start]
        if (!char.isLetterOrDigit() && char != '.' && char != '_') return null
        start--
    }
    if (start < 0) return null
    if (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '@')) return null
    val query = text.substring(start + 1, caret).lowercase()
    if (query.length > 21) return null
    val names = members.filter { it.startsWith(query) } +
        listOfNotNull(MENTION_ALL.takeIf { it.startsWith(query) || "all".startsWith(query) })
    if (names.isEmpty()) return null
    return MentionQuery(at = start, caret = caret, names = names)
}

/** Replaces the half-typed token with the chosen name and a trailing space. */
private fun applyMention(
    value: TextFieldValue,
    at: Int,
    caret: Int,
    name: String
): TextFieldValue {
    val inserted = "@$name "
    val text = value.text.take(at) + inserted + value.text.substring(caret)
    return TextFieldValue(text = text, selection = TextRange(at + inserted.length))
}

@Composable
private fun MentionStrip(
    names: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(items = names, key = { it }) { name ->
            val everyone = name == MENTION_ALL
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (everyone) SignalGreen.copy(alpha = 0.16f) else SurfaceElevated)
                    .border(
                        1.dp,
                        if (everyone) SignalGreen.copy(alpha = 0.45f) else Hairline,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onPick(name) }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
            ) {
                if (everyone) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(SignalGreen.copy(alpha = 0.22f))
                    ) {
                        Icon(
                            Icons.Outlined.AlternateEmail,
                            contentDescription = null,
                            tint = SignalGreen,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else {
                    IdenticonAvatar(username = name, size = 26.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (everyone) "Everyone" else "@$name",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (everyone) SignalGreen else TextPrimary
                )
            }
        }
    }
}

/** The queue sitting above the composer: what is written but has not left yet. */
@Composable
private fun ScheduledStrip(
    items: List<ScheduledMessage>,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val next = items.minByOrNull { it.at } ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SignalGreen.copy(alpha = 0.10f))
            .border(1.dp, SignalGreen.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = SignalGreen,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Leaves ${scheduleStamp(next.at, now)}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = SignalGreen
            )
            Text(
                text = if (next.secret) "Hidden message" else next.text,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (items.size > 1) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "+${items.size - 1}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = SoftMint
            )
        }
    }
}

/** Quick departures plus a dial, for a message that should not leave yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSheet(
    preview: String,
    now: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    var custom by remember { mutableStateOf(false) }
    val options = remember(now) { scheduleOptions(now) }
    val clockState = rememberTimePickerState(
        initialHour = hourOf(now + 3_600_000L),
        initialMinute = 0,
        is24Hour = true
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Send later",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "It waits encrypted on this phone and leaves on its own. " +
                    "The hub is told nothing until then.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Canvas)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(SignalGreen)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            if (custom) {
                TimePicker(
                    state = clockState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Canvas,
                        clockDialSelectedContentColor = OnSignal,
                        clockDialUnselectedContentColor = TextPrimary,
                        selectorColor = SignalGreen,
                        containerColor = SurfaceElevated,
                        periodSelectorBorderColor = Hairline,
                        periodSelectorSelectedContainerColor = SignalGreen.copy(alpha = 0.18f),
                        periodSelectorUnselectedContainerColor = Canvas,
                        periodSelectorSelectedContentColor = SignalGreen,
                        periodSelectorUnselectedContentColor = TextSecondary,
                        timeSelectorSelectedContainerColor = SignalGreen.copy(alpha = 0.18f),
                        timeSelectorUnselectedContainerColor = Canvas,
                        timeSelectorSelectedContentColor = SignalGreen,
                        timeSelectorUnselectedContentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 24.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .border(1.dp, Hairline, CircleShape)
                            .clickable { custom = false }
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    )
                    Text(
                        text = "Schedule",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSignal,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(SignalGreen)
                            .clickable {
                                onPick(nextOccurrence(clockState.hour, clockState.minute))
                            }
                            .padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                options.forEach { option ->
                    SheetAction(
                        label = option.label,
                        icon = {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = SignalGreen
                            )
                        },
                        onClick = { onPick(option.at) }
                    )
                }
                SheetAction(
                    label = "Pick a time",
                    tint = SoftMint,
                    icon = {
                        Icon(Icons.Outlined.Timer, contentDescription = null, tint = SoftMint)
                    },
                    onClick = { custom = true }
                )
            }
        }
    }
}

/** Everything queued for this thread, with a way to hurry it or call it off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledSheet(
    items: List<ScheduledMessage>,
    now: Long,
    onDismiss: () -> Unit,
    onSendNow: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Waiting to send",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "These have not left this phone yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(14.dp))
            items.sortedBy { it.at }.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = scheduleStamp(item.at, now),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = SignalGreen
                            )
                            if (item.secret) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Outlined.VisibilityOff,
                                    contentDescription = "Hidden message",
                                    tint = SoftMint,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            if (item.burnMinutes != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "burns ${burnLabel(item.burnMinutes)}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = SoftMint
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { onSendNow(item.id) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send now",
                            tint = SignalGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { onCancel(item.id) }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Cancel",
                            tint = WarningRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class ScheduleOption(val label: String, val at: Long)

/** The handful of departures worth offering without opening a dial. */
private fun scheduleOptions(now: Long): List<ScheduleOption> {
    val quarter = now + 15 * 60_000L
    val hour = now + 60 * 60_000L
    val evening = atClock(now, 21, 0)
    val morning = atClock(now + 86_400_000L, 9, 0)
    return buildList {
        add(ScheduleOption("In 15 minutes · ${clockTime(quarter)}", quarter))
        add(ScheduleOption("In an hour · ${clockTime(hour)}", hour))
        if (evening > now + 10 * 60_000L) {
            add(ScheduleOption("This evening · ${clockTime(evening)}", evening))
        }
        add(ScheduleOption("Tomorrow morning · ${clockTime(morning)}", morning))
    }
}

private fun atClock(base: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = base
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun hourOf(at: Long): Int = Calendar.getInstance()
    .apply { timeInMillis = at }
    .get(Calendar.HOUR_OF_DAY)

/** The next time the clock reads this, today if it is still ahead of us. */
private fun nextOccurrence(hour: Int, minute: Int): Long {
    val now = System.currentTimeMillis()
    val today = atClock(now, hour, minute)
    return if (today > now + 30_000L) today else atClock(now + 86_400_000L, hour, minute)
}

/** "at 21:04", "tomorrow 09:00" — how a queued message says when it leaves. */
private fun scheduleStamp(at: Long, now: Long): String = when {
    dayBucket(at) == dayBucket(now) -> "at ${clockTime(at)}"
    dayBucket(at) == dayBucket(now + 86_400_000L) -> "tomorrow ${clockTime(at)}"
    else -> "${daySeparator(at)} · ${clockTime(at)}"
}
