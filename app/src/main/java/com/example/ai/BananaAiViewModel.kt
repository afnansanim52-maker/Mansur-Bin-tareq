package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.BananaImageStorageHelper
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "BananaAiViewModel"
private const val BANANA_MODEL_NAME = "gemini-nano-banana-2.1"
private const val GENERATION_TIMEOUT_MS = 60_000L

enum class BananaMode {
    CREATE,
    EDIT
}

enum class BananaAspectOption(val label: String, val sdkRatio: AspectRatio?) {
    SQUARE_1_1("1:1", null),
    LANDSCAPE_16_9("16:9", AspectRatio.LANDSCAPE_16x9),
    PORTRAIT_9_16("9:16", AspectRatio.PORTRAIT_9x16)
}

enum class BananaSizeOption(val label: String, val sdkSize: ImageSize) {
    SIZE_1K("1K", ImageSize.SIZE_1K),
    SIZE_2K("2K", ImageSize.SIZE_2K)
}

data class BananaGenerationRecord(
    val id: String,
    val prompt: String,
    val mode: BananaMode,
    val bitmap: Bitmap,
    val caption: String?,
    val aspectLabel: String,
    val sizeLabel: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

sealed interface BananaUiState {
    data object Idle : BananaUiState
    data class Loading(val mode: BananaMode) : BananaUiState
    data class Success(val record: BananaGenerationRecord) : BananaUiState
    data class Error(val message: String) : BananaUiState
}

class BananaAiViewModel : ViewModel() {

    private val _mode = MutableStateFlow(BananaMode.CREATE)
    val mode: StateFlow<BananaMode> = _mode.asStateFlow()

    private val _aspectOption = MutableStateFlow(BananaAspectOption.SQUARE_1_1)
    val aspectOption: StateFlow<BananaAspectOption> = _aspectOption.asStateFlow()

    private val _sizeOption = MutableStateFlow(BananaSizeOption.SIZE_1K)
    val sizeOption: StateFlow<BananaSizeOption> = _sizeOption.asStateFlow()

    private val _selectedSourceBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedSourceBitmap: StateFlow<Bitmap?> = _selectedSourceBitmap.asStateFlow()

    private val _uiState = MutableStateFlow<BananaUiState>(BananaUiState.Idle)
    val uiState: StateFlow<BananaUiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<BananaGenerationRecord>>(emptyList())
    val history: StateFlow<List<BananaGenerationRecord>> = _history.asStateFlow()

    fun setMode(newMode: BananaMode) {
        _mode.value = newMode
    }

    fun setAspectOption(option: BananaAspectOption) {
        _aspectOption.value = option
    }

    fun setSizeOption(option: BananaSizeOption) {
        _sizeOption.value = option
    }

    fun selectSourceUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val decoded = BananaImageStorageHelper.decodePickedImage(context, uri)
            if (decoded != null) {
                _selectedSourceBitmap.value = decoded
                _mode.value = BananaMode.EDIT
            } else {
                _uiState.value = BananaUiState.Error("Unable to read the selected photo.")
            }
        }
    }

    fun useBitmapAsEditSource(bitmap: Bitmap) {
        _selectedSourceBitmap.value = bitmap
        _mode.value = BananaMode.EDIT
    }

    fun clearSourceBitmap() {
        _selectedSourceBitmap.value = null
    }

    fun selectHistoryRecord(record: BananaGenerationRecord) {
        _uiState.value = BananaUiState.Success(record)
    }

    fun generateOrEdit(promptInput: String) {
        val trimmedPrompt = promptInput.trim()
        if (trimmedPrompt.isEmpty()) return

        val activeMode = _mode.value
        val sourceBitmap = if (activeMode == BananaMode.EDIT) _selectedSourceBitmap.value else null
        if (activeMode == BananaMode.EDIT && sourceBitmap == null) {
            _uiState.value = BananaUiState.Error("Please select a photo to edit first.")
            return
        }

        val selectedAspect = _aspectOption.value
        val selectedSize = _sizeOption.value

        viewModelScope.launch {
            _uiState.value = BananaUiState.Loading(activeMode)
            try {
                val model = buildBananaModel(selectedAspect, selectedSize)
                val response = withTimeout(GENERATION_TIMEOUT_MS) {
                    if (activeMode == BananaMode.EDIT && sourceBitmap != null) {
                        model.generateContent(
                            content {
                                image(sourceBitmap)
                                text(trimmedPrompt)
                            }
                        )
                    } else {
                        model.generateContent(trimmedPrompt)
                    }
                }

                val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
                val generatedBitmap = parts.filterIsInstance<ImagePart>().firstOrNull()?.image
                val captionText = parts.filterIsInstance<TextPart>()
                    .map { it.text.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                    .ifBlank { null }

                if (generatedBitmap != null) {
                    val record = BananaGenerationRecord(
                        id = "${System.currentTimeMillis()}_${_history.value.size}",
                        prompt = trimmedPrompt,
                        mode = activeMode,
                        bitmap = generatedBitmap,
                        caption = captionText,
                        aspectLabel = selectedAspect.label,
                        sizeLabel = selectedSize.label
                    )
                    _history.value = (listOf(record) + _history.value).take(12)
                    _uiState.value = BananaUiState.Success(record)
                } else {
                    val detail = captionText ?: "No image was returned by Banana AI."
                    _uiState.value = BananaUiState.Error(detail)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Banana AI timed out", e)
                _uiState.value = BananaUiState.Error(
                    "Banana AI request timed out after 60 seconds. Please try again."
                )
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Banana AI quota exceeded", e)
                _uiState.value = BananaUiState.Error(
                    "Quota or billing limit reached. Ensure the Firebase project is on the Blaze plan and try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Banana AI request failed", e)
                _uiState.value = BananaUiState.Error(
                    e.localizedMessage ?: "Banana AI generation failed."
                )
            }
        }
    }

    private fun buildBananaModel(
        aspect: BananaAspectOption,
        size: BananaSizeOption
    ): GenerativeModel {
        val useDefaultConfig = aspect == BananaAspectOption.SQUARE_1_1 && size == BananaSizeOption.SIZE_1K
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE, ResponseModality.TEXT)
            if (!useDefaultConfig) {
                val builder = ImageConfig.builder().setImageSize(size.sdkSize)
                aspect.sdkRatio?.let { builder.setAspectRatio(it) }
                imageConfig = builder.build()
            }
        }
        return Firebase.ai.generativeModel(
            modelName = BANANA_MODEL_NAME,
            generationConfig = config
        )
    }
}
