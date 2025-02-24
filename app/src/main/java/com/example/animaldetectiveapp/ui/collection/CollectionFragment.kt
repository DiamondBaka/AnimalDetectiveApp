package com.example.animaldetectiveapp.ui.collection

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.animaldetectiveapp.R
import com.example.animaldetectiveapp.databinding.FragmentCollectionBinding
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

// Define AnimalEntry data class to match the structure of animals.json
data class AnimalEntry(val image: String, val animal: String, val description: String, val longitude: Double, val latitude: Double) {

}

class CollectionFragment : Fragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView with a GridLayoutManager (3 columns)
        binding.collectionRecyclerView.layoutManager = GridLayoutManager(context, 3)

        // Get stored images
        val imageDir = getOutputDirectory()
        val imageFiles = imageDir.listFiles { file ->
            file.extension.lowercase() in listOf("jpg", "jpeg", "png")
        }?.toList() ?: emptyList()

        // Set adapter with click listener
        binding.collectionRecyclerView.adapter = CollectionAdapter(imageFiles) { file ->
            showExpandedImage(file)
        }

        // Close button action
        binding.closeButton.setOnClickListener {
            binding.expandedImageContainer.visibility = View.GONE
        }
    }

    private fun showExpandedImage(file: File) {
        binding.expandedImageContainer.visibility = View.VISIBLE

        Glide.with(this)
            .load(file)
            .into(binding.expandedImageView)

        val formattedDate = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
            .format(file.lastModified())

        // Read animal info (including description) from animals.json
        val animalsFile = File(getOutputDirectory(), "animals.json")
        var animalName = "Unknown"
        var description = ""
        if (animalsFile.exists()) {
            val json = animalsFile.readText()
            val animalsList = Gson().fromJson(json, Array<AnimalEntry>::class.java)
            val entry = animalsList.find { it.image == file.name }
            if (entry != null) {
                animalName = entry.animal
                description = entry.description
            }
        }

        // Set new separate TextViews instead of a single one
        binding.dateInfoTextView.text = "Date: $formattedDate"
        binding.animalNameTextView.text = "Animal: $animalName"
        binding.animalDescriptionTextView.text = "Description: $description"

        // Close button action
        binding.closeButton.setOnClickListener {
            binding.expandedImageContainer.visibility = View.GONE
        }

        // Delete button action remains unchanged
        binding.deleteButton.setOnClickListener {
            if (file.delete()) {
                Toast.makeText(requireContext(), "Image deleted", Toast.LENGTH_SHORT).show()
                removeFromAnimalsJson(file.name)
                refreshImageList()
                binding.expandedImageContainer.visibility = View.GONE
            } else {
                Toast.makeText(requireContext(), "Failed to delete image", Toast.LENGTH_SHORT).show()
            }
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

    private fun removeFromAnimalsJson(imageName: String) {
        val animalsFile = File(getOutputDirectory(), "animals.json")
        if (animalsFile.exists()) {
            try {
                val json = animalsFile.readText()
                val animalsList = Gson().fromJson(json, Array<AnimalEntry>::class.java).toMutableList()
                // Remove the entry with a matching image name
                animalsList.removeAll { it.image == imageName }
                val newJson = Gson().toJson(animalsList)
                animalsFile.writeText(newJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun refreshImageList() {
        val imageDir = getOutputDirectory()
        val imageFiles = imageDir.listFiles { file ->
            file.extension.lowercase() in listOf("jpg", "jpeg", "png")
        }?.toList() ?: emptyList()

        binding.collectionRecyclerView.adapter = CollectionAdapter(imageFiles) { file ->
            showExpandedImage(file)
        }
    }


}