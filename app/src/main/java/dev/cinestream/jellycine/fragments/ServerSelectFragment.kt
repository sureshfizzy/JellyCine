package dev.cinestream.jellycine.fragments

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
import android.widget.Toast
import dev.cinestream.jellycine.Adapters.ServerGridAdapter
import dev.cinestream.jellycine.viewmodels.ServerSelectViewModel
import dev.cinestream.jellycine.viewmodels.ServerSelectViewModelFactory
import dev.cinestream.jellycine.dialogs.DeleteServerDialogFragment


class ServerSelectFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentServerSelectBinding.inflate(inflater)

        val application = requireNotNull(this.activity).application

        val dataSource = ServerDatabase.getInstance(application).serverDatabaseDao

        val viewModelFactory = ServerSelectViewModelFactory(dataSource, application)
        val viewModel = ViewModelProvider(this, viewModelFactory).get(ServerSelectViewModel::class.java)

        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        binding.serversRecyclerView.adapter = ServerGridAdapter(ServerGridAdapter.OnClickListener { server ->
            Toast.makeText(application, "You selected server $server", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_serverSelectFragment_to_mainActivity)
        }, ServerGridAdapter.OnLongClickListener { server ->
            DeleteServerDialogFragment(viewModel, server).show(parentFragmentManager, "deleteServer")
            true
        })

        binding.buttonAddServer.setOnClickListener {
            this.findNavController().navigate(R.id.action_serverSelectFragment_to_addServerFragment)
        }
        return binding.root
    }
}