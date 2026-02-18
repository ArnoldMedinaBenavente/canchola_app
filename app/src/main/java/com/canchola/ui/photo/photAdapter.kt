package com.canchola.ui.photo

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.canchola.R

// Asegúrate de importar tu R correctamente según el paquete de tu app

class PhotoAdapter(
    private val photoList: MutableList<Uri>, // Lista de URIs de las fotos
    private val onDeleteClick: (Int) -> Unit // Función lambda para manejar el borrado
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

        // USAR GLIDE EN LUGAR DE setImageURI
        com.bumptech.glide.Glide.with(holder.itemView.context)
            .load(imageUri)
            .centerCrop() // Esto hace que se vea bien en el cuadro sin deformarse
            .placeholder(android.R.drawable.ic_menu_report_image) // Icono temporal
            .error(android.R.drawable.stat_notify_error) // Icono si falla
            .into(holder.imgPhoto)

        // Configurar el botón de eliminar
        holder.btnRemove.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount(): Int = photoList.size
}