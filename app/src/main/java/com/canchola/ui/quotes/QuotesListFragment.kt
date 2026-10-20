package com.canchola.ui.quotes

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.canchola.data.local.AppDatabase
import com.canchola.databinding.FragmentQuotesListBinding
import com.canchola.models.Quote
import kotlinx.coroutines.launch

class QuotesListFragment : Fragment() {

    private var _binding: FragmentQuotesListBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var db: AppDatabase
    private lateinit var adapter: QuotesAdapter
    
    private var filterType: String = TYPE_OPERATIONS

    companion object {
        const val ARG_FILTER_TYPE = "filter_type"
        const val TYPE_OPERATIONS = "operations"
        const val TYPE_NEW_QUOTES = "new_quotes"

        fun newInstance(filterType: String): QuotesListFragment {
            val fragment = QuotesListFragment()
            val args = Bundle()
            args.putString(ARG_FILTER_TYPE, filterType)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            filterType = it.getString(ARG_FILTER_TYPE) ?: TYPE_OPERATIONS
        }
        db = AppDatabase.getDatabase(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQuotesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        
        binding.btnCreateFromEmpty.setOnClickListener {
            val intent = Intent(requireContext(), CreateQuoteActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadQuotes()
    }

    private fun setupRecyclerView() {
        binding.rvQuotesFragment.layoutManager = LinearLayoutManager(requireContext())
    }

    fun loadQuotes() {
        if (_binding == null) return
        lifecycleScope.launch {
            // Obtenemos los datos directamente de la base de datos usando los nuevos métodos del DAO
            val filteredList = when (filterType) {
                TYPE_OPERATIONS -> db.quoteDao().getOperationQuotes()
                TYPE_NEW_QUOTES -> db.quoteDao().getAppRequests()
                else -> db.quoteDao().getAllQuotes()
            }

            if (filteredList.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.rvQuotesFragment.visibility = View.GONE
                
                // OCULTAR BOTÓN DE CREAR SI ES TAB DE OPERACIONES (Solo lectura del servidor)
                if (filterType == TYPE_OPERATIONS) {
                    binding.btnCreateFromEmpty.visibility = View.GONE
                    binding.tvEmptyState.text = "No hay operaciones registradas"
                } else {
                    binding.btnCreateFromEmpty.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "No hay solicitudes pendientes"
                }
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.rvQuotesFragment.visibility = View.VISIBLE
                
                adapter = QuotesAdapter(
                    quotes = filteredList,
                    onDownloadClick = { quote -> /* Lógica descarga */ },
                    onOptionsClick = { quote -> (activity as? com.canchola.ui.home.HomeActivity)?.showQuoteOptions(quote) },
                    onDeleteClick = { quote -> (activity as? com.canchola.ui.home.HomeActivity)?.confirmDeleteQuote(quote) }
                )
                binding.rvQuotesFragment.adapter = adapter
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
