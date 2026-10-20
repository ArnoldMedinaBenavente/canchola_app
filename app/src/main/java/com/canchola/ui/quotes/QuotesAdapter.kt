package com.canchola.ui.quotes

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.canchola.databinding.ItemQuoteBinding
import com.canchola.models.Quote

class QuotesAdapter(
    private val quotes: List<Quote>,
    private val onDownloadClick: (Quote) -> Unit,
    private val onOptionsClick: (Quote) -> Unit,
    private val onDeleteClick: (Quote) -> Unit
) : RecyclerView.Adapter<QuotesAdapter.QuoteViewHolder>() {

    class QuoteViewHolder(val binding: ItemQuoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuoteViewHolder {
        val binding = ItemQuoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuoteViewHolder, position: Int) {
        val quote = quotes[position]
        holder.binding.apply {
            // Título dinámico
            tvFolio.text = if (quote.isQuote()) "Folio: #${quote.idQuote}" else quote.getDisplayTitle()
            
            // Mostrar nombre del proyecto si existe
            if (!quote.projectName.isNullOrEmpty()) {
                tvProjectName.visibility = View.VISIBLE
                tvProjectName.text = quote.projectName
            } else {
                tvProjectName.visibility = View.GONE
            }

            // Cliente unificado
            tvCliente.text = quote.getFinalCustomer()
            tvAtteDetalle.text = quote.atte ?: ""

            // Keywords / Tags
            if (!quote.keywords.isNullOrEmpty()) {
                tvKeywords.visibility = View.VISIBLE
                tvKeywords.text = quote.keywords.replace(",", " • ")
            } else {
                tvKeywords.visibility = View.GONE
            }

            // Botones para cotizaciones locales
            if (quote.isLocal()) {
                layoutActionsLocal.visibility = View.VISIBLE
                btnEditLocal.setOnClickListener {
                    val intent = Intent(holder.itemView.context, CreateQuoteActivity::class.java)
                    intent.putExtra("QUOTE_DATA", quote)
                    holder.itemView.context.startActivity(intent)
                }
                btnDeleteLocal.setOnClickListener {
                    onDeleteClick(quote)
                }
            } else {
                layoutActionsLocal.visibility = View.GONE
            }

            // Color del Folio/Proyecto para distinguir
            val color = if (quote.isQuote()) android.graphics.Color.parseColor("#007AFF") else android.graphics.Color.parseColor("#5856D6")
            tvFolio.setTextColor(color)

            root.setOnClickListener { onOptionsClick(quote) }
        }
    }

    override fun getItemCount() = quotes.size
}
