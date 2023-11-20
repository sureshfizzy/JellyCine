package dev.cinestream.jellycine.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dev.cinestream.jellycine.Adapters.CollectionListAdapter
import dev.cinestream.jellycine.databinding.FragmentMediaBinding
import dev.cinestream.jellycine.viewmodels.MediaViewModel
import dev.cinestream.jellycine.viewmodels.MediaViewModelFactory



class MediaFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val application = requireNotNull(this.activity).application
        val binding = FragmentMediaBinding.inflate(inflater, container, false)
        val viewModelFactory = MediaViewModelFactory(application)
        val viewModel = ViewModelProvider(this, viewModelFactory).get(MediaViewModel::class.java)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.viewsRecyclerView.adapter = CollectionListAdapter()

        viewModel.finishedLoading.observe(viewLifecycleOwner, {
            if (it) {
                binding.loadingIncicator.visibility = View.GONE
            }
        })

        return binding.root
    }
}