package com.ADR20770.exifscrubberspoofer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchScrubFragment : Fragment() {

    private lateinit var btnSelectImages: MaterialButton
    private lateinit var batchEmptyCard: View
    private lateinit var batchProgressCard: View
    private lateinit var batchProgressText: TextView
    private lateinit var batchCurrentFileName: TextView
    private lateinit var batchProgressBar: ProgressBar
    private lateinit var batchResultText: TextView

    private val pickMultipleMediaContract = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scrubAndSaveMultipleImages(uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_batch_scrub, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSelectImages = view.findViewById(R.id.btnSelectImages)
        batchEmptyCard = view.findViewById(R.id.batchEmptyCard)
        batchProgressCard = view.findViewById(R.id.batchProgressCard)
        batchProgressText = view.findViewById(R.id.batchProgressText)
        batchCurrentFileName = view.findViewById(R.id.batchCurrentFileName)
        batchProgressBar = view.findViewById(R.id.batchProgressBar)
        batchResultText = view.findViewById(R.id.batchResultText)

        btnSelectImages.setOnClickListener {
            pickMultipleMediaContract.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun scrubAndSaveMultipleImages(uris: List<Uri>) {
        btnSelectImages.isEnabled = false
        batchEmptyCard.visibility = View.GONE
        batchProgressCard.visibility = View.VISIBLE
        batchResultText.visibility = View.GONE
        batchProgressBar.max = uris.size
        batchProgressBar.progress = 0

        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val checkGps = prefs.getBoolean("pref_check_gps", true)
        val checkCamera = prefs.getBoolean("pref_check_camera", true)
        val checkDate = prefs.getBoolean("pref_check_date", true)
        val checkCompress = prefs.getBoolean("pref_check_compress", false)
        val checkDeleteOriginal = prefs.getBoolean("pref_check_delete_original", false)

        lifecycleScope.launch {
            var successCount = 0

            withContext(Dispatchers.IO) {
                for ((index, uri) in uris.withIndex()) {
                    val currentNum = index + 1
                    withContext(Dispatchers.Main) {
                        batchProgressText.text = getString(R.string.batch_progress_format, currentNum, uris.size)
                        batchCurrentFileName.text = uri.lastPathSegment ?: "Unbenannt"
                        batchProgressBar.progress = currentNum
                    }

                    try {
                        val resultUri = ExifScrubberEngine.processSingleImageInternal(
                            context = ctx,
                            sourceUri = uri,
                            fakeMake = "",
                            fakeModel = "",
                            fakeDate = "",
                            latStr = "",
                            lonStr = "",
                            scrubGps = checkGps,
                            scrubCamera = checkCamera,
                            scrubDate = checkDate,
                            checkCompress = checkCompress
                        )

                        if (resultUri != null) {
                            successCount++
                            if (checkDeleteOriginal) {
                                withContext(Dispatchers.Main) {
                                    (activity as? MainActivity)?.deleteOriginalImage(uri)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                btnSelectImages.isEnabled = true
                batchResultText.text = getString(R.string.batch_result_format, successCount, uris.size)
                batchResultText.visibility = View.VISIBLE
                ExifScrubberEngine.triggerHapticFeedback(ctx)
                Toast.makeText(ctx, String.format(getString(R.string.batch_success_toast), successCount), Toast.LENGTH_LONG).show()
            }
        }
    }
}
