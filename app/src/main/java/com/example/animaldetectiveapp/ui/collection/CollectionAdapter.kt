package com.example.animaldetectiveapp.ui.collection

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.animaldetectiveapp.R
import java.io.File

class CollectionAdapter(
    private val imageFiles: List<File>,
    private val onImageClick: (File) -> Unit // Lambda function for click handling
) : RecyclerView.Adapter<CollectionAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageViewItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_collection, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val file = imageFiles[position]
        Glide.with(holder.itemView.context)
            .load(file)
            .centerCrop()
            .into(holder.imageView)

        // Handle image click
        holder.itemView.setOnClickListener { onImageClick(file) }
    }

    override fun getItemCount(): Int = imageFiles.size
}
