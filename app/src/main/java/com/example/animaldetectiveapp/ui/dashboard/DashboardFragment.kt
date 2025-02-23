package com.example.animaldetectiveapp.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.example.animaldetectiveapp.R
import com.example.animaldetectiveapp.databinding.FragmentDashboardBinding
import com.example.animaldetectiveapp.ui.collection.AnimalEntry
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import java.io.File

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                enableMyLocation()
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ViewModelProvider(this).get(DashboardViewModel::class.java)
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = SupportMapFragment.newInstance()
        childFragmentManager.beginTransaction()
            .replace(binding.mapContainer.id, mapFragment)
            .commit()

        mapFragment.getMapAsync { map ->
            googleMap = map
            map.mapType = GoogleMap.MAP_TYPE_NORMAL

            // Apply custom map style (if desired)
            try {
                val success = map.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style)
                )
                if (!success) {
                    Toast.makeText(requireContext(), "Style parsing failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error applying map style", Toast.LENGTH_SHORT).show()
            }

            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                enableMyLocation()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            loadAnimalMarkers()

            googleMap?.setOnMarkerClickListener { marker ->
                // Instead of showing a Toast, display the custom popup anchored to the marker
                showMarkerPopup(marker)
                true // consume the event
            }
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLocation = LatLng(location.latitude, location.longitude)
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))
                } else {
                    val defaultLocation = LatLng(37.7749, -122.4194) // fallback location
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
                }
            }
        }
    }

    private fun loadAnimalMarkers() {
        // Read the animals.json file from the shared output directory
        val animalsFile = File(getOutputDirectory(), "animals.json")
        if (animalsFile.exists()) {
            val json = animalsFile.readText()
            try {
                val animalsList = Gson().fromJson(json, Array<AnimalEntry>::class.java)
                animalsList.forEach { entry ->
                    val position = LatLng(entry.latitude, entry.longitude)
                    val marker = googleMap?.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title(entry.animal)
                    )
                    // Save the image file name in the marker's tag for later retrieval
                    marker?.tag = entry.image
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading markers", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMarkerPopup(marker: Marker) {
        // Inflate the popup layout
        val popupView = layoutInflater.inflate(R.layout.marker_popup, binding.mapContainer, false)
        val popupImage = popupView.findViewById<ImageView>(R.id.popupImage)
        val popupText = popupView.findViewById<TextView>(R.id.popupText)

        // Retrieve the image file name from the marker's tag
        val imageName = marker.tag as? String ?: return

        // Load the image from the output directory
        val imageFile = File(getOutputDirectory(), imageName)
        if (imageFile.exists()) {
            Glide.with(this)
                .load(imageFile)
                .apply(RequestOptions.bitmapTransform(RoundedCorners(48)))
                .into(popupImage)
        }
        // Set any text details (for example, the animal name)
        popupText.text = marker.title ?: "Details"

        // Add the popup view to the map container
        // (Assuming mapContainer is a FrameLayout covering the map)
        binding.mapContainer.addView(popupView)

        // Postpone positioning until the view has been laid out
        popupView.post {
            // Convert marker's LatLng to screen coordinates
            val projection = googleMap?.projection
            val markerScreenPosition = projection?.toScreenLocation(marker.position)
            if (markerScreenPosition != null) {
                // Calculate an offset so the popup appears above the marker.
                val x = markerScreenPosition.x - popupView.width / 2f
                val y = markerScreenPosition.y - popupView.height - 20f  // 20px offset above the marker
                // Set the position of the popup view
                popupView.x = x
                popupView.y = y
            }
        }

        // Optional: Dismiss the popup when it is tapped
        popupView.setOnClickListener {
            binding.mapContainer.removeView(popupView)
            // Optionally, navigate to a detailed view in your CollectionFragment here.
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = requireContext().externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return mediaDir ?: requireContext().filesDir
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
