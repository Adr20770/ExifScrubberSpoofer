package com.ADR20770.exifscrubberspoofer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.os.LocaleListCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var singleScrubFragment: SingleScrubFragment
    private lateinit var batchScrubFragment: BatchScrubFragment
    private lateinit var toolsFragment: ToolsFragment
    private lateinit var activeFragment: Fragment

    // Launcher für die System-Löschbestätigung (Android 11+)
    private val deleteRequestLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.i(TAG, "Originalfoto erfolgreich gelöscht.")
            Toast.makeText(this, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "Löschen durch Nutzer abgebrochen.")
            Toast.makeText(this, getString(R.string.toast_delete_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A1: Theme-Auswahl vor setContentView
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val themeChoice = prefs.getInt("app_theme_choice", 0) // 0: System, 1: Light, 2: Dark, 3: OLED
        when (themeChoice) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2, 3 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        setContentView(R.layout.activity_main)

        // OLED Theme = schwarzer Hintergrund
        if (themeChoice == 3) {
            window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        }

        // A1 & Item 7: Edge-to-Edge Insets auf Root-Layout anwenden
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            showIntroDialog()
        }

        checkFirstRun()

        bottomNav = findViewById(R.id.bottomNavigation)

        // Fragments initialisieren & nach Config-Change auflösen (Fix 2)
        if (savedInstanceState == null) {
            singleScrubFragment = SingleScrubFragment()
            batchScrubFragment = BatchScrubFragment()
            toolsFragment = ToolsFragment()
            activeFragment = singleScrubFragment
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, toolsFragment, "3").hide(toolsFragment)
                .add(R.id.fragment_container, batchScrubFragment, "2").hide(batchScrubFragment)
                .add(R.id.fragment_container, singleScrubFragment, "1")
                .commit()
        } else {
            singleScrubFragment = supportFragmentManager.findFragmentByTag("1") as? SingleScrubFragment ?: SingleScrubFragment()
            batchScrubFragment = supportFragmentManager.findFragmentByTag("2") as? BatchScrubFragment ?: BatchScrubFragment()
            toolsFragment = supportFragmentManager.findFragmentByTag("3") as? ToolsFragment ?: ToolsFragment()
            activeFragment = when (bottomNav.selectedItemId) {
                R.id.nav_batch -> batchScrubFragment
                R.id.nav_tools -> toolsFragment
                else -> singleScrubFragment
            }
        }

        // A2: Bottom Navigation 3 Tabs Listener
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_single -> {
                    supportFragmentManager.beginTransaction().hide(activeFragment).show(singleScrubFragment).commit()
                    activeFragment = singleScrubFragment
                    true
                }
                R.id.nav_batch -> {
                    supportFragmentManager.beginTransaction().hide(activeFragment).show(batchScrubFragment).commit()
                    activeFragment = batchScrubFragment
                    true
                }
                R.id.nav_tools -> {
                    supportFragmentManager.beginTransaction().hide(activeFragment).show(toolsFragment).commit()
                    activeFragment = toolsFragment
                    true
                }
                else -> false
            }
        }

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uri ->
                bottomNav.selectedItemId = R.id.nav_single
                singleScrubFragment.setSelectedUriFromIntent(uri)
            }
        }
    }

    internal fun deleteOriginalImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                if (DocumentsContract.isDocumentUri(this@MainActivity, uri)) {
                    try {
                        if (DocumentsContract.deleteDocument(contentResolver, uri)) {
                            Toast.makeText(this@MainActivity, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "DocumentsContract.deleteDocument failed for $uri", e)
                    }
                }

                val mediaUri = ExifScrubberEngine.getMediaStoreUriFromPickerUri(this@MainActivity, uri) ?: uri

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    } catch (e: Exception) {
                        Log.e(TAG, "createDeleteRequest failed for $mediaUri, trying direct delete", e)
                        try {
                            contentResolver.delete(mediaUri, null, null)
                            Toast.makeText(this@MainActivity, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
                        } catch (secEx: Exception) {
                            Log.e(TAG, "Direct delete failed for $mediaUri", secEx)
                            Toast.makeText(this@MainActivity, "Originalfoto konnte nicht gelöscht werden (Photo Picker Schutz).", Toast.LENGTH_LONG).show()
                        }
                    }
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    try {
                        contentResolver.delete(mediaUri, null, null)
                        Toast.makeText(this@MainActivity, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
                    } catch (e: android.app.RecoverableSecurityException) {
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                    } catch (e: Exception) {
                        Log.e(TAG, "Android 10 delete failed", e)
                    }
                } else {
                    contentResolver.delete(mediaUri, null, null)
                    Toast.makeText(this@MainActivity, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete original image $uri", e)
            }
        }
    }

    internal fun shareImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.btn_share_image)))
    }

    internal fun exportPrivacyReport() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val sb = StringBuilder()
        sb.append("=== EXIF PRIVACY AUDIT REPORT ===\n")
        sb.append("Date: ${java.util.Date()}\n")
        sb.append("Processing: 100% Local (GDPR Compliant)\n\n")
        sb.append("Executed Actions:\n")

        if (prefs.getBoolean("pref_check_camera", true)) {
            sb.append("• Camera Data: Scrubbed / Sanitized\n")
        }
        if (prefs.getBoolean("pref_check_date", true)) {
            sb.append("• Timestamp: Cleared / Sanitized\n")
        }
        if (prefs.getBoolean("pref_check_gps", true)) {
            sb.append("• GPS Location: Scrubbed / Sanitized\n")
        }
        if (prefs.getBoolean("pref_check_compress", false)) {
            sb.append("• Storage Optimization: JPEG 85% Compressed\n")
        }
        if (prefs.getBoolean("pref_check_delete_original", false)) {
            sb.append("• Original File: System Deletion Requested\n")
        }
        sb.append("================================")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_title))
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.btn_export_report)))
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
                bottomNav.selectedItemId = R.id.nav_tools
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
                val mult = multipliers[which]
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putFloat("pref_text_size_multiplier", mult)
                    .apply()
                setTextSizeMultiplier(mult)
            }
            .show()
    }

    private fun setTextSizeMultiplier(multiplier: Float) {
        val root = findViewById<ViewGroup>(android.R.id.content)
        applyTextSizeRecursive(root, multiplier)
    }

    private fun applyTextSizeRecursive(view: View, multiplier: Float) {
        if (view is TextView && view !is MaterialButton) {
            val baseSize = 16f
            view.textSize = baseSize * multiplier
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTextSizeRecursive(view.getChildAt(i), multiplier)
            }
        }
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
            setTextColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
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
}
