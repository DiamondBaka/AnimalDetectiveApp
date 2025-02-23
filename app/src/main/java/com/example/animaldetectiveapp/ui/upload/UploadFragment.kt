package com.example.animaldetectiveapp.ui.upload

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.animaldetectiveapp.R
import com.example.animaldetectiveapp.databinding.FragmentUploadBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

class UploadFragment : Fragment() {

    private var _binding: FragmentUploadBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Permission request launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val uploadViewModel = ViewModelProvider(this).get(UploadViewModel::class.java)
        _binding = FragmentUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.captureButton.setOnClickListener {
            takePhoto()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("UploadFragment", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            getOutputDirectory(),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("UploadFragment", "Photo capture failed: ${exc.message}", exc)
                    Toast.makeText(requireContext(), "Photo capture failed", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    Log.d("UploadFragment", "Photo capture succeeded: $savedUri")
                    Toast.makeText(requireContext(), "Photo captured!", Toast.LENGTH_SHORT).show()

                    // Show the popup dialog with the captured image and analysis status
                    showImageAnalysisDialog(photoFile)
                }
            }
        )
    }

    private fun showImageAnalysisDialog(photoFile: File) {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_analysis, null)
        val dialogImageView = dialogView.findViewById<ImageView>(R.id.dialogImageView)
        val dialogStatusTextView = dialogView.findViewById<TextView>(R.id.dialogStatusTextView)
        val dialogCloseButton = dialogView.findViewById<Button>(R.id.dialogCloseButton)

        // Load the captured image
        Glide.with(this).load(photoFile).into(dialogImageView)
        dialogStatusTextView.text = "Analyzing..."

        // Create and show the dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogCloseButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        // Launch a coroutine to call Gemini API and update the dialog with the result
        viewLifecycleOwner.lifecycleScope.launch {
            val animalName = sendToGeminiWithResult(photoFile)
            // Optionally update your animals.json as well
            updateAnimalsJson(photoFile.name, animalName)
            dialogStatusTextView.text = "Animal: $animalName"
        }
    }

    // New suspend function that returns the animal name detected by Gemini API
    private suspend fun sendToGeminiWithResult(photoFile: File): String {
        return try {
            val imageBytes = withContext(Dispatchers.IO) { photoFile.readBytes() }
            // Convert byte array to Bitmap if needed
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            // Create the GenerativeModel instance (using your Gemini API key)
            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-pro-latest",
                apiKey = getString(R.string.gemini_api_key)
            )

            // Build the input content using the DSL
            val inputContent = content {
                image(bitmap)
                text("What animal is this? Answer with only the name of the animal.")
            }

            // Call the Gemini API
            val response = generativeModel.generateContent(inputContent)
            // Return the detected animal name (or "Unknown" if response is empty)
            response.text?.trim() ?: "Unknown"
        } catch (e: Exception) {
            Log.e("UploadFragment", "Error sending to Gemini", e)
            "Error"
        }
    }

    private fun updateAnimalsJson(imageName: String, animalName: String) {
        val animalsFile = File(getOutputDirectory(), "animals.json")
        val animalsList = try {
            if (animalsFile.exists()) {
                val json = animalsFile.readText()
                Gson().fromJson(json, Array<AnimalEntry>::class.java).toMutableList()
            } else {
                mutableListOf()
            }
        } catch (e: Exception) {
            Log.e("UploadFragment", "Error parsing animals.json", e)
            mutableListOf()
        }
        animalsList.add(AnimalEntry(imageName, animalName))
        val newJson = Gson().toJson(animalsList)
        animalsFile.writeText(newJson)
    }

    private fun getOutputDirectory(): File {
        val mediaDir = requireContext().externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return mediaDir ?: requireContext().filesDir
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
}

data class AnimalEntry(val image: String, val animal: String)
