package com.ADR20770.exifscrubberspoofer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MapPickerActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var selectedGeoPoint: GeoPoint? = null
    private var currentMarker: Marker? = null

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_map_picker)

        val toolbar = findViewById<Toolbar>(R.id.mapToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        val controller = mapView.controller
        controller.setZoom(6.0)
        val germanyCenter = GeoPoint(51.1657, 10.4515)
        controller.setCenter(germanyCenter)

        val mReceive = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    setMarkerAt(p)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        }
        val overlayEvents = MapEventsOverlay(mReceive)
        mapView.overlays.add(overlayEvents)

        val btnConfirm = findViewById<MaterialButton>(R.id.btnConfirmLocation)
        btnConfirm.setOnClickListener {
            selectedGeoPoint?.let { point ->
                val intent = Intent().apply {
                    putExtra(EXTRA_LAT, point.latitude)
                    putExtra(EXTRA_LON, point.longitude)
                }
                setResult(RESULT_OK, intent)
                finish()
            }
        }
    }

    private fun setMarkerAt(geoPoint: GeoPoint) {
        selectedGeoPoint = geoPoint
        if (currentMarker != null) {
            mapView.overlays.remove(currentMarker)
        }
        currentMarker = Marker(mapView).apply {
            position = geoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Selected Location"
        }
        mapView.overlays.add(currentMarker)
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
