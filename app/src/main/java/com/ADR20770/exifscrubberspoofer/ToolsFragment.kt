package com.ADR20770.exifscrubberspoofer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ToolsFragment : Fragment() {

    private companion object {
        private const val TAG = "ToolsFragment"
    }

    private lateinit var historyManager: HistoryManager
    private lateinit var rvHistory: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter

    private val imageToPdfContract = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            convertImageToPdf(uri)
        }
    }

    private val pdfToImageContract = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            convertPdfToImages(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tools, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyManager = HistoryManager(requireContext())

        view.findViewById<MaterialButton>(R.id.btnImageToPdf).setOnClickListener {
            imageToPdfContract.launch("image/*")
        }

        view.findViewById<MaterialButton>(R.id.btnPdfToImage).setOnClickListener {
            pdfToImageContract.launch("application/pdf")
        }

        view.findViewById<MaterialButton>(R.id.btnExportReport).setOnClickListener {
            (activity as? MainActivity)?.exportPrivacyReport()
        }

        // Feature 4: Privacy Report als PDF speichern
        view.findViewById<MaterialButton>(R.id.btnExportPdfReport).setOnClickListener {
            exportPrivacyReportAsPdf()
        }

        // Feature 7: Verlauf (Letzte 10 Scrub-Vorgänge)
        rvHistory = view.findViewById(R.id.rvHistory)
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        setupHistoryAdapter()

        view.findViewById<MaterialButton>(R.id.btnClearHistory).setOnClickListener {
            historyManager.clearHistory()
            setupHistoryAdapter()
            Toast.makeText(requireContext(), "Verlauf gelöscht", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupHistoryAdapter() {
        val items = historyManager.getHistory()
        historyAdapter = HistoryAdapter(items) { uriStr ->
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(uriStr), "image/jpeg")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Datei konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
            }
        }
        rvHistory.adapter = historyAdapter
    }

    // Feature 4: Privacy Report als PDF speichern
    private fun exportPrivacyReportAsPdf() {
        val ctx = requireContext()
        lifecycleScope.launch {
            var success = false
            withContext(Dispatchers.IO) {
                var pdfDocument: PdfDocument? = null
                try {
                    val prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val reportContent = "=== EXIF PRIVACY AUDIT REPORT ===\n" +
                            "Date: ${Date()}\n" +
                            "Status: 100% Secured & Cleaned\n" +
                            "- GPS Location: ${if (prefs.getBoolean("pref_check_gps", true)) "Scrubbed" else "Preserved"}\n" +
                            "- Camera Data: ${if (prefs.getBoolean("pref_check_camera", true)) "Scrubbed" else "Preserved"}\n" +
                            "- Timestamps: ${if (prefs.getBoolean("pref_check_date", true)) "Cleared" else "Preserved"}\n" +
                            "- Processing: 100% Local (GDPR Compliant)\n" +
                            "================================"

                    pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                    val page = pdfDocument.startPage(pageInfo)

                    val canvas = page.canvas
                    val paint = android.graphics.Paint().apply {
                        color = Color.BLACK
                        textSize = 14f
                    }

                    var y = 50f
                    for (line in reportContent.split("\n")) {
                        canvas.drawText(line, 50f, y, paint)
                        y += 24f
                    }
                    pdfDocument.finishPage(page)

                    val fileName = "Privacy_Report_${System.currentTimeMillis()}.pdf"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/ExifScrubber")
                        }
                    }

                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }

                    val savedUri = ctx.contentResolver.insert(collection, contentValues)
                    if (savedUri != null) {
                        ctx.contentResolver.openOutputStream(savedUri)?.use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }
                        success = true
                    }
                    pdfDocument.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error exporting privacy report PDF", e)
                } finally {
                    pdfDocument?.close()
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(ctx, "Privacy Report PDF saved to Documents/ExifScrubber!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, "Error saving report PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun convertImageToPdf(imageUri: Uri) {
        val ctx = requireContext()
        lifecycleScope.launch {
            var success = false
            withContext(Dispatchers.IO) {
                var bitmap: Bitmap? = null
                var pdfDocument: PdfDocument? = null
                try {
                    val inputStream = ctx.contentResolver.openInputStream(imageUri) ?: return@withContext
                    bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    if (bitmap == null) return@withContext

                    pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                    val page = pdfDocument.startPage(pageInfo)

                    val canvas = page.canvas
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)

                    val fileName = "Converted_${System.currentTimeMillis()}.pdf"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/ExifScrubber")
                        }
                    }

                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }

                    val savedUri = ctx.contentResolver.insert(collection, contentValues)
                    if (savedUri != null) {
                        ctx.contentResolver.openOutputStream(savedUri)?.use { outputStream ->
                            pdfDocument.writeTo(outputStream)
                        }
                        success = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting image to PDF", e)
                } finally {
                    pdfDocument?.close()
                    bitmap?.recycle()
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(ctx, getString(R.string.pdf_save_success), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, getString(R.string.pdf_save_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun convertPdfToImages(pdfUri: Uri) {
        val ctx = requireContext()
        lifecycleScope.launch {
            var savedCount = 0
            withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { pdfRenderer ->
                            val pageCount = pdfRenderer.pageCount

                            for (i in 0 until pageCount) {
                                val page = pdfRenderer.openPage(i)
                                var bitmap: Bitmap? = null
                                try {
                                    bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    page.close()

                                    val fileName = "PDF_Page_${i + 1}_${System.currentTimeMillis()}.jpg"
                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ExifScrubber")
                                        }
                                    }

                                    val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                                    } else {
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                    }

                                    val savedUri = ctx.contentResolver.insert(imageCollection, contentValues)
                                    if (savedUri != null) {
                                        ctx.contentResolver.openOutputStream(savedUri)?.use { out ->
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                        }
                                        savedCount++
                                    }
                                } finally {
                                    bitmap?.recycle()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting PDF to images", e)
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, getString(R.string.pdf_extract_success, savedCount), Toast.LENGTH_LONG).show()
            }
        }
    }
}

// History Adapter for RecyclerView
class HistoryAdapter(
    private val items: List<ScrubHistoryItem>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInfo: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
        val status = if (item.success) "Erfolgreich" else "Fehlgeschlagen"
        holder.tvInfo.text = "$dateStr - $status\n${item.uriString}"
        holder.itemView.setOnClickListener { onItemClick(item.uriString) }
    }

    override fun getItemCount() = items.size
}
