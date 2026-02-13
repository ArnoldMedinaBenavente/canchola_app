import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.canchola.databinding.ItemQuoteBinding
import com.canchola.models.Quote

class QuotesAdapter(

    private val quotes: List<Quote>,
    private val onDownloadClick: (Quote) -> Unit,
    private val onOptionsClick: (Quote) -> Unit // <--- AGREGA ESTA LÍNEA
) : RecyclerView.Adapter<QuotesAdapter.QuoteViewHolder>() {

    class QuoteViewHolder(val binding: ItemQuoteBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuoteViewHolder {
        val binding = ItemQuoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuoteViewHolder, position: Int) {
        val quote = quotes[position]
        holder.binding.apply {
            tvFolio.text = "Folio: ${quote.idQuote}"
            tvCliente.text = quote.nameCustomer
            tvAtteDetalle.text = quote.atte

            // Lógica de Keywords
            if (!quote.keywords.isNullOrEmpty()) {
                tvKeywords.visibility = View.VISIBLE
                tvKeywords.text = quote.keywords.replace(",", " • ")
            } else {
                tvKeywords.visibility = View.GONE
            }

            // Acción del botón de descarga
         //   btnDownload.setOnClickListener { onDownloadClick(quote) }

            // Acción al tocar toda la tarjeta (AQUÍ YA NO DARÁ ERROR)
            root.setOnClickListener { onOptionsClick(quote) }
        }
    }

    override fun getItemCount() = quotes.size
}