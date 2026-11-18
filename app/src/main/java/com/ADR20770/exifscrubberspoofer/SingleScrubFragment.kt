package com.ADR20770.exifscrubberspoofer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Random

class SingleScrubFragment : Fragment() {

    private lateinit var statusText: TextView
    private lateinit var btnSelectImage: MaterialButton
    private lateinit var btnScrubData: MaterialButton
    private lateinit var btnShareImage: MaterialButton
    private lateinit var imagePreview: ImageView
    private lateinit var previewPlaceholder: View
    private lateinit var progressBar: ProgressBar
    private lateinit var chipCleanedBadge: Chip

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
    private lateinit var spinnerCustomPresets: Spinner

    private var imageUri: Uri? = null
    private var cleanedImageUri: Uri? = null
    private var currentExifData: Map<String, String>? = null

    private lateinit var presetManager: PresetManager
    private lateinit var historyManager: HistoryManager

    private val pickMediaContract = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            cleanedImageUri = null
            btnShareImage.visibility = View.GONE
            chipCleanedBadge.visibility = View.GONE
            statusText.text = getString(R.string.status_image_loaded)
            btnScrubData.isEnabled = true

            imagePreview.setImageURI(uri)
            imagePreview.visibility = View.VISIBLE
            previewPlaceholder.visibility = View.GONE

