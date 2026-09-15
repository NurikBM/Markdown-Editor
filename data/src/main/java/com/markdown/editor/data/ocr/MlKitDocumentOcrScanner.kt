package com.markdown.editor.data.ocr

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.ocr.DocumentOcrScanner
import com.markdown.editor.domain.ocr.OcrResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR scanner powered by Google ML Kit Text Recognition v2.
 * Analyzes physical document structure, detects headings, lists, and paragraphs,
 * and formats extracted text into native Markdown blocks.
 */
class MlKitDocumentOcrScanner(
    @Suppress("unused") private val context: Context
) : DocumentOcrScanner {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeText(
        imageBytes: ByteArray,
        defaultTitle: String
    ): Result<OcrResult> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Image data cannot be empty"))
        }

        val rotationDegrees = extractExifRotation(imageBytes)

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)
        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > 2560 || boundsOptions.outHeight / sampleSize > 2560) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
            ?: return Result.failure(IllegalArgumentException("Failed to decode image from provided bytes"))

        val inputImage = try {
            InputImage.fromBitmap(bitmap, rotationDegrees)
        } catch (e: Exception) {
            bitmap.recycle()
            return Result.failure(e)
        }

        return try {
            val visionText = processImage(inputImage)
            bitmap.recycle()

            if (visionText.text.isBlank()) {
                return Result.failure(NoSuchElementException("No readable text detected in the image"))
            }

            val result = convertVisionTextToMarkdown(visionText, defaultTitle)
            Result.success(result)
        } catch (e: Exception) {
            bitmap.recycle()
            Result.failure(e)
        }
    }

    /**
     * Directly processes an existing [Bitmap] (e.g. rendered from PDF page) without re-encoding.
     */
    suspend fun recognizeBitmap(
        bitmap: android.graphics.Bitmap,
        defaultTitle: String = "Scanned Note"
    ): Result<OcrResult> {
        val inputImage = try {
            InputImage.fromBitmap(bitmap, 0)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return try {
            val visionText = processImage(inputImage)
            if (visionText.text.isBlank()) {
                return Result.failure(NoSuchElementException("No readable text detected in the image"))
            }

            val result = convertVisionTextToMarkdown(visionText, defaultTitle)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractExifRotation(imageBytes: ByteArray): Int {
        return try {
            val exif = android.media.ExifInterface(java.io.ByteArrayInputStream(imageBytes))
            when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private suspend fun processImage(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (continuation.isActive) {
                        continuation.resume(visionText)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        }

    private fun convertVisionTextToMarkdown(visionText: Text, fallbackTitle: String): OcrResult {
        val blocks = mutableListOf<MarkdownBlock>()
        val markdownBuilder = StringBuilder()

        val allLines = visionText.textBlocks.flatMap { it.lines }
        val medianLineHeight = computeMedianLineHeight(allLines)

        var detectedTitle: String? = null

        for (textBlock in visionText.textBlocks) {
            val blockResult = processTextBlock(textBlock, medianLineHeight)
            if (blockResult != null) {
                blocks.add(blockResult)
                if (detectedTitle == null && (blockResult.type is BlockType.Heading || blockResult.rawContent.length < 50)) {
                    detectedTitle = blockResult.rawContent.trim('#', ' ', '\t', '\n')
                }
                if (markdownBuilder.isNotEmpty()) {
                    markdownBuilder.append("\n\n")
                }
                markdownBuilder.append(formatBlockMarkdown(blockResult))
            }
        }

        val finalTitle = detectedTitle?.take(40)?.ifBlank { fallbackTitle } ?: fallbackTitle
        return OcrResult(
            title = finalTitle,
            markdownContent = markdownBuilder.toString().trim(),
            blocks = blocks
        )
    }

    private fun processTextBlock(textBlock: Text.TextBlock, medianLineHeight: Float): MarkdownBlock? {
        val rawText = textBlock.text.trim()
        if (rawText.isBlank()) return null

        val lines = textBlock.lines
        val firstLine = lines.firstOrNull()
        val firstLineHeight = firstLine?.boundingBox?.height()?.toFloat() ?: 0f

        val isHeading = lines.size <= 2 &&
                firstLineHeight > 0f &&
                medianLineHeight > 0f &&
                firstLineHeight >= medianLineHeight * 1.35f

        val bulletRegex = Regex("""^([•–—▪*\-]|\d+[\.\)])\s+(.*)$""")
        val match = bulletRegex.find(rawText)

        return when {
            isHeading -> {
                val cleanHeading = rawText.replace("\n", " ").trim()
                MarkdownBlock(
                    id = BlockId(UUID.randomUUID().toString()),
                    rawContent = cleanHeading,
                    type = BlockType.Heading(level = 1)
                )
            }
            match != null -> {
                val isNumbered = match.groupValues[1].first().isDigit()
                MarkdownBlock(
                    id = BlockId(UUID.randomUUID().toString()),
                    rawContent = rawText,
                    type = BlockType.ListItem(ordered = isNumbered)
                )
            }
            else -> {
                val paragraphText = lines.joinToString(" ") { it.text.trim() }
                MarkdownBlock(
                    id = BlockId(UUID.randomUUID().toString()),
                    rawContent = paragraphText,
                    type = BlockType.Paragraph
                )
            }
        }
    }

    private fun computeMedianLineHeight(lines: List<Text.Line>): Float {
        val heights = lines.mapNotNull { it.boundingBox?.height()?.toFloat() }.filter { it > 0f }.sorted()
        if (heights.isEmpty()) return 0f
        val mid = heights.size / 2
        return if (heights.size % 2 == 0) (heights[mid - 1] + heights[mid]) / 2f else heights[mid]
    }

    private fun formatBlockMarkdown(block: MarkdownBlock): String {
        return when (val type = block.type) {
            is BlockType.Heading -> "${"#".repeat(type.level)} ${block.rawContent}"
            is BlockType.ListItem -> block.rawContent
            else -> block.rawContent
        }
    }
}
