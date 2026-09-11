package com.markdown.editor.presentation.export

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File

/**
 * Android platform helper for printing, PDF generation via the Print Framework,
 * and system content sharing.
 */
object AndroidExportHelper {

    /**
     * Prints the provided [htmlContent] using Android's native [PrintManager].
     * Allows the user to print to physical printers or select "Save as PDF".
     *
     * Note: Must be invoked on the main/UI thread because [WebView] and [PrintManager] require it.
     */
    fun printHtml(
        context: Context,
        jobName: String,
        htmlContent: String,
        onError: ((Throwable) -> Unit)? = null
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                onError?.invoke(IllegalStateException("PrintManager service is unavailable on this device"))
                return
            }

            // Create offscreen WebView to render HTML into print pages
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        val sanitizedJobName = jobName.ifBlank { "Document" }
                        val printAdapter = webView.createPrintDocumentAdapter(sanitizedJobName)
                        printManager.print(
                            sanitizedJobName,
                            printAdapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .build()
                        )
                    } catch (e: Exception) {
                        onError?.invoke(e)
                    }
                }
            }

            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            onError?.invoke(e)
        }
    }

    /**
     * Shares content to external applications via Android's [Intent.ACTION_SEND] share sheet.
     */
    fun shareText(
        context: Context,
        title: String,
        content: String,
        mimeType: String = "text/plain"
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        val chooserIntent = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    }

    /**
     * Saves exported content to a temporary cache file so it can be read or shared.
     */
    fun saveToExportCache(
        context: Context,
        fileName: String,
        content: String
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val targetFile = File(exportDir, fileName)
        targetFile.writeText(content, Charsets.UTF_8)
        return targetFile
    }
}

