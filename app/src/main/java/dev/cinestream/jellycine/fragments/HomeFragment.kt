package dev.cinestream.jellycine.fragments

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.cinestream.jellycine.Adapters.ViewListAdapter
import dev.cinestream.jellycine.databinding.FragmentHomeBinding
import dev.cinestream.jellycine.viewmodels.HomeViewModel
import dev.cinestream.jellycine.viewmodels.HomeViewModelFactory

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val application = requireNotNull(this.activity).application
        val binding = FragmentHomeBinding.inflate(inflater, container, false)
        val viewModelFactory = HomeViewModelFactory(application)
        val viewModel = ViewModelProvider(this, viewModelFactory).get(HomeViewModel::class.java)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.viewsRecyclerView.adapter = ViewListAdapter()

        viewModel.finishedLoading.observe(viewLifecycleOwner, {
            if (it) {
                binding.loadingIncicator.visibility = View.GONE
            }
        })


        return binding.root
    }
}