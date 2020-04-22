package org.ascolto.onlus.geocrowd19.android.ui.uploadData

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.bendingspoons.base.extensions.invisible
import com.bendingspoons.base.extensions.visible
import kotlinx.android.synthetic.main.insert_upload_data_code_fragment.*
import kotlinx.android.synthetic.main.where_to_find_the_code_fragment.close
import kotlinx.android.synthetic.main.where_to_find_the_code_fragment.okButton
import kotlinx.android.synthetic.main.where_to_find_the_code_fragment.title
import org.ascolto.onlus.geocrowd19.android.R
import org.ascolto.onlus.geocrowd19.android.loading
import org.koin.androidx.viewmodel.ext.android.getSharedViewModel

class InsertUploadDataCodeFragment : Fragment(R.layout.insert_upload_data_code_fragment) {

    private lateinit var viewModel: UploadDataViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = getSharedViewModel()

        okButton.isEnabled = false
        codeTextField.filters = arrayOf(InputFilter.AllCaps())

        close.setOnClickListener {
            close()
        }

        okButton.setOnClickListener {
            viewModel.exportData(codeTextField.text.toString())
        }

        info.setOnClickListener {
            val action = InsertUploadDataCodeFragmentDirections.actionGlobalInstruction()
            findNavController().navigate(action)
        }

        infoText.setOnClickListener {
            val action = InsertUploadDataCodeFragmentDirections.actionGlobalInstruction()
            findNavController().navigate(action)
        }

        codeTextField.doOnTextChanged { text, _, _, _ ->
            validateCode(text.toString())
            error.invisible()
        }

        viewModel.loading.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.let {
                activity?.loading(it)
            }
        })

        viewModel.error.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.let {
                error.visible()
            }
        })

        viewModel.success.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.let {
                val action = UploadDataSuccessFragmentDirections.actionGlobalSuccess()
                findNavController().navigate(action)
            }
        })
    }

    private fun validateCode(code: String) {
        val isCodeValid = code.length == 8
        error.visibility = if (isCodeValid) View.VISIBLE else View.GONE
        okButton.isEnabled = isCodeValid
    }

    private fun close() {
        activity?.finish()
    }
}