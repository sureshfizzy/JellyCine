package dev.cinestream.jellycine.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import dev.cinestream.jellycine.R
import dev.cinestream.jellycine.database.Server
import dev.cinestream.jellycine.viewmodels.ServerSelectViewModel
import java.lang.IllegalStateException

class DeleteServerDialogFragment(private val viewModel: ServerSelectViewModel, val server: Server) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle(getString(R.string.remove_server))
                .setMessage(getString(R.string.remove_server_dialog_text, server.name))
                .setPositiveButton(getString(R.string.remove)) { _, _ ->
                    viewModel.deleteServer(server)
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ ->

                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}