package com.ADR20770.exifscrubberspoofer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Vibrator
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ExifScrubberEngine {

    private const val TAG = "ExifScrubberEngine"

    fun processSingleImageInternal(
        context: Context,
        sourceUri: Uri,
        fakeMake: String,
        fakeModel: String,
        fakeDate: String,
        latStr: String,
        lonStr: String,
        scrubGps: Boolean,
        scrubCamera: Boolean,
        scrubDate: Boolean,
        checkCompress: Boolean
    ): Uri? {
        val resolver = context.contentResolver
        val tempFile = File(context.cacheDir, "temp_proc_${System.currentTimeMillis()}.jpg")

        try {
            // 1. Quellbild in temporäre Sandbox-Datei kopieren
            resolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            // 2. HEIC / Komprimierung verarbeiten (VOR EXIF-Anwendung)
            val isHeic = isHeicImage(context, sourceUri)
            if (isHeic || checkCompress) {
                try {
                    val orientation = resolver.openInputStream(sourceUri)?.use { input ->
                        ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } ?: ExifInterface.ORIENTATION_NORMAL

                    val bitmap = resolver.openInputStream(sourceUri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                    if (bitmap != null) {
                        val rotated = rotateBitmapIfNeeded(bitmap, orientation)
                        val quality = if (checkCompress) 85 else 95
                        FileOutputStream(tempFile).use { compStream ->
                            rotated.compress(Bitmap.CompressFormat.JPEG, quality, compStream)
                        }
                        if (rotated != bitmap) rotated.recycle()
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Bitmap compression/rotation error", e)
                }
            }

            // 3. EXIF-Änderungen auf der temporären Sandbox-Datei anwenden
            val tempExif = ExifInterface(tempFile.absolutePath)
            applyExifChanges(
                exif = tempExif,
                fakeMake = fakeMake,
                fakeModel = fakeModel,
                fakeDate = fakeDate,
                latStr = latStr,
                lonStr = lonStr,
                scrubGps = scrubGps,
                scrubCamera = scrubCamera,
                scrubDate = scrubDate
            )
            tempExif.saveAttributes()

            // 4. MediaStore-Eintrag mit IS_PENDING = 1 anlegen (Android 10+)
            val fileName = "Cleaned_${System.currentTimeMillis()}.jpg"
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

            val savedUri = resolver.insert(imageCollection, contentValues) ?: return null

            try {
                resolver.openOutputStream(savedUri)?.use { out ->
                    tempFile.inputStream().copyTo(out)
                } ?: throw IOException("Could not open destination output stream")

                // EXIF DIREKT auf der finalen MediaStore-URI via FileDescriptor schreiben
                resolver.openFileDescriptor(savedUri, "rw")?.use { pfd ->
                    val targetExif = ExifInterface(pfd.fileDescriptor)
                    applyExifChanges(
                        exif = targetExif,
                        fakeMake = fakeMake,
                        fakeModel = fakeModel,
                        fakeDate = fakeDate,
                        latStr = latStr,
                        lonStr = lonStr,
                        scrubGps = scrubGps,
                        scrubCamera = scrubCamera,
                        scrubDate = scrubDate
                    )
                    targetExif.saveAttributes()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(savedUri, contentValues, null, null)
                }

                tempFile.delete()
                return savedUri

            } catch (e: Exception) {
                Log.e(TAG, "Error writing to MediaStore destination URI", e)
                resolver.delete(savedUri, null, null)
                tempFile.delete()
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in processSingleImageInternal", e)
            tempFile.delete()
            return null
        }
    }

    fun applyExifChanges(
        exif: ExifInterface,
        fakeMake: String,
        fakeModel: String,
        fakeDate: String,
        latStr: String,
        lonStr: String,
        scrubGps: Boolean,
        scrubCamera: Boolean,
        scrubDate: Boolean
    ) {
        // 1. Kamera-Infos
        if (fakeMake.isNotEmpty() || fakeModel.isNotEmpty()) {
            if (fakeMake.isNotEmpty()) exif.setAttribute(ExifInterface.TAG_MAKE, fakeMake)
            if (fakeModel.isNotEmpty()) exif.setAttribute(ExifInterface.TAG_MODEL, fakeModel)
        } else if (scrubCamera) {
            exif.setAttribute(ExifInterface.TAG_MAKE, null)
            exif.setAttribute(ExifInterface.TAG_MODEL, null)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, null)
        }

        // 2. Datum & Uhrzeit
        val formattedDate = formatExifDate(fakeDate)
        if (formattedDate.isNotEmpty()) {
            exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formattedDate)
        } else if (scrubDate) {
            exif.setAttribute(ExifInterface.TAG_DATETIME, null)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null)
        }

        // 3. GPS-Daten (A3: Vollständiges Löschen aller bekannten GPS-Tags)
        if (latStr.isNotEmpty() && lonStr.isNotEmpty()) {
            try {
                val lat = latStr.toDouble()
                val lon = lonStr.toDouble()
                exif.setLatLong(lat, lon)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid GPS latitude/longitude format: $latStr, $lonStr", e)
            }
        } else if (scrubGps) {
            removeAllGpsTags(exif)
        }
    }

    fun removeAllGpsTags(exif: ExifInterface) {
        @Suppress("DEPRECATION")
        val gpsTags = arrayOf(
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_VERSION_ID
        )
        for (tag in gpsTags) {
            exif.setAttribute(tag, null)
        }
    }

    fun formatExifDate(rawDate: String): String {
        val trimmed = rawDate.trim()
        if (trimmed.isEmpty()) return ""

        val formatters = listOf(
            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy:MM:dd")
        )

        for (formatter in formatters) {
            try {
                val dateTime = LocalDateTime.parse(trimmed, formatter)
                return dateTime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
            } catch (_: Exception) {
                try {
                    val dateOnly = LocalDate.parse(trimmed, formatter)
                    return dateOnly.atTime(12, 0, 0).format(DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                } catch (_: Exception) {}
            }
        }

        Log.w(TAG, "Failed to parse date string: $trimmed")
        return ""
    }

    fun isHeicImage(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null && (mimeType.equals("image/heic", ignoreCase = true) || mimeType.equals("image/heif", ignoreCase = true))) {
            return true
        }
        val path = uri.path?.lowercase()
        return path != null && (path.endsWith(".heic") || path.endsWith(".heif"))
    }

    fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun getMediaStoreUriFromPickerUri(context: Context, pickerUri: Uri): Uri? {
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
            context.contentResolver.query(pickerUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    if (idIndex != -1) {
                        val id = cursor.getLong(idIndex)
                        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query MediaStore _ID for $pickerUri", e)
        }
        return null
    }

    fun triggerHapticFeedback(context: Context) {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(75, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(75)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }
}
