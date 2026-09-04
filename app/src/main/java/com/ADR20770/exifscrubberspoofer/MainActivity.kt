package com.ADR20770.exifscrubberspoofer

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.google.android.gms.ads.MobileAds
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
    private lateinit var editSpoofMake: EditText
    private lateinit var editSpoofModel: EditText

    private var imageUri: Uri? = null
    private var cleanedImageUri: Uri? = null

    private val getImageContract = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        checkFirstRun()

        toolbar.setNavigationOnClickListener {
            openOptionsMenu()
        }

        MobileAds.initialize(this) {}

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
        editSpoofMake = findViewById(R.id.editSpoofMake)
        editSpoofModel = findViewById(R.id.editSpoofModel)

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        btnScrubData.isEnabled = false

        btnSelectImage.setOnClickListener {
            getImageContract.launch("image/*")
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

                    // 2. EXIF-Metadaten im Cache restlos löschen oder spoofen
                    val exif = ExifInterface(tempFile.absolutePath)
                    
                    if (checkGps.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, null)
                        exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, null)
                    }
                    
                    if (checkCamera.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_MAKE, null)
                        exif.setAttribute(ExifInterface.TAG_MODEL, null)
                        exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
                    }
                    
                    if (checkDate.isChecked) {
                        exif.setAttribute(ExifInterface.TAG_DATETIME, null)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
                    }
                    
                    // Spoofing anwenden
                    val fakeMake = editSpoofMake.text.toString()
                    val fakeModel = editSpoofModel.text.toString()
                    if (fakeMake.isNotEmpty()) exif.setAttribute(ExifInterface.TAG_MAKE, fakeMake)
                    if (fakeModel.isNotEmpty()) exif.setAttribute(ExifInterface.TAG_MODEL, fakeModel)
                    
                    exif.saveAttributes()

                    // 3. Das saubere Bild in die offizielle Handy-Galerie (MediaStore) exportieren
                    val fileName = "Cleaned_${System.currentTimeMillis()}.jpg"
                    val resolver = contentResolver
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

                    val savedUri = resolver.insert(imageCollection, contentValues)
                    if (savedUri != null) {
                        val galleryOutputStream = resolver.openOutputStream(savedUri)
                        if (galleryOutputStream != null) {
                            tempFile.inputStream().copyTo(galleryOutputStream)
                            galleryOutputStream.close()
                            cleanedImageUri = savedUri // Speichern für Share-Button
                        }
                    }

                    statusText.text = getString(R.string.status_done)
                    progressBar.visibility = View.GONE
                    btnSelectImage.isEnabled = true
                    btnShareImage.visibility = View.VISIBLE
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkFirstRun() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val languageSet = prefs.getBoolean("app_language_set", false)
        val showIntroAlways = prefs.getBoolean("show_intro_always", false)
        val isFirstRun = prefs.getBoolean("is_first_run", true)

        if (!languageSet) {
            showLanguageDialog(isFirstTime = true)
        } else if (isFirstRun || showIntroAlways) {
            showIntroDialog()
        }
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
}
