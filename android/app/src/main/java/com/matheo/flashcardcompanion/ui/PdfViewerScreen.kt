package com.matheo.flashcardcompanion.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheo.flashcardcompanion.AppViewModel
import com.matheo.flashcardcompanion.ai.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A course PDF, rendered with the platform's own [PdfRenderer] — no PDF library
 * and no viewer JS — plus a chat that stays grounded on the page being read.
 *
 * Pages are rendered lazily and cached, because rasterising a 60-page deck up
 * front would stall the screen and hold tens of megabytes of bitmaps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(vm: AppViewModel, absPath: String, onBack: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val file = remember(absPath) { File(absPath) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var descriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    val widthPx = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp - PagePadding * 2).roundToPx()
    }

    DisposableEffect(absPath) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor = pfd
            val r = PdfRenderer(pfd)
            renderer = r
            pageCount = r.pageCount
        }.onFailure { failed = true }
        onDispose {
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
            renderer = null
            descriptor = null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = PagePadding)) {
        AppHeader(title = file.nameWithoutExtension, onBack = onBack) {
            if (pageCount > 0) {
                Text(
                    t.t("pdf.page", mapOf("n" to currentPage, "total" to pageCount)),
                    fontFamily = FcType.mono,
                    fontSize = 11.sp,
                    color = c.muted,
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                failed -> Text(t.t("pdf.loadError"), color = c.degrBandInk, fontSize = 14.sp)
                renderer == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.accent)
                else -> LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(pageCount) { index ->
                        PdfPage(renderer!!, index, widthPx)
                    }
                }
            }

            if (!failed && renderer != null) {
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 18.dp)
                        .pressScale(interaction)
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.ctaBg)
                        .clickableNoRipple(interaction) { helpOpen = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = c.ctaInk, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.t("pdf.helpFab"), color = c.ctaInk, fontSize = 13.sp)
                }
            }
        }
    }

    if (helpOpen) {
        PdfHelpSheet(vm, file, currentPage) { helpOpen = false }
    }
}

/** One page, rasterised on demand at the screen's width. */
@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int, widthPx: Int) {
    val c = LocalFcColors.current
    var bitmap by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index, widthPx) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                // PdfRenderer allows only one open page at a time across the
                // whole renderer, so every access is serialised on it.
                synchronized(renderer) {
                    renderer.openPage(index).use { page ->
                        val height = (widthPx.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }.getOrNull()
        }
    }

    val bmp = bitmap
    if (bmp == null) {
        Box(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceSunk))
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/** A conversation about the open PDF, re-grounded on the visible page each turn. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfHelpSheet(vm: AppViewModel, file: File, page: Int, onDismiss: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var history by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surface) {
        Column(Modifier.fillMaxHeight(0.88f).padding(horizontal = 18.dp)) {
            Text(
                t.t("pdf.helpFab"),
                fontFamily = FcType.serif,
                fontSize = 20.sp,
                color = c.ink,
            )
            Spacer(Modifier.height(10.dp))

            Box(Modifier.weight(1f)) {
                if (history.isEmpty() && !busy) {
                    Text(t.t("pdf.noCourse"), color = c.muted, fontSize = 13.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(history.size) { i ->
                            val m = history[i]
                            val mine = m.role == "user"
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (mine) c.accentSoft else c.surfaceSunk)
                                    .padding(12.dp)
                            ) {
                                if (mine) {
                                    Text(m.content, color = c.ink, fontSize = 14.sp)
                                } else {
                                    MathText(
                                        html = renderMarkdown(m.content),
                                        modifier = Modifier.fillMaxWidth().height(260.dp),
                                        fontSizeSp = 14,
                                        serif = false,
                                    )
                                }
                            }
                        }
                        if (busy) {
                            item {
                                Text(t.t("pdf.loading"), color = c.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = c.degrBandInk, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(t.t("pdf.askPlaceholder")) },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                )
                Spacer(Modifier.width(8.dp))
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(48.dp)
                        .pressScale(interaction)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (input.isBlank() || busy) c.muted2 else c.ctaBg)
                        .clickableNoRipple(interaction) {
                            if (input.isBlank() || busy) return@clickableNoRipple
                            val question = input.trim()
                            input = ""
                            error = null
                            history = history + ChatMessage("user", question)
                            busy = true
                            scope.launch {
                                runCatching { vm.explainService.pdfHelp(file, history, t.lang, page) }
                                    .onSuccess { history = history + ChatMessage("assistant", it) }
                                    .onFailure {
                                        error = if (vm.repo.prefs.apiKey.isBlank()) t.t("ai.noKey")
                                        else t.t("pdf.helpError")
                                    }
                                busy = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = c.ctaInk, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
