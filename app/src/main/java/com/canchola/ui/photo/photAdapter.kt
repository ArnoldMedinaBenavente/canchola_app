package com.canchola.ui.photo

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.canchola.R

class PhotoAdapter(
    private val photoList: MutableList<Uri>,
    private val isReadOnly: Boolean = false,
    private val onPhotoClick: ((Uri) -> Unit)? = null,
    private val onDeleteClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgPhoto: ImageView = itemView.findViewById(R.id.imgPhoto)
        val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val imageUri = photoList[position]

        com.bumptech.glide.Glide.with(holder.itemView.context)
            .load(imageUri)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_report_image)
            .error(android.R.drawable.stat_notify_error)
            .into(holder.imgPhoto)

        if (isReadOnly) {
            holder.btnRemove.visibility = View.GONE
        } else {
            holder.btnRemove.visibility = View.VISIBLE
            holder.btnRemove.setOnClickListener {
                onDeleteClick?.invoke(position)
            }
        }

        holder.imgPhoto.setOnClickListener {
            onPhotoClick?.invoke(imageUri)
        }
    }

    override fun getItemCount(): Int = photoList.size
}