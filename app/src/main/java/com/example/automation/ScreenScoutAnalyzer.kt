package com.example.automation

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class ScreenScoutState(
    val detectedOs: TargetOS? = null,
    val isVsCodeDetected: Boolean = false,
    val isTerminalActive: Boolean = false,
    val detectedActiveFile: String? = null,
    val detectedTextLines: List<String> = emptyList(),
    val statusSummary: String = "Point camera at your PC monitor to scan VS Code & OS state..."
)

class ScreenScoutAnalyzer(
    private val onStateUpdated: (ScreenScoutState) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastAnalyzedTimestamp = 0L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        // Limit OCR processing to once every 600ms for fast performance
        if (currentTimestamp - lastAnalyzedTimestamp < 600L) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text } }
                val fullTextLower = visionText.text.lowercase()

                // Detect OS
                val detectedOs = when {
                    fullTextLower.contains("powershell") || fullTextLower.contains("ps c:") -> TargetOS.WINDOWS_POWERSHELL
                    fullTextLower.contains("cmd.exe") || fullTextLower.contains("c:\\users") -> TargetOS.WINDOWS_CMD
                    fullTextLower.contains("zsh") || fullTextLower.contains("darwin") || fullTextLower.contains("macbook") -> TargetOS.MACOS_ZSH
                    fullTextLower.contains("bash") || fullTextLower.contains("ubuntu") || fullTextLower.contains("linux") -> TargetOS.LINUX_BASH
                    else -> null
                }

                // Detect VS Code IDE
                val isVsCode = fullTextLower.contains("visual studio code") ||
                        fullTextLower.contains("explorer") ||
                        fullTextLower.contains("terminal") ||
                        fullTextLower.contains("problems") ||
                        fullTextLower.contains("output") ||
                        fullTextLower.contains("code") ||
                        lines.any { it.endsWith(".kt") || it.endsWith(".java") || it.endsWith(".py") || it.endsWith(".json") }

                // Detect Terminal Prompt
                val isTerminal = fullTextLower.contains("powershell") ||
                        fullTextLower.contains("ps c:") ||
                        fullTextLower.contains("cmd") ||
                        fullTextLower.contains("$") ||
                        fullTextLower.contains(">") ||
                        fullTextLower.contains("terminal")

                // Detect Active File Name from Tab Bar
                val filePattern = Regex("""\b[\w\-]+\.(kt|java|py|js|ts|json|xml|html|css|md|txt)\b""", RegexOption.IGNORE_CASE)
                val detectedFile = lines.mapNotNull { line ->
                    filePattern.find(line)?.value
                }.firstOrNull()

                val summaryBuilder = StringBuilder()
                if (isVsCode) summaryBuilder.append("VS Code Detected. ")
                if (detectedOs != null) summaryBuilder.append("OS: ${detectedOs.label}. ")
                if (isTerminal) summaryBuilder.append("Terminal Active. ")
                if (detectedFile != null) summaryBuilder.append("Active File: $detectedFile. ")

                if (summaryBuilder.isEmpty()) {
                    summaryBuilder.append("Scanning screen text... Align camera with PC screen.")
                }

                val scoutState = ScreenScoutState(
                    detectedOs = detectedOs,
                    isVsCodeDetected = isVsCode,
                    isTerminalActive = isTerminal,
                    detectedActiveFile = detectedFile,
                    detectedTextLines = lines.take(10),
                    statusSummary = summaryBuilder.toString().trim()
                )

                onStateUpdated(scoutState)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
