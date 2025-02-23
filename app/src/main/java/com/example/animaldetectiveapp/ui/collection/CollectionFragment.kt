package com.example.animaldetectiveapp.ui.collection

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
data class AnimalEntry(val image: String, val animal: String, val longitude: Double, val latitude: Double) {

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

        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .format(file.lastModified())

        // Read animal name from animals.json
        val animalsFile = File(getOutputDirectory(), "animals.json")
        var animalName = "Unknown"
        if (animalsFile.exists()) {
            val json = animalsFile.readText()
            val animalsList = Gson().fromJson(json, Array<AnimalEntry>::class.java)
            val animalMap = animalsList.associate { it.image to it.animal }
            animalName = animalMap[file.name] ?: "Unknown"
        }

        binding.imageInfoTextView.text = "File: ${file.name}\nDate: $formattedDate\nAnimal: $animalName"
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