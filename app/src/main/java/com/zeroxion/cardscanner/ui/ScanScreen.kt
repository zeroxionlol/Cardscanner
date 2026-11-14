package com.zeroxion.cardscanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroxion.cardscanner.MainViewModel
import com.zeroxion.cardscanner.ScanState
import com.zeroxion.cardscanner.data.CardBrief
import com.zeroxion.cardscanner.data.CardInfo
import com.zeroxion.cardscanner.data.VARIANT_HOLO
import com.zeroxion.cardscanner.data.VARIANT_NORMAL
import java.io.File

@Composable
fun ScanScreen(vm: MainViewModel, padding: PaddingValues) {
    val state by vm.scan.collectAsStateWithLifecycle()
    when (val s = state) {
        is ScanState.Camera -> CameraPane(vm, padding)
        is ScanState.Working -> WorkingPane(s, padding)
        is ScanState.Result -> ResultPane(vm, s, padding)
    }
}

// ---------------------------------------------------------------- Kamera

@Composable
private fun CameraPane(vm: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val lang by vm.lang.collectAsStateWithLifecycle()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onImage(uri)
    }
    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(2048, 1536),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .build()
    }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun capture() {
        if (busy) return
        busy = true
        val file = File(context.cacheDir, "capture.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    busy = false
                    vm.onImage(Uri.fromFile(file))
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    vm.notify("Das Foto konnte nicht aufgenommen werden.")
                }
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (granted) {
            CameraPreview(imageCapture = imageCapture, torch = torch, modifier = Modifier.fillMaxSize())
            FrameOverlay()
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Kamera nicht freigegeben", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Erlaube den Kamerazugriff, um Karten zu fotografieren – oder wähle ein Foto aus der Galerie.",
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { permission.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(50),
                ) {
                    Text("Kamera erlauben")
                }
            }
        }

        // Kopfzeile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = padding.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Karte scannen", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Karte in den Rahmen legen, gerade und ohne Spiegelung",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            Text(
                text = if (lang == "de") "Karten: DE" else "Karten: EN",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { vm.toggleLang() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // Bedienleiste
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = padding.calculateBottomPadding() + 24.dp, start = 32.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundControl(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Foto aus Galerie", tint = Color.White)
            }

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (granted && !busy) Gold else Color.Gray)
                    .clickable(enabled = granted && !busy) { capture() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 3.dp,
                        color = Color.White,
                    )
                }
            }

            RoundControl(onClick = { torch = !torch }, enabled = granted) {
                Icon(
                    if (torch) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = "Licht",
                    tint = if (torch) Gold else Color.White,
                )
            }
        }
    }
}

@Composable
private fun RoundControl(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun CameraPreview(imageCapture: ImageCapture, torch: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            } catch (e: Exception) {
                camera = null
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { if (future.isDone) future.get().unbindAll() }
        }
    }

    LaunchedEffect(torch, camera) {
        val cam = camera
        if (cam != null && cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(torch)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Abgedunkelter Rand mit einem Ausschnitt im Kartenformat (63 × 88). */
@Composable
private fun FrameOverlay() {
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val w = minOf(size.width * 0.8f, size.height * 0.58f * 63f / 88f)
        val h = w * 88f / 63f
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f - size.height * 0.02f
        val radius = CornerRadius(w * 0.055f)
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = Color.Black,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = radius,
            blendMode = BlendMode.Clear,
        )
        drawRoundRect(
            color = Gold,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = radius,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

// ---------------------------------------------------------------- Auswertung läuft

@Composable
private fun WorkingPane(state: ScanState.Working, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.photo != null) {
            CardImage(
                model = state.photo,
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(63f / 88f)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(28.dp))
        }
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(state.step, style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------------------------------------------------------- Ergebnis

@Composable
private fun ResultPane(vm: MainViewModel, state: ScanState.Result, padding: PaddingValues) {
    val keyboard = LocalSoftwareKeyboardController.current
    val selected = state.selected
    var variant by remember(selected?.id) { mutableStateOf(selected?.defaultVariant ?: VARIANT_NORMAL) }
    var query by rememberSaveable(state.photo.path) { mutableStateOf(state.parsed.names.firstOrNull() ?: "") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "SCAN-ERGEBNIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (selected != null) "Ist das deine Karte?" else "Karte nicht erkannt",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            IconButton(onClick = { vm.discardScan() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Verwerfen")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.notice != null) {
                item {
                    Text(
                        state.notice,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(14.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        CardImage(
                            model = state.photo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(63f / 88f)
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Dein Foto",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        CardImage(
                            model = selected?.imageUrl(high = true),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(63f / 88f)
                                .clip(RoundedCornerShape(18.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Erkannte Karte",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (selected != null) {
                item {
                    Column {
                        Text(selected.name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            listOfNotNull(
                                selected.setName.takeIf { it.isNotBlank() },
                                selected.numberLabel.takeIf { it.isNotBlank() },
                                selected.rarity,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    PricePanel(
                        prices = selected.prices(variant),
                        updated = formatIsoDay(selected.priceUpdated),
                    )
                }
                if (selected.hasNormal && selected.hasHolo) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Variante",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilterChip(
                                selected = variant == VARIANT_NORMAL,
                                onClick = { variant = VARIANT_NORMAL },
                                label = { Text("Normal") },
                                shape = RoundedCornerShape(50),
                            )
                            FilterChip(
                                selected = variant == VARIANT_HOLO,
                                onClick = { variant = VARIANT_HOLO },
                                label = { Text("Holo / Reverse") },
                                shape = RoundedCornerShape(50),
                            )
                        }
                    }
                }
            }

            if (state.candidates.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Andere Treffer", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.candidates, key = { it.id }) { card ->
                                CandidateChip(
                                    card = card,
                                    selected = card.id == selected?.id,
                                    onClick = { vm.selectCandidate(card) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nicht dabei? Nach Namen suchen", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        placeholder = { Text("z. B. Glurak ex") },
                        trailingIcon = {
                            if (state.searching) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = {
                                    keyboard?.hide()
                                    vm.search(query)
                                }) {
                                    Icon(Icons.Rounded.Search, contentDescription = "Suchen")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            vm.search(query)
                        }),
                    )
                    if (state.searchResults.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.searchResults, key = { it.id }) { brief ->
                                BriefChip(brief = brief, onClick = { vm.pickSearchResult(brief) })
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = padding.calculateBottomPadding() + 12.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { vm.discardScan() },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Text("Neu scannen")
            }
            Button(
                onClick = { vm.addSelected(variant) },
                enabled = selected != null,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1.4f)
                    .height(52.dp),
            ) {
                Text("Zur Sammlung", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CandidateChip(card: CardInfo, selected: Boolean, onClick: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(2.dp, outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        CardImage(
            model = card.imageUrl(),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(63f / 88f)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(card.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            card.setName.ifBlank { card.id },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatEuro(card.prices(card.defaultVariant)?.headline),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BriefChip(brief: CardBrief, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        CardImage(
            model = brief.imageUrl,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(63f / 88f)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(brief.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            brief.id,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
