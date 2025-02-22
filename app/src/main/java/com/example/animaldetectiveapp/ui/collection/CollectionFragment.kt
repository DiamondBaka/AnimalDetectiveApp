package com.example.animaldetectiveapp.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.example.animaldetectiveapp.R
import com.example.animaldetectiveapp.databinding.FragmentCollectionBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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

        // Display image info (file name and date)
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .format(file.lastModified())
        binding.imageInfoTextView.text = "File: ${file.name}\nDate: $formattedDate"
    }


    private fun getOutputDirectory(): File {
        val mediaDir = activity?.externalMediaDirs?.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else requireContext().filesDir
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
