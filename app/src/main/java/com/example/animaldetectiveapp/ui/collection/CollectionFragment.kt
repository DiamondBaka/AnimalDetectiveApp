package com.example.animaldetectiveapp.ui.collection

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.animaldetectiveapp.R
import com.example.animaldetectiveapp.databinding.FragmentCollectionBinding
import java.io.File

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

        // Setup RecyclerView with a GridLayoutManager with 3 columns
        binding.collectionRecyclerView.layoutManager = GridLayoutManager(context, 3)

        // Get the directory where photos are saved (reuse similar logic as in UploadFragment)
        val imageDir = getOutputDirectory()

        // Retrieve image files and sort them by newest first
        val imageFiles = imageDir.listFiles { file ->
            file.extension.lowercase() in listOf("jpg", "jpeg", "png")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()


        Log.d("CollectionFragment", "Found ${imageFiles.size} images")

        // Set the adapter
        binding.collectionRecyclerView.adapter = CollectionAdapter(imageFiles)
    }

    // Helper method to get the output directory (ensure this is the same as where you save images)
    private fun getOutputDirectory(): File {
        val mediaDir = activity?.externalMediaDirs?.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else activity?.filesDir ?: File("")
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
