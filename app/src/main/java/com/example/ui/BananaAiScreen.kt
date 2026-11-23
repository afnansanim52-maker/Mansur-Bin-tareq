package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.BananaAiViewModel
import com.example.ai.BananaAspectOption
import com.example.ai.BananaGenerationRecord
import com.example.ai.BananaMode
import com.example.ai.BananaSizeOption
import com.example.ai.BananaUiState
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.LocalVitRemoteColors
import com.example.ui.theme.MonoFontFamily
import com.example.util.BananaImageStorageHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BananaAiScreen(
    viewModel: BananaAiViewModel,
    strings: AppStrings,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val extColors = LocalVitRemoteColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val aspectOption by viewModel.aspectOption.collectAsStateWithLifecycle()
    val sizeOption by viewModel.sizeOption.collectAsStateWithLifecycle()
    val selectedSourceBitmap by viewModel.selectedSourceBitmap.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var promptText by rememberSaveable { mutableStateOf("") }
    var isSavingImage by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker (Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectSourceUri(context, uri)
        }
    }

    val isLoading = uiState is BananaUiState.Loading
    var loadingSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            loadingSeconds = 0
            while (true) {
                delay(1000L)
                loadingSeconds += 1
            }
        } else {
            loadingSeconds = 0
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("banana_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.close
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = extColors.warningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = strings.bananaTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = extColors.warningAmberContainer,
                        border = BorderStroke(1.dp, extColors.warningAmber.copy(alpha = 0.45f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = strings.bananaBadgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = extColors.warningAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                // 1. Mode Switcher (Create Image vs Edit Photo)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModeSelectorCard(
                            selected = mode == BananaMode.CREATE,
                            label = strings.bananaModeCreate,
                            icon = Icons.Filled.AutoAwesome,
                            accentColor = extColors.warningAmber,
                            testTag = "banana_mode_create",
                            onClick = { viewModel.setMode(BananaMode.CREATE) },
                            modifier = Modifier.weight(1f)
                        )
                        ModeSelectorCard(
                            selected = mode == BananaMode.EDIT,
                            label = strings.bananaModeEdit,
                            icon = Icons.Filled.AutoFixHigh,
                            accentColor = extColors.accentCyan,
                            testTag = "banana_mode_edit",
                            onClick = { viewModel.setMode(BananaMode.EDIT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 2. Source Photo Picker Card (when in Edit Mode)
                item {
                    AnimatedVisibility(visible = mode == BananaMode.EDIT) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, extColors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val source = selectedSourceBitmap
                                if (source != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.background)
                                    ) {
                                        Image(
                                            bitmap = source.asImageBitmap(),
                                            contentDescription = strings.bananaChangePhoto,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        IconButton(
                                            onClick = { viewModel.clearSourceBitmap() },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.65f))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = strings.close,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("banana_change_photo_button")
                                    ) {
                                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(strings.bananaChangePhoto)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, extColors.accentCyan.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .testTag("banana_pick_photo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = extColors.accentCyan
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = strings.bananaPickPhoto,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Prompt Input, Aspect Ratio, Resolution & Generate CTA
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, extColors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = promptText,
                                onValueChange = { promptText = it },
                                placeholder = {
                                    Text(
                                        text = if (mode == BananaMode.CREATE) {
                                            strings.bananaPromptHintCreate
                                        } else {
                                            strings.bananaPromptHintEdit
                                        },
                                        color = extColors.subtleText
                                    )
                                },
                                minLines = 3,
                                maxLines = 5,
                                enabled = !isLoading,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = extColors.warningAmber,
                                    unfocusedBorderColor = extColors.cardBorder,
                                    focusedContainerColor = MaterialTheme.colorScheme.background,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("banana_prompt_input")
                            )

                            // Quick Creative Presets (in Create mode)
                            if (mode == BananaMode.CREATE) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = strings.bananaQuickPresetsTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extColors.subtleText
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(strings.bananaPresets) { preset ->
                                            Surface(
                                                onClick = { if (!isLoading) promptText = preset },
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                border = BorderStroke(1.dp, extColors.cardBorder)
                                            ) {
                                                Text(
                                                    text = preset,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .widthIn(max = 220.dp)
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Aspect Ratio & Resolution Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = strings.bananaAspectLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extColors.subtleText
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        BananaAspectOption.entries.forEach { option ->
                                            FilterChip(
                                                selected = aspectOption == option,
                                                onClick = { viewModel.setAspectOption(option) },
                                                enabled = !isLoading,
                                                label = {
                                                    Text(
                                                        text = option.label,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = MonoFontFamily
                                                        )
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = extColors.warningAmberContainer,
                                                    selectedLabelColor = extColors.warningAmber
                                                ),
                                                modifier = Modifier.testTag("banana_aspect_${option.name.lowercase()}")
                                            )
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = strings.bananaQualityLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extColors.subtleText
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        BananaSizeOption.entries.forEach { option ->
                                            FilterChip(
                                                selected = sizeOption == option,
                                                onClick = { viewModel.setSizeOption(option) },
                                                enabled = !isLoading,
                                                label = {
                                                    Text(
                                                        text = option.label,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = MonoFontFamily
                                                        )
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = extColors.onlineGreenContainer,
                                                    selectedLabelColor = extColors.onlineGreen
                                                ),
                                                modifier = Modifier.testTag("banana_size_${option.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }

                            val canSubmit = !isLoading && promptText.isNotBlank() &&
                                (mode == BananaMode.CREATE || selectedSourceBitmap != null)

                            Button(
                                onClick = { viewModel.generateOrEdit(promptText) },
                                enabled = canSubmit,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = extColors.warningAmber,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("banana_generate_button")
                            ) {
                                Icon(
                                    imageVector = if (mode == BananaMode.CREATE) Icons.Filled.AutoAwesome else Icons.Filled.AutoFixHigh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (mode == BananaMode.CREATE) {
                                        strings.bananaGenerateButton
                                    } else {
                                        strings.bananaEditButton
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // 4. Live Output Stage (Idle / Loading Shimmer / Rendered Image / Authentic Error)
                item {
                    BananaOutputStageCard(
                        uiState = uiState,
                        loadingSeconds = loadingSeconds,
                        isSavingImage = isSavingImage,
                        strings = strings,
                        onRetry = { viewModel.generateOrEdit(promptText) },
                        onSave = { record ->
                            if (!isSavingImage) {
                                isSavingImage = true
                                scope.launch {
                                    val result = BananaImageStorageHelper.saveBitmapToGallery(
                                        context = context,
                                        bitmap = record.bitmap
                                    )
                                    isSavingImage = false
                                    snackbarHostState.showSnackbar(
                                        if (result.isSuccess) strings.bananaSavedSuccess else strings.bananaSaveFailed
                                    )
                                }
                            }
                        },
                        onShare = { record ->
                            scope.launch {
                                BananaImageStorageHelper.shareBitmap(
                                    context = context,
                                    bitmap = record.bitmap,
                                    chooserTitle = strings.bananaShareImage
                                )
                            }
                        },
                        onEditResult = { record ->
                            viewModel.useBitmapAsEditSource(record.bitmap)
                            promptText = ""
                        }
                    )
                }

                // 5. Session Creations Strip
                if (history.size > 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = strings.bananaHistoryTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(history, key = { it.id }) { item ->
                                    val isCurrent = (uiState as? BananaUiState.Success)?.record?.id == item.id
                                    Box(
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                width = if (isCurrent) 2.dp else 1.dp,
                                                color = if (isCurrent) extColors.warningAmber else extColors.cardBorder,
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable { viewModel.selectHistoryRecord(item) }
                                    ) {
                                        Image(
                                            bitmap = item.bitmap.asImageBitmap(),
                                            contentDescription = item.prompt,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ModeSelectorCard(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extColors = LocalVitRemoteColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor.copy(alpha = 0.7f) else extColors.cardBorder
        ),
        modifier = modifier
            .height(52.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentColor else extColors.subtleText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.onSurface else extColors.subtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BananaOutputStageCard(
    uiState: BananaUiState,
    loadingSeconds: Int,
    isSavingImage: Boolean,
    strings: AppStrings,
    onRetry: () -> Unit,
    onSave: (BananaGenerationRecord) -> Unit,
    onShare: (BananaGenerationRecord) -> Unit,
    onEditResult: (BananaGenerationRecord) -> Unit
) {
    val extColors = LocalVitRemoteColors.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extColors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        when (uiState) {
            is BananaUiState.Idle -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(extColors.warningAmberContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                            tint = extColors.warningAmber,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = strings.bananaIdleTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = strings.bananaIdleSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = extColors.subtleText,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is BananaUiState.Loading -> {
                val infiniteTransition = rememberInfiniteTransition(label = "banana_pulse")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.08f,
                    targetValue = 0.24f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glow_alpha"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.1f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    extColors.warningAmber.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = extColors.warningAmber,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = strings.bananaLoadingTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = strings.bananaLoadingSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.subtleText,
                            textAlign = TextAlign.Center
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${loadingSeconds}s",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                color = extColors.warningAmber,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            is BananaUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = extColors.dangerRed,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("banana_error_text")
                    )
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("banana_retry_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.retry)
                    }
                }
            }

            is BananaUiState.Success -> {
                val record = uiState.record
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Image(
                            bitmap = record.bitmap.asImageBitmap(),
                            contentDescription = record.prompt,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("banana_generated_image")
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "${record.aspectLabel} • ${record.sizeLabel}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!record.caption.isNullOrBlank()) {
                        Text(
                            text = record.caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = extColors.subtleText
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSave(record) },
                            enabled = !isSavingImage,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extColors.onlineGreen,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("banana_save_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.bananaSaveGallery, style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = { onShare(record) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, extColors.cardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("banana_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.bananaShareImage, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    OutlinedButton(
                        onClick = { onEditResult(record) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, extColors.warningAmber.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("banana_refine_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = extColors.warningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.bananaRefineFurther,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
