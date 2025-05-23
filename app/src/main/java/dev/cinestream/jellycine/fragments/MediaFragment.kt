package dev.cinestream.jellycine.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import dev.cinestream.jellycine.Adapters.CollectionListAdapter
import dev.cinestream.jellycine.databinding.FragmentMediaBinding
import dev.cinestream.jellycine.viewmodels.MediaViewModel
import dev.cinestream.jellycine.viewmodels.MediaViewModelFactory



class MediaFragment : Fragment() {

    private val args: MediaFragmentArgs by navArgs()

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

        binding.favoriteButton.setOnClickListener {
            viewModel.toggleFavorite()
        }

        binding.playButton.setOnClickListener {
            Log.d("MediaFragment", "Play button clicked for item: ${viewModel.mediaItemDetails.value?.name}")
            Toast.makeText(context, "Play action for ${viewModel.mediaItemDetails.value?.name}", Toast.LENGTH_SHORT).show()
        }

        binding.viewsRecyclerView.adapter = CollectionListAdapter() // This seems to be for a different purpose, maybe for seasons/episodes list later?

        // Get itemId from arguments
        val itemId = args.itemId

        // Call ViewModel to load details
        // This method (loadMediaDetails) will be added to MediaViewModel in a subsequent task.
        viewModel.loadMediaDetails(itemId)

        viewModel.finishedLoading.observe(viewLifecycleOwner, {
            if (it) {
                binding.loadingIncicator.visibility = View.GONE
            }
        })

        return binding.root
    }
}