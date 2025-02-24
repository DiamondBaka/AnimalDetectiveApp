package com.example.animaldetectiveapp.ui.dashboard

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import com.example.animaldetectiveapp.R
import com.example.animaldetectiveapp.databinding.FragmentDashboardBinding
import com.example.animaldetectiveapp.ui.collection.AnimalEntry
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.mapbox.maps.CameraChangedCallback
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import java.io.File

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapboxMap: MapboxMap
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private var currentPopUpData: PopUpData? = null
    private var isAnimating = false // Flag to prevent multiple pop-ups during animation

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

        binding.mapView.getMapboxMap().apply {
            mapboxMap = this
            loadStyle(Style.MAPBOX_STREETS) { style ->
                initializeMapFeatures(style)

                val callback = CameraChangedCallback { _ ->
                    currentPopUpData?.let { popUpData ->
                        val screenPosition = mapboxMap.pixelForCoordinate(popUpData.annotation.point)
                        popUpData.view.post {
                            val x = screenPosition.x - popUpData.view.width / 2f
                            val y = screenPosition.y - popUpData.view.height - 60f
                            popUpData.view.x = x.toFloat()
                            popUpData.view.y = y.toFloat()
                        }
                    }
                }
                mapboxMap.subscribeCameraChanged(callback)
            }
        }
    }

    private fun initializeMapFeatures(style: Style) {
        val annotationApi = binding.mapView.annotations
        pointAnnotationManager = annotationApi.createPointAnnotationManager()

        val waypointBitmap = getBitmapFromDrawable(R.drawable.ic_waypoint, 160, 180)
        style.addImage("waypoint-icon", waypointBitmap)

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
        setupMarkerClickListener()
    }

    private fun enableMyLocation() {
        binding.mapView.location.enabled = true

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(requireContext(), "Location permission not available", Toast.LENGTH_SHORT).show()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val userLocation = Point.fromLngLat(location.longitude, location.latitude)
                        mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(userLocation)
                                .zoom(14.0)
                                .build()
                        )
                    } else {
                        val defaultLocation = Point.fromLngLat(-122.4194, 37.7749)
                        mapboxMap.setCamera(
                            CameraOptions.Builder()
                                .center(defaultLocation)
                                .zoom(10.0)
                                .build()
                        )
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to get location", Toast.LENGTH_SHORT).show()
                }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Location access denied: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAnimalMarkers() {
        val animalsFile = File(getOutputDirectory(), "animals.json")
        if (!animalsFile.exists()) return

        try {
            val json = animalsFile.readText()
            val animalsList = Gson().fromJson(json, Array<AnimalEntry>::class.java)
            pointAnnotationManager.deleteAll()
            animalsList.forEach { entry ->
                val point = Point.fromLngLat(entry.longitude, entry.latitude)
                val annotationOptions = PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage("waypoint-icon")
                    .withData(
                        Gson().toJsonTree(
                            mapOf("image" to entry.image, "animal" to entry.animal)
                        ).asJsonObject
                    )
                pointAnnotationManager.create(annotationOptions)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error loading markers: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMarkerClickListener() {
        mapboxMap.addOnMapClickListener(OnMapClickListener { point ->
            val clickScreenPosition = mapboxMap.pixelForCoordinate(point)
            val annotations = pointAnnotationManager.annotations
            annotations.forEach { annotation ->
                val annotationScreenPosition = mapboxMap.pixelForCoordinate(annotation.point)
                val left = annotationScreenPosition.x - 80
                val right = annotationScreenPosition.x + 80
                val top = annotationScreenPosition.y - 90
                val bottom = annotationScreenPosition.y + 90

                if (clickScreenPosition.x >= left && clickScreenPosition.x <= right &&
                    clickScreenPosition.y >= top && clickScreenPosition.y <= bottom) {
                    showMarkerPopup(annotation)
                    return@OnMapClickListener true
                }
            }
            false
        })
    }

    private fun showMarkerPopup(annotation: com.mapbox.maps.plugin.annotation.generated.PointAnnotation) {
        // Prevent opening if animation is in progress
        if (isAnimating) return

        // Check if this annotation’s pop-up is already open
        currentPopUpData?.let { popUpData ->
            if (popUpData.annotation == annotation) {
                closePopup(popUpData.view) // Toggle: close if same waypoint tapped
                return
            } else {
                closePopup(popUpData.view) // Close old pop-up before opening new one
            }
        }

        val popupView = layoutInflater.inflate(R.layout.marker_popup, binding.mapView, false)
        val popupImage = popupView.findViewById<ImageView>(R.id.popupImage)
        val popupText = popupView.findViewById<TextView>(R.id.popupText)

        val data = annotation.getData()?.asJsonObject ?: return
        val imageName = data.get("image")?.asString ?: return
        val animalName = data.get("animal")?.asString ?: "Unknown Animal"

        val imageFile = File(getOutputDirectory(), imageName)
        if (imageFile.exists()) {
            Glide.with(this)
                .load(imageFile)
                .apply(RequestOptions.bitmapTransform(RoundedCorners(48)))
                .into(popupImage)
        } else {
            popupImage.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        popupText.text = animalName
        popupText.setShadowLayer(2f, 1f, 1f, Color.WHITE)

        popupView.scaleX = 0f
        popupView.scaleY = 0f
        popupView.alpha = 0f
        popupView.isClickable = true

        binding.mapView.addView(popupView)
        currentPopUpData = PopUpData(popupView, annotation)

        popupView.post {
            val screenPosition = mapboxMap.pixelForCoordinate(annotation.point)
            val x = screenPosition.x - popupView.width / 2f
            val y = screenPosition.y - popupView.height - 60f
            popupView.x = x.toFloat()
            popupView.y = y.toFloat()

            isAnimating = true
            val scaleAnimator = ObjectAnimator.ofFloat(popupView, "scaleX", 0f, 1f).apply {
                duration = 300
                addUpdateListener { popupView.scaleY = popupView.scaleX }
            }
            val alphaAnimator = ObjectAnimator.ofFloat(popupView, "alpha", 0f, 1f).apply {
                duration = 300
            }
            AnimatorSet().apply {
                playTogether(scaleAnimator, alphaAnimator)
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}
                    override fun onAnimationEnd(animation: Animator) { isAnimating = false }
                    override fun onAnimationCancel(animation: Animator) { isAnimating = false }
                    override fun onAnimationRepeat(animation: Animator) {}
                })
                start()
            }
        }

        popupView.setOnClickListener {
            closePopup(popupView)
        }
    }

    private fun closePopup(popupView: View) {
        if (isAnimating) return // Prevent multiple closes during animation

        currentPopUpData = null // Clear immediately to prevent overlap
        isAnimating = true
        val scaleAnimator = ObjectAnimator.ofFloat(popupView, "scaleX", 1f, 0f).apply {
            duration = 300
            addUpdateListener { popupView.scaleY = popupView.scaleX }
        }
        val alphaAnimator = ObjectAnimator.ofFloat(popupView, "alpha", 1f, 0f).apply {
            duration = 300
        }
        AnimatorSet().apply {
            playTogether(scaleAnimator, alphaAnimator)
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    binding.mapView.removeView(popupView)
                    isAnimating = false
                }
                override fun onAnimationCancel(animation: Animator) {
                    binding.mapView.removeView(popupView)
                    isAnimating = false
                }
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = requireContext().externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return mediaDir ?: requireContext().filesDir
    }

    private fun getBitmapFromDrawable(drawableId: Int, width: Int, height: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableId)
        val originalBitmap = Bitmap.createBitmap(
            drawable?.intrinsicWidth ?: 100,
            drawable?.intrinsicHeight ?: 100,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(originalBitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return Bitmap.createScaledBitmap(originalBitmap, width, height, true)
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (::pointAnnotationManager.isInitialized) {
            loadAnimalMarkers()
        }
    }

    data class PopUpData(val view: View, val annotation: com.mapbox.maps.plugin.annotation.generated.PointAnnotation)
}