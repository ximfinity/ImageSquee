package com.pixelcrunch.squee

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pixelcrunch.squee.ui.theme.ImageSqueeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    private var isProcessing by mutableStateOf(true)
    private var progressText by mutableStateOf("Preparing...")
    private var processedCount by mutableIntStateOf(0)
    private var totalCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ImageSqueeTheme {
                ProcessingOverlay(
                    visible = isProcessing,
                    progressText = progressText,
                    processed = processedCount,
                    total = totalCount,
                )
            }
        }

        val imageUris = extractUris()
        if (imageUris.isEmpty()) {
            Toast.makeText(this, "No images received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        totalCount = imageUris.size
        progressText = "Squeezing ${imageUris.size} image${if (imageUris.size > 1) "s" else ""}..."

        lifecycleScope.launch {
            try {
                val repository = SettingsRepository(applicationContext)
                val prefs = repository.preferences.first()
                val processor = ImageProcessor(applicationContext)

                val results = processor.processImages(imageUris, prefs) { processed, total ->
                    processedCount = processed
                    totalCount = total
                    progressText = "Squeezed $processed of $total..."
                }

                forwardResults(results)
            } catch (e: Exception) {
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "Processing failed: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                finish()
            }
        }
    }

    private fun extractUris(): List<Uri> {
        val action = intent.action ?: return emptyList()
        return when (action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private fun forwardResults(results: List<ImageProcessor.ProcessingResult>) {
        if (results.isEmpty()) return

        val shareIntent = if (results.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = results[0].mimeType
                putExtra(Intent.EXTRA_STREAM, results[0].uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = results.first().mimeType
                val uriList = ArrayList(results.map { it.uri })
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriList)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooser = Intent.createChooser(shareIntent, null).apply {
            // Exclude ourselves from the chooser
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(componentName))
        }
        startActivity(chooser)
    }
}

@Composable
private fun ProcessingOverlay(
    visible: Boolean,
    progressText: String,
    processed: Int,
    total: Int,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .size(200.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (total > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$processed / $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