            displayMetadata(uri)
        } else {
            statusText.text = getString(R.string.status_no_image)
            imagePreview.visibility = View.GONE
            previewPlaceholder.visibility = View.VISIBLE
            metadataCard.visibility = View.GONE
        }
    }

    private val mapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val lat = data?.getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0.0) ?: 0.0
            val lon = data?.getDoubleExtra(MapPickerActivity.EXTRA_LON, 0.0) ?: 0.0
            editSpoofLat.setText(lat.toString())
            editSpoofLon.setText(lon.toString())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_single_scrub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presetManager = PresetManager(requireContext())
        historyManager = HistoryManager(requireContext())

        statusText = view.findViewById(R.id.statusText)
        btnSelectImage = view.findViewById(R.id.btnSelectImage)
        btnScrubData = view.findViewById(R.id.btnScrubData)
        btnShareImage = view.findViewById(R.id.btnShareImage)
        imagePreview = view.findViewById(R.id.imagePreview)
        previewPlaceholder = view.findViewById(R.id.previewPlaceholder)
        progressBar = view.findViewById(R.id.progressBar)
        chipCleanedBadge = view.findViewById(R.id.chipCleanedBadge)

        metadataCard = view.findViewById(R.id.metadataCard)
        metadataText = view.findViewById(R.id.metadataText)
        checkGps = view.findViewById(R.id.checkGps)
        checkCamera = view.findViewById(R.id.checkCamera)
        checkDate = view.findViewById(R.id.checkDate)
        checkCompress = view.findViewById(R.id.checkCompress)
        checkDeleteOriginal = view.findViewById(R.id.checkDeleteOriginal)
        editSpoofMake = view.findViewById(R.id.editSpoofMake)
        editSpoofModel = view.findViewById(R.id.editSpoofModel)
        editSpoofDate = view.findViewById(R.id.editSpoofDate)
        editSpoofLat = view.findViewById(R.id.editSpoofLat)
        editSpoofLon = view.findViewById(R.id.editSpoofLon)
        spinnerCustomPresets = view.findViewById(R.id.spinnerCustomPresets)

        loadSavedPreferences()
        setupPreferenceListeners()
        setupCustomPresetSpinner()

        btnScrubData.isEnabled = false

        view.findViewById<View>(R.id.btnExtendedExif).setOnClickListener {
            currentExifData?.let { data ->
                val details = if (data.isEmpty()) "Keine EXIF-Tags gefunden" else data.entries.joinToString("\n") { (k, v) -> "$k: $v" }
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.btn_extended_exif)
                    .setMessage(details)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }

        // Feature 1: Map Picker & Random Offset
        view.findViewById<View>(R.id.btnPickLocation).setOnClickListener {
            val intent = Intent(requireContext(), MapPickerActivity::class.java)
            mapPickerLauncher.launch(intent)
        }

        view.findViewById<View>(R.id.btnRandomOffset).setOnClickListener {
            val lat = editSpoofLat.text.toString().toDoubleOrNull() ?: 52.5200
            val lon = editSpoofLon.text.toString().toDoubleOrNull() ?: 13.4050
            // ~1000m random offset
            val random = Random()
            val latOffset = (random.nextDouble() - 0.5) * 0.02
            val lonOffset = (random.nextDouble() - 0.5) * 0.02
            editSpoofLat.setText(String.format(java.util.Locale.US, "%.4f", lat + latOffset))
            editSpoofLon.setText(String.format(java.util.Locale.US, "%.4f", lon + lonOffset))
            Toast.makeText(requireContext(), "Zufälliger GPS-Offset angewendet!", Toast.LENGTH_SHORT).show()
        }

        // Feature 2: Random Date & Shift Date
        view.findViewById<View>(R.id.btnRandomDate).setOnClickListener {
            val randomDay = Random().nextInt(365 * 25) // up to 25 years back
            val randomDate = LocalDate.now().minusDays(randomDay.toLong())
            editSpoofDate.setText(randomDate.format(DateTimeFormatter.ofPattern("yyyy:MM:dd 12:00:00")))
            Toast.makeText(requireContext(), "Zufälliges Datum generiert!", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnShiftDate).setOnClickListener {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("N Tage verschieben")
            val input = EditText(requireContext()).apply {
                hint = "Tage (z.B. -30 oder 15)"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            builder.setView(input)
            builder.setPositiveButton("Verschieben") { _, _ ->
                val days = input.text.toString().toIntOrNull() ?: 0
                val currentText = editSpoofDate.text.toString().trim()
                val baseDate = try {
                    LocalDate.parse(currentText.substring(0, 10), DateTimeFormatter.ofPattern("yyyy:MM:dd"))
                } catch (_: Exception) {
                    LocalDate.now()
                }
                val shifted = baseDate.plusDays(days.toLong())
                editSpoofDate.setText(shifted.format(DateTimeFormatter.ofPattern("yyyy:MM:dd")) + " 12:00:00")
                Toast.makeText(requireContext(), "Datum um $days Tage verschoben!", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Abbrechen", null)
            builder.show()
        }

        view.findViewById<View>(R.id.btnPresetLeica).setOnClickListener {
            editSpoofMake.setText("Leica")
            editSpoofModel.setText("M11")
        }
        view.findViewById<View>(R.id.btnPresetHasselblad).setOnClickListener {
            editSpoofMake.setText("Hasselblad")
            editSpoofModel.setText("X1D-50c")
        }
        view.findViewById<View>(R.id.btnPresetFilm).setOnClickListener {
            editSpoofMake.setText("Canon")
            editSpoofModel.setText("AE-1 Program")
        }
        view.findViewById<View>(R.id.btnPresetClear).setOnClickListener {
            editSpoofMake.setText("")
            editSpoofModel.setText("")
            editSpoofLat.setText("")
            editSpoofLon.setText("")
            editSpoofDate.setText("")
        }

        btnSelectImage.setOnClickListener {
            pickMediaContract.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnScrubData.setOnClickListener {
            imageUri?.let { uri ->
                scrubAndSaveSingleImage(uri)
            }
        }

        btnShareImage.setOnClickListener {
            cleanedImageUri?.let { uri ->
                (activity as? MainActivity)?.shareImage(uri)
            }
        }

        imageUri?.let { uri ->
            imagePreview.setImageURI(uri)
            imagePreview.visibility = View.VISIBLE
            previewPlaceholder.visibility = View.GONE
            btnScrubData.isEnabled = true
            displayMetadata(uri)
        }
    }

    private fun setupCustomPresetSpinner() {
        val presets = presetManager.getPresets()
        val list = mutableListOf("Eigene Presets wählen...") + presets.map { it.name } + listOf("+ Als Preset speichern...")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, list)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCustomPresets.adapter = adapter

        spinnerCustomPresets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position == 0) return
                if (position == list.size - 1) {
                    showSavePresetDialog()
                    spinnerCustomPresets.setSelection(0)
                } else {
                    val preset = presets[position - 1]
                    editSpoofMake.setText(preset.make)
                    editSpoofModel.setText(preset.model)
                    editSpoofDate.setText(preset.date)
                    editSpoofLat.setText(preset.lat)
                    editSpoofLon.setText(preset.lon)
                    Toast.makeText(requireContext(), "Preset '${preset.name}' geladen", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showSavePresetDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Eigenes Preset speichern")
        val input = EditText(requireContext()).apply { hint = "Preset Name (z.B. Mein Studio)" }
        builder.setView(input)
        builder.setPositiveButton("Speichern") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                val preset = CustomPreset(
                    name = name,
                    make = editSpoofMake.text.toString().trim(),
                    model = editSpoofModel.text.toString().trim(),
                    date = editSpoofDate.text.toString().trim(),
                    lat = editSpoofLat.text.toString().trim(),
                    lon = editSpoofLon.text.toString().trim()
                )
                presetManager.savePreset(preset)
                setupCustomPresetSpinner()
                Toast.makeText(requireContext(), "Preset gespeichert!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Abbrechen", null)
        builder.show()
    }

    fun setSelectedUriFromIntent(uri: Uri) {
        imageUri = uri
        if (view != null) {
            imagePreview.setImageURI(uri)
            imagePreview.visibility = View.VISIBLE
            previewPlaceholder.visibility = View.GONE
            btnScrubData.isEnabled = true
            displayMetadata(uri)
        }
    }

    private fun displayMetadata(uri: Uri) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val map = mutableMapOf<String, String>()

                @Suppress("DEPRECATION")
                val tags = arrayOf(
                    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_ORIENTATION,
                    ExifInterface.TAG_COLOR_SPACE, ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH
                )

                for (tag in tags) {
                    val attr = exif.getAttribute(tag)
                    if (attr != null) map[tag] = attr
                }

                val latLong = exif.latLong
                if (latLong != null) {
                    map["GPS_LATITUDE_LONGITUDE"] = "${latLong[0]}, ${latLong[1]}"
                }

                currentExifData = map

                val sb = StringBuilder()
                if (latLong != null) sb.append("📍 Standort: ${latLong[0]}, ${latLong[1]}\n")
                val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                if (make != null || model != null) sb.append("📷 Kamera: $make $model\n")
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (date != null) sb.append("📅 Datum: $date\n")

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

    private fun scrubAndSaveSingleImage(uri: Uri) {
        btnScrubData.isEnabled = false
        btnSelectImage.isEnabled = false
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.status_cleaning)

        lifecycleScope.launch {
            var resultUri: Uri? = null
            val ctx = requireContext()

            withContext(Dispatchers.IO) {
                resultUri = ExifScrubberEngine.processSingleImageInternal(
                    context = ctx,
                    sourceUri = uri,
                    fakeMake = editSpoofMake.text.toString().trim(),
                    fakeModel = editSpoofModel.text.toString().trim(),
                    fakeDate = editSpoofDate.text.toString().trim(),
                    latStr = editSpoofLat.text.toString().trim(),
                    lonStr = editSpoofLon.text.toString().trim(),
                    scrubGps = checkGps.isChecked,
                    scrubCamera = checkCamera.isChecked,
                    scrubDate = checkDate.isChecked,
                    checkCompress = checkCompress.isChecked
                )
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnSelectImage.isEnabled = true

                if (resultUri != null) {
                    cleanedImageUri = resultUri
                    statusText.text = getString(R.string.status_done)
                    btnShareImage.visibility = View.VISIBLE
                    chipCleanedBadge.visibility = View.VISIBLE
                    ExifScrubberEngine.triggerHapticFeedback(ctx)
                    historyManager.addHistoryItem(resultUri.toString(), true)
                    Toast.makeText(ctx, getString(R.string.save_success_toast), Toast.LENGTH_LONG).show()

                    // Feature 5: Vor/Nach-Vergleich Dialog
                    showComparisonDialog(uri, resultUri)

                    if (checkDeleteOriginal.isChecked) {
                        (activity as? MainActivity)?.deleteOriginalImage(uri)
                    }
                } else {
                    statusText.text = getString(R.string.error_scrubbing)
                    btnScrubData.isEnabled = true
                    historyManager.addHistoryItem(uri.toString(), false)
                }
            }
        }
    }

    private fun showComparisonDialog(originalUri: Uri, scrubbedUri: Uri) {
        try {
            val ctx = requireContext()
            val beforeExif = ctx.contentResolver.openInputStream(originalUri)?.use { ExifInterface(it) }
            val afterExif = ctx.contentResolver.openInputStream(scrubbedUri)?.use { ExifInterface(it) }

            val sb = StringBuilder()
            val tags = listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME, ExifInterface.TAG_GPS_LATITUDE)
            for (tag in tags) {
                val beforeVal = beforeExif?.getAttribute(tag) ?: "Keine"
                val afterVal = afterExif?.getAttribute(tag) ?: "Entfernt / Überschrieben"
                sb.append("• $tag:\n  Vorher: $beforeVal\n  Nachher: $afterVal\n\n")
            }

            AlertDialog.Builder(ctx)
                .setTitle("Vor / Nach-Vergleich (EXIF)")
                .setMessage(sb.toString())
                .setPositiveButton(R.string.ok, null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSavedPreferences() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        checkGps.isChecked = prefs.getBoolean("pref_check_gps", true)
        checkCamera.isChecked = prefs.getBoolean("pref_check_camera", true)
        checkDate.isChecked = prefs.getBoolean("pref_check_date", true)
        checkCompress.isChecked = prefs.getBoolean("pref_check_compress", false)
        checkDeleteOriginal.isChecked = prefs.getBoolean("pref_check_delete_original", false)
    }

    private fun setupPreferenceListeners() {
        val savePreferences = {
            requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit()
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
}
