package com.pixelcrunch.squee

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class ImageProcessor(private val context: Context) {

    data class ProcessingResult(
        val uri: Uri,
        val mimeType: String,
    )

    suspend fun processImages(
        uris: List<Uri>,
        prefs: UserPreferences,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<ProcessingResult> = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "squee").apply {
            mkdirs()
            // Clean up any stale files from previous runs
            listFiles()?.forEach { it.delete() }
        }

        uris.mapIndexed { index, uri ->
            val result = processImage(uri, prefs, cacheDir, index)
            onProgress(index + 1, uris.size)
            result
        }
    }

    private fun processImage(
        uri: Uri,
        prefs: UserPreferences,
        cacheDir: File,
        index: Int,
    ): ProcessingResult {
        // Decode with orientation correction
        val bitmap = decodeBitmap(uri, prefs)

        // Resize
        val resized = resizeBitmap(bitmap, prefs.resolutionPreset.maxDimension)
        if (resized !== bitmap) bitmap.recycle()

        // Determine format and compress
        val format = prefs.outputFormat
        val outFile = File(cacheDir, "squee_${index}_${System.currentTimeMillis()}.${format.extension}")

        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
        }

        outFile.outputStream().use { outputStream ->
            resized.compress(compressFormat, prefs.compressionQuality, outputStream)
        }
        resized.recycle()

        // Strip EXIF metadata from the output file (JPEG/WebP may carry some)
        if (prefs.stripMetadata) {
            stripExifData(outFile)
        }

        val outUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile,
        )

        val mimeType = when (format) {
            OutputFormat.JPEG -> "image/jpeg"
            OutputFormat.WEBP -> "image/webp"
        }

        return ProcessingResult(uri = outUri, mimeType = mimeType)
    }

    private fun decodeBitmap(uri: Uri, prefs: UserPreferences): Bitmap {
        // First pass: get dimensions for down-sampling
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        // Calculate inSampleSize for memory efficiency
        val maxDim = prefs.resolutionPreset.maxDimension
        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDim)
        options.inJustDecodeBounds = false

        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalStateException("Failed to decode image from $uri")

        // Read EXIF orientation and rotate if needed
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        return applyExifOrientation(rawBitmap, orientation)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sampleSize = 1
        val longerSide = max(width, height)
        if (longerSide > maxDim) {
            var half = longerSide / 2
            while (half / sampleSize >= maxDim) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longerSide = max(width, height)

        if (longerSide <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / longerSide
        val newWidth = (width * scale).roundToInt()
        val newHeight = (height * scale).roundToInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun stripExifData(file: File) {
        val exif = ExifInterface(file)
        val tagsToStrip = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            @Suppress("DEPRECATION") ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_METERING_MODE,
        )
        for (tag in tagsToStrip) {
            exif.setAttribute(tag, null)
        }
        exif.saveAttributes()
    }
}
