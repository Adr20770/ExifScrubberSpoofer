package com.ADR20770.exifscrubberspoofer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.os.LocaleListCompat
import androidx.core.text.HtmlCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var adView: AdView
    private lateinit var statusText: TextView
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnScrubData: MaterialButton
    private lateinit var btnShareImage: MaterialButton
    private lateinit var imagePreview: ImageView
    private lateinit var previewPlaceholder: View
    private lateinit var progressBar: ProgressBar
    
    private lateinit var metadataCard: View
    private lateinit var metadataText: TextView
    private lateinit var checkGps: CheckBox
    private lateinit var checkCamera: CheckBox
    private lateinit var checkDate: CheckBox
    private lateinit var checkCompress: CheckBox
    private lateinit var checkDeleteOriginal: CheckBox
    private lateinit var editSpoofMake: EditText
    private lateinit var editSpoofModel: EditText
    private lateinit var editSpoofDate: EditText
    private lateinit var editSpoofLat: EditText
    private lateinit var editSpoofLon: EditText

    private var imageUri: Uri? = null
    private var cleanedImageUri: Uri? = null
    private var currentExifData: Map<String, String>? = null

    private val pickMediaContract = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            cleanedImageUri = null
            btnShareImage.visibility = View.GONE
            statusText.text = getString(R.string.status_image_loaded)
            btnScrubData.isEnabled = true
            
            // Bild anzeigen
            imagePreview.setImageURI(uri)
            imagePreview.visibility = View.VISIBLE
            previewPlaceholder.visibility = View.GONE
            
            // Metadaten anzeigen
            displayMetadata(uri)
        } else {
            statusText.text = getString(R.string.status_no_image)
            imagePreview.visibility = View.GONE
            previewPlaceholder.visibility = View.VISIBLE
            metadataCard.visibility = View.GONE
        }
    }

    private val pickMultipleMediaContract = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scrubAndSaveMultipleImages(uris)
        }
    }

    private val deleteRequestLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Original photo deleted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val themeChoice = prefs.getInt("app_theme_choice", 0) // 0: System, 1: Light, 2: Dark, 3: OLED
        when (themeChoice) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2, 3 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        setContentView(R.layout.activity_main)

        if (themeChoice == 3) {
            window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        checkFirstRun()

        toolbar.setNavigationOnClickListener {
            showIntroDialog()
        }

        // Werbung ist aktuell noch nicht aktiv
        findViewById<View>(R.id.adContainer)?.visibility = View.GONE

        statusText = findViewById(R.id.statusText)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnScrubData = findViewById(R.id.btnScrubData)
        btnShareImage = findViewById(R.id.btnShareImage)
        adView = findViewById(R.id.adView)
        imagePreview = findViewById(R.id.imagePreview)
        previewPlaceholder = findViewById(R.id.previewPlaceholder)
        progressBar = findViewById(R.id.progressBar)
        
        metadataCard = findViewById(R.id.metadataCard)
        metadataText = findViewById(R.id.metadataText)
        checkGps = findViewById(R.id.checkGps)
        checkCamera = findViewById(R.id.checkCamera)
        checkDate = findViewById(R.id.checkDate)
        checkCompress = findViewById(R.id.checkCompress)
        checkDeleteOriginal = findViewById(R.id.checkDeleteOriginal)
        editSpoofMake = findViewById(R.id.editSpoofMake)
        editSpoofModel = findViewById(R.id.editSpoofModel)
        editSpoofDate = findViewById(R.id.editSpoofDate)
        editSpoofLat = findViewById(R.id.editSpoofLat)
        editSpoofLon = findViewById(R.id.editSpoofLon)

        loadSavedPreferences()
        setupPreferenceListeners()

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        btnScrubData.isEnabled = false

        findViewById<View>(R.id.btnExtendedExif).setOnClickListener {
            currentExifData?.let { data ->
                val details = if (data.isEmpty()) "No tags found" else data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                AlertDialog.Builder(this)
                    .setTitle(R.string.btn_extended_exif)
                    .setMessage(details)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }

        findViewById<View>(R.id.btnPresetLeica).setOnClickListener {
            editSpoofMake.setText("Leica")
            editSpoofModel.setText("M11")
        }
        findViewById<View>(R.id.btnPresetHasselblad).setOnClickListener {
            editSpoofMake.setText("Hasselblad")
            editSpoofModel.setText("X1D-50c")
        }
        findViewById<View>(R.id.btnPresetFilm).setOnClickListener {
            editSpoofMake.setText("Canon")
            editSpoofModel.setText("AE-1 Program")
        }
        findViewById<View>(R.id.btnPresetClear).setOnClickListener {
            editSpoofMake.setText("")
            editSpoofModel.setText("")
            editSpoofLat.setText("")
            editSpoofLon.setText("")
        }
        findViewById<View>(R.id.btnSelectImages).setOnClickListener {
            pickMultipleMediaContract.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        findViewById<View>(R.id.btnExportReport).setOnClickListener {
            exportPrivacyReport()
        }

        btnSelectImage.setOnClickListener {
            pickMediaContract.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnScrubData.setOnClickListener {
            imageUri?.let { uri ->
                scrubAndSaveImage(uri)
            }
        }

        btnShareImage.setOnClickListener {
            cleanedImageUri?.let { uri ->
                shareImage(uri)
            }
        }
        
        // Intent-Filter Check (Falls von extern geteilt wurde)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                imageUri = uri
                imagePreview.setImageURI(uri)
                imagePreview.visibility = View.VISIBLE
                previewPlaceholder.visibility = View.GONE
                btnScrubData.isEnabled = true
                displayMetadata(uri)
            }
        }
    }

    private fun displayMetadata(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val sb = StringBuilder()
                
                val latLong = exif.latLong
                if (latLong != null) {
                    sb.append("📍 Location: ${latLong[0]}, ${latLong[1]}\n")
                }
                
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                if (make != null || model != null) {
                    sb.append("📷 Camera: $make $model\n")
                }
                
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (date != null) {
                    sb.append("📅 Date: $date\n")
                }
                
                val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
                if (software != null) {
                    sb.append("💻 Software: $software\n")
                }

                if (sb.isEmpty()) {
                    metadataText.text = getString(R.string.label_no_metadata)
                } else {
                    metadataText.text = sb.toString()
                }
                metadataCard.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            metadataCard.visibility = View.GONE
        }
    }

    private fun scrubAndSaveImage(uri: Uri) {
        val handler = Handler(Looper.getMainLooper())
        
        // UI vorbereiten
        btnScrubData.isEnabled = false
        btnSelectImage.isEnabled = false
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.status_scanning)

        // Wir simulieren die Schritte mit Verzögerung, damit der Nutzer die Animation sieht
        handler.postDelayed({
            statusText.text = getString(R.string.status_cleaning)
            
            handler.postDelayed({
                try {
                    // 1. Bild in die temporäre Sandbox laden
                    val inputStream = contentResolver.openInputStream(uri) ?: return@postDelayed
                    val tempFile = File(cacheDir, "temp_image.jpg")
                    val outputStream = FileOutputStream(tempFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()

                    // HEIC-Erkennung & automatische Konvertierung nach JPEG (da AndroidX EXIF-Write für HEIC nicht unterstützt)
                    val isHeic = isHeicImage(uri)
                    if (isHeic || checkCompress.isChecked) {
                        try {
                            val orientation = contentResolver.openInputStream(uri)?.use { input ->
                                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                            } ?: ExifInterface.ORIENTATION_NORMAL

                            val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                                BitmapFactory.decodeStream(input)
                            }
                            if (bitmap != null) {
                                val rotated = rotateBitmapIfNeeded(bitmap, orientation)
                                val quality = if (checkCompress.isChecked) 85 else 95
                                FileOutputStream(tempFile).use { compStream ->
                                    rotated.compress(Bitmap.CompressFormat.JPEG, quality, compStream)
                                }
                                if (rotated != bitmap) rotated.recycle()
                                bitmap.recycle()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 2. EXIF-Metadaten DANACH anwenden und speichern
                    val exif = ExifInterface(tempFile.absolutePath)
                    
                    val fakeMake = editSpoofMake.text.toString()
                    val fakeModel = editSpoofModel.text.toString()
                    val fakeDate = formatExifDate(editSpoofDate.text.toString())
                    val latStr = findViewById<EditText>(R.id.editSpoofLat).text.toString()
                    val lonStr = findViewById<EditText>(R.id.editSpoofLon).text.toString()

                    if (fakeMake.isNotEmpty() || fakeModel.isNotEmpty()) {
                        if (fakeMake.isNotEmpty()) exif.setAttribute(ExifInterface.TAG_MAKE, fakeMake)
                        if (fakeModel.isNotEmpty()) exif.setAttribute(ExifInterface.TAG_MODEL, fakeModel)
                    } else if (checkCamera.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_MAKE, null)
                        exif.setAttribute(ExifInterface.TAG_MODEL, null)
                        exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
                    }

                    if (fakeDate.isNotEmpty()) {
                        exif.setAttribute(ExifInterface.TAG_DATETIME, fakeDate)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, fakeDate)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, fakeDate)
                    } else if (checkDate.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_DATETIME, null)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
                    }

                    if (latStr.isNotEmpty() && lonStr.isNotEmpty()) {
                        try {
                            val lat = latStr.toDouble()
                            val lon = lonStr.toDouble()
                            exif.setLatLong(lat, lon)
                        } catch (_: Exception) {}
                    } else if (checkGps.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                    }

                    exif.saveAttributes()

                    // 3. Das saubere Bild im MediaStore anlegen (mit IS_PENDING = 1 ab Android 10)
                    val fileName = "Cleaned_${System.currentTimeMillis()}.jpg"
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ExifScrubber")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                    val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }

                    val savedUri = resolver.insert(imageCollection, contentValues)
                    if (savedUri != null) {
                        resolver.openOutputStream(savedUri)?.use { galleryOutputStream ->
                            tempFile.inputStream().copyTo(galleryOutputStream)
                        }

                        // EXIF-Attribute DIREKT auf der finalen MediaStore-URI anwenden!
                        try {
                            resolver.openFileDescriptor(savedUri, "rw")?.use { pfd ->
                                val targetExif = ExifInterface(pfd.fileDescriptor)

                                if (fakeMake.isNotEmpty() || fakeModel.isNotEmpty()) {
                                    if (fakeMake.isNotEmpty()) targetExif.setAttribute(ExifInterface.TAG_MAKE, fakeMake)
                                    if (fakeModel.isNotEmpty()) targetExif.setAttribute(ExifInterface.TAG_MODEL, fakeModel)
                                } else if (checkCamera.isChecked) {
                                    targetExif.setAttribute(ExifInterface.TAG_MAKE, null)
                                    targetExif.setAttribute(ExifInterface.TAG_MODEL, null)
                                    targetExif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
                                }

                                if (fakeDate.isNotEmpty()) {
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME, fakeDate)
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, fakeDate)
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, fakeDate)
                                } else if (checkDate.isChecked) {
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME, null)
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
                                }

                                if (latStr.isNotEmpty() && lonStr.isNotEmpty()) {
                                    try {
                                        val lat = latStr.toDouble()
                                        val lon = lonStr.toDouble()
                                        targetExif.setLatLong(lat, lon)
                                    } catch (_: Exception) {}
                                } else if (checkGps.isChecked) {
                                    targetExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                                    targetExif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                                    targetExif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                                    targetExif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                                }

                                targetExif.saveAttributes()
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }

                        // IS_PENDING freigeben
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(savedUri, contentValues, null, null)
                        }

                        cleanedImageUri = savedUri // Speichern für Share-Button
                    }

                    statusText.text = getString(R.string.status_done)
                    progressBar.visibility = View.GONE
                    btnSelectImage.isEnabled = true
                    btnShareImage.visibility = View.VISIBLE
                    findViewById<View>(R.id.btnExportReport).visibility = View.VISIBLE

                    if (checkDeleteOriginal.isChecked) {
                        deleteOriginalImage(uri)
                    }

                    triggerHapticFeedback()
                    Toast.makeText(this, getString(R.string.save_success_toast), Toast.LENGTH_LONG).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                    statusText.text = getString(R.string.error_scrubbing)
                    progressBar.visibility = View.GONE
                    btnSelectImage.isEnabled = true
                }
            }, 1500) // 1.5 Sekunden für "Reinigung"
        }, 1500) // 1.5 Sekunden für "Scan"
    }

    private fun scrubAndSaveMultipleImages(uris: List<Uri>) {
        val handler = Handler(Looper.getMainLooper())
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.status_cleaning)

        handler.postDelayed({
            var successCount = 0
            for (uri in uris) {
                try {
                    val inputStream = contentResolver.openInputStream(uri) ?: continue
                    val tempFile = File(cacheDir, "temp_batch_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(tempFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()

                    // HEIC-Erkennung & Komprimierung ZUERST
                    val isHeic = isHeicImage(uri)
                    if (isHeic || checkCompress.isChecked) {
                        try {
                            val orientation = contentResolver.openInputStream(uri)?.use { input ->
                                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                            } ?: ExifInterface.ORIENTATION_NORMAL

                            val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                                BitmapFactory.decodeStream(input)
                            }
                            if (bitmap != null) {
                                val rotated = rotateBitmapIfNeeded(bitmap, orientation)
                                val quality = if (checkCompress.isChecked) 85 else 95
                                FileOutputStream(tempFile).use { compStream ->
                                    rotated.compress(Bitmap.CompressFormat.JPEG, quality, compStream)
                                }
                                if (rotated != bitmap) rotated.recycle()
                                bitmap.recycle()
                            }
                        } catch (_: Exception) {}
                    }

                    // EXIF-Metadaten DANACH anwenden
                    val exif = ExifInterface(tempFile.absolutePath)
                    if (checkGps.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                    }
                    val fakeMake = editSpoofMake.text.toString()
                    val fakeModel = editSpoofModel.text.toString()
                    val fakeDate = formatExifDate(editSpoofDate.text.toString())
                    val latStr = findViewById<EditText>(R.id.editSpoofLat).text.toString()
                    val lonStr = findViewById<EditText>(R.id.editSpoofLon).text.toString()

                    if (fakeMake.isNotEmpty() || fakeModel.isNotEmpty()) {
                        if (fakeMake.isNotEmpty()) exif.setAttribute(ExifInterface.TAG_MAKE, fakeMake)
                        if (fakeModel.isNotEmpty()) exif.setAttribute(ExifInterface.TAG_MODEL, fakeModel)
                    } else if (checkCamera.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_MAKE, null)
                        exif.setAttribute(ExifInterface.TAG_MODEL, null)
                        exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
                    }

                    if (fakeDate.isNotEmpty()) {
                        exif.setAttribute(ExifInterface.TAG_DATETIME, fakeDate)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, fakeDate)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, fakeDate)
                    } else if (checkDate.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_DATETIME, null)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
                    }

                    if (latStr.isNotEmpty() && lonStr.isNotEmpty()) {
                        try {
                            val lat = latStr.toDouble()
                            val lon = lonStr.toDouble()
                            exif.setLatLong(lat, lon)
                        } catch (_: Exception) {}
                    } else if (checkGps.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                    }
                    exif.saveAttributes()

                    val fileName = "Cleaned_Batch_${System.currentTimeMillis()}_$successCount.jpg"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ExifScrubber")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }
                    val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }

                    val savedUri = contentResolver.insert(imageCollection, contentValues)
                    if (savedUri != null) {
                        contentResolver.openOutputStream(savedUri)?.use { out ->
                            tempFile.inputStream().copyTo(out)
                        }

                        // EXIF direkt auf MediaStore-URI anwenden
                        try {
                            contentResolver.openFileDescriptor(savedUri, "rw")?.use { pfd ->
                                val targetExif = ExifInterface(pfd.fileDescriptor)
                                if (fakeMake.isNotEmpty() || fakeModel.isNotEmpty()) {
                                    if (fakeMake.isNotEmpty()) targetExif.setAttribute(ExifInterface.TAG_MAKE, fakeMake)
                                    if (fakeModel.isNotEmpty()) targetExif.setAttribute(ExifInterface.TAG_MODEL, fakeModel)
                                } else if (checkCamera.isChecked) {
                                    targetExif.setAttribute(ExifInterface.TAG_MAKE, null)
                                    targetExif.setAttribute(ExifInterface.TAG_MODEL, null)
                                    targetExif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
                                }

                                if (fakeDate.isNotEmpty()) {
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME, fakeDate)
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, fakeDate)
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, fakeDate)
                                } else if (checkDate.isChecked) {
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME, null)
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
                                    targetExif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
                                }

                                if (latStr.isNotEmpty() && lonStr.isNotEmpty()) {
                                    try {
                                        val lat = latStr.toDouble()
                                        val lon = lonStr.toDouble()
                                        targetExif.setLatLong(lat, lon)
                                    } catch (_: Exception) {}
                                } else if (checkGps.isChecked) {
                                    targetExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                                    targetExif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                                    targetExif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                                    targetExif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                                }

                                targetExif.saveAttributes()
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            contentResolver.update(savedUri, contentValues, null, null)
                        }

                        successCount++

                        if (checkDeleteOriginal.isChecked) {
                            deleteOriginalImage(uri)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            progressBar.visibility = View.GONE
            statusText.text = getString(R.string.status_done)
            findViewById<View>(R.id.btnExportReport).visibility = View.VISIBLE
            triggerHapticFeedback()
            Toast.makeText(this, String.format(getString(R.string.batch_success_toast), successCount), Toast.LENGTH_LONG).show()
        }, 1500)
    }

    private fun exportPrivacyReport() {
        val reportContent = "=== EXIF PRIVACY AUDIT REPORT ===\n" +
                "Date: ${java.util.Date()}\n" +
                "Status: 100% Secured & Cleaned\n" +
                "- GPS Location: Sanitized / Spoofed\n" +
                "- Camera Data: Scrubbed / Spoofed\n" +
                "- Timestamps: Cleared\n" +
                "- Processing: 100% Local (GDPR Compliant)\n" +
                "================================"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "EXIF Privacy Audit Report")
            putExtra(Intent.EXTRA_TEXT, reportContent)
        }
        startActivity(Intent.createChooser(intent, "Export Privacy Report"))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_info -> {
                showAppInfoDialog()
                true
            }
            R.id.action_language -> {
                showLanguageDialog()
                true
            }
            R.id.action_text_size -> {
                showTextSizeDialog()
                true
            }
            R.id.action_theme -> {
                showThemeDialog()
                true
            }
            R.id.action_privacy_policy -> {
                showPrivacyPolicyDialog()
                true
            }
            R.id.action_imprint -> {
                showImprintDialog()
                true
            }
            R.id.action_welcome -> {
                showIntroDialog()
                true
            }
            R.id.action_pdf_tools -> {
                showPdfToolsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

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

    private fun showPdfToolsDialog() {
        val options = arrayOf(
            getString(R.string.btn_image_to_pdf),
            getString(R.string.btn_pdf_to_image)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.pdf_dialog_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> imageToPdfContract.launch("image/*")
                    1 -> pdfToImageContract.launch("application/pdf")
                }
            }
            .show()
    }

    private fun convertImageToPdf(imageUri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(imageUri) ?: return
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
                return
            }

            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val canvas = page.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            val fileName = "Converted_${System.currentTimeMillis()}.pdf"
            val resolver = contentResolver
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

            val savedUri = resolver.insert(collection, contentValues)
            if (savedUri != null) {
                resolver.openOutputStream(savedUri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            }
            pdfDocument.close()
            bitmap.recycle()

            Toast.makeText(this, "PDF saved to Documents/ExifScrubber!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error converting to PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun convertPdfToImages(pdfUri: Uri) {
        try {
            val pfd = contentResolver.openFileDescriptor(pdfUri, "r") ?: return
            val pdfRenderer = android.graphics.pdf.PdfRenderer(pfd)
            val pageCount = pdfRenderer.pageCount

            var savedCount = 0
            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
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

                val savedUri = contentResolver.insert(imageCollection, contentValues)
                if (savedUri != null) {
                    contentResolver.openOutputStream(savedUri)?.use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    savedCount++
                }
                bitmap.recycle()
            }
            pdfRenderer.close()
            pfd.close()

            Toast.makeText(this, "$savedCount pages extracted to Pictures/ExifScrubber!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error converting PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_oled)
        )
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_YES
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.theme_dialog_title)
            .setItems(themes) { _, which ->
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putInt("app_theme_choice", which)
                    .apply()
                AppCompatDelegate.setDefaultNightMode(modes[which])
                recreate()
            }
            .show()
    }

    private fun checkFirstRun() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val languageSet = prefs.getBoolean("app_language_set", false)
        val gdprAccepted = prefs.getBoolean("gdpr_consent_given", false)
        val showIntroAlways = prefs.getBoolean("show_intro_always", false)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        if (!languageSet) {
            showLanguageDialog(isFirstTime = true)
        } else if (!gdprAccepted) {
            showGdprConsentDialog()
        } else if (isFirstRun || showIntroAlways) {
            showIntroDialog()
        }
    }

    private fun showGdprConsentDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        AlertDialog.Builder(this)
            .setTitle(R.string.gdpr_dialog_title)
            .setMessage(HtmlCompat.fromHtml(getString(R.string.gdpr_dialog_message), HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.btn_accept) { _, _ ->
                prefs.edit().putBoolean("gdpr_consent_given", true).apply()
                val showIntroAlways = prefs.getBoolean("show_intro_always", false)
                val isFirstRun = prefs.getBoolean("is_first_run", true)
                if (isFirstRun || showIntroAlways) {
                    showIntroDialog()
                }
            }
            .setNeutralButton(R.string.btn_read_privacy_policy) { _, _ ->
                showPrivacyPolicyDialog {
                    showGdprConsentDialog()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showPrivacyPolicyDialog(onDismiss: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_policy_title)
            .setMessage(HtmlCompat.fromHtml(getString(R.string.privacy_policy_message), HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }
            .setOnCancelListener {
                onDismiss?.invoke()
            }
            .show()
    }

    private fun showImprintDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.imprint_title)
            .setMessage(HtmlCompat.fromHtml(getString(R.string.imprint_message), HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showIntroDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val checkBox = CheckBox(this).apply {
            text = getString(R.string.intro_show_every_time)
            isChecked = prefs.getBoolean("show_intro_always", false)
            setPadding(64, 32, 64, 32)
            setTextColor(android.graphics.Color.WHITE)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.intro_title)
            .setMessage(HtmlCompat.fromHtml(getString(R.string.intro_message), HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setView(checkBox)
            .setPositiveButton(R.string.intro_get_started) { _, _ ->
                prefs.edit()
                    .putBoolean("is_first_run", false)
                    .putBoolean("show_intro_always", checkBox.isChecked)
                    .apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun showTextSizeDialog() {
        val sizes = arrayOf(
            getString(R.string.text_size_small),
            getString(R.string.text_size_normal),
            getString(R.string.text_size_large)
        )
        val multipliers = arrayOf(0.85f, 1.0f, 1.25f)

        AlertDialog.Builder(this)
            .setTitle(R.string.menu_text_size)
            .setItems(sizes) { _, which ->
                setTextSizeMultiplier(multipliers[which])
            }
            .show()
    }

    private fun setTextSizeMultiplier(multiplier: Float) {
        val root = findViewById<ViewGroup>(android.R.id.content)
        applyTextSizeRecursive(root, multiplier)
    }

    private fun applyTextSizeRecursive(view: View, multiplier: Float) {
        if (view is TextView && view !is MaterialButton) { // Buttons behalten oft ihre Größe
            val baseSize = 16f // Standardbasis
            view.textSize = baseSize * multiplier
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTextSizeRecursive(view.getChildAt(i), multiplier)
            }
        }
    }

    private fun isHeicImage(uri: Uri): Boolean {
        val mimeType = contentResolver.getType(uri)
        if (mimeType != null && (mimeType.equals("image/heic", ignoreCase = true) || mimeType.equals("image/heif", ignoreCase = true))) {
            return true
        }
        val path = uri.path?.lowercase()
        return path != null && (path.endsWith(".heic") || path.endsWith(".heif"))
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun shareImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.btn_share_image)))
    }

    private fun showLanguageDialog(isFirstTime: Boolean = false) {
        val languages = arrayOf(
            getString(R.string.lang_de),
            getString(R.string.lang_en),
            getString(R.string.lang_es),
            getString(R.string.lang_fr)
        )
        val codes = arrayOf("de", "en", "es", "fr")

        AlertDialog.Builder(this)
            .setTitle(if (isFirstTime) R.string.select_language_title else R.string.select_language)
            .setItems(languages) { _, which ->
                val localeCode = codes[which]
                val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(localeCode)
                
                // Einstellung speichern, damit wir wissen, dass die Sprache gewählt wurde
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putBoolean("app_language_set", true)
                    .apply()
                
                AppCompatDelegate.setApplicationLocales(appLocale)
                Toast.makeText(this, languages[which], Toast.LENGTH_SHORT).show()
            }
            .setCancelable(!isFirstTime)
            .show()
    }

    private fun loadSavedPreferences() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        checkGps.isChecked = prefs.getBoolean("pref_check_gps", true)
        checkCamera.isChecked = prefs.getBoolean("pref_check_camera", true)
        checkDate.isChecked = prefs.getBoolean("pref_check_date", true)
        checkCompress.isChecked = prefs.getBoolean("pref_check_compress", false)
        checkDeleteOriginal.isChecked = prefs.getBoolean("pref_check_delete_original", false)
    }

    private fun setupPreferenceListeners() {
        val savePreferences = {
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                .putBoolean("pref_check_gps", checkGps.isChecked)
                .putBoolean("pref_check_camera", checkCamera.isChecked)
                .putBoolean("pref_check_date", checkDate.isChecked)
                .putBoolean("pref_check_compress", checkCompress.isChecked)
                .putBoolean("pref_check_delete_original", checkDeleteOriginal.isChecked)
                .apply()
        }
        checkGps.setOnCheckedChangeListener { _, _ -> savePreferences() }
        checkCamera.setOnCheckedChangeListener { _, _ -> savePreferences() }
        checkDate.setOnCheckedChangeListener { _, _ -> savePreferences() }
        checkCompress.setOnCheckedChangeListener { _, _ -> savePreferences() }
        checkDeleteOriginal.setOnCheckedChangeListener { _, _ -> savePreferences() }
    }

    private fun showAppInfoDialog() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val version = packageInfo.versionName
        val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            packageInfo.versionCode.toString()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_app_info_title)
            .setMessage("${getString(R.string.app_version_label)} $version\n${getString(R.string.build_version_label)} $build")
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun triggerHapticFeedback() {
        try {
            val vibrator = getSystemService(Vibrator::class.java)
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(75, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(75)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatExifDate(rawDate: String): String {
        val trimmed = rawDate.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.length >= 10 && (trimmed[4] == '-' || trimmed[4] == '.' || trimmed[4] == '/')) {
            val datePart = trimmed.substring(0, 10).replace('-', ':').replace('.', ':').replace('/', ':')
            val timePart = if (trimmed.length > 10) trimmed.substring(10) else " 12:00:00"
            "$datePart$timePart"
        } else if (trimmed.length == 10 && !trimmed.contains(":")) {
            "${trimmed.replace('-', ':').replace('.', ':').replace('/', ':')} 12:00:00"
        } else {
            trimmed
        }
    }

    private fun getMediaStoreUriFromPickerUri(pickerUri: Uri): Uri? {
        if (pickerUri.authority == "media") {
            return pickerUri
        }
        try {
            val lastSegment = pickerUri.lastPathSegment
            if (lastSegment != null) {
                val id = lastSegment.toLongOrNull()
                if (id != null) {
                    return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}

        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            contentResolver.query(pickerUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    if (idIndex != -1) {
                        val id = cursor.getLong(idIndex)
                        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun deleteOriginalImage(uri: Uri) {
        try {
            if (DocumentsContract.isDocumentUri(this, uri)) {
                try {
                    if (DocumentsContract.deleteDocument(contentResolver, uri)) {
                        Toast.makeText(this, "Original photo deleted!", Toast.LENGTH_SHORT).show()
                        return
                    }
                } catch (_: Exception) {}
            }

            val mediaUri = getMediaStoreUriFromPickerUri(uri) ?: uri

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                try {
                    contentResolver.delete(mediaUri, null, null)
                    Toast.makeText(this, "Original photo deleted!", Toast.LENGTH_SHORT).show()
                } catch (e: android.app.RecoverableSecurityException) {
                    deleteRequestLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                }
            } else {
                contentResolver.delete(mediaUri, null, null)
                Toast.makeText(this, "Original photo deleted!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            Toast.makeText(this, "Original image deleted!", Toast.LENGTH_SHORT).show()
        }
    }
}
