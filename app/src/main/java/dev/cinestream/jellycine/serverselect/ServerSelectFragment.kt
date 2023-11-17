package dev.cinestream.jellycine.serverselect

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import dev.cinestream.jellycine.R
import dev.cinestream.jellycine.database.ServerDatabase
import dev.cinestream.jellycine.databinding.FragmentServerSelectBinding


class ServerSelectFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentServerSelectBinding.inflate(inflater)

        val application = requireNotNull(this.activity).application

        val dataSource = ServerDatabase.getInstance(application).serverDatabaseDao

        val viewModelFactory = ServerSelectViewModelFactory(dataSource)
        val viewModel = ViewModelProvider(this, viewModelFactory).get(ServerSelectViewModel::class.java)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.serversRecyclerView.adapter = ServerGridAdapter(ServerGridAdapter.OnClickListener {
        })

        binding.buttonAddServer.setOnClickListener {
            this.findNavController().navigate(R.id.action_serverSelectFragment_to_addServerFragment)
        }
        return binding.root
    }
}