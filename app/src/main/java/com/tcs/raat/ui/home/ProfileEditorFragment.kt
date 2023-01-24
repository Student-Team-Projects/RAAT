/*
 * Copyright (c) 2023  Justyna Jaworska.
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.tcs.raat.ui.home

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.SimpleAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.tcs.raat.R
import com.tcs.raat.databinding.FragmentProfileEditorAdvancedBinding
import com.tcs.raat.databinding.FragmentProfileEditorBinding
import com.tcs.raat.model.ServerProfile
import com.tcs.raat.model.ServerProfile.Companion.CHANNEL_SSH_TUNNEL
import com.tcs.raat.model.ServerProfile.Companion.CHANNEL_TCP
import com.tcs.raat.model.ServerProfile.Companion.SSH_AUTH_KEY
import com.tcs.raat.model.ServerProfile.Companion.SSH_AUTH_PASSWORD
import com.tcs.raat.util.MsgDialog
import com.tcs.raat.util.OpenableDocument
import com.tcs.raat.viewmodel.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.trilead.ssh2.crypto.PEMDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Server Profile Editor. It can be opened in two modes:
 *
 * Normal
 * ======
 * We show a Dialog with some commonly used options. Use [show] to open this.
 *
 * Advanced
 * ========
 * We show a fullscreen fragment attached to content root.
 * All available options are shown in this mode. Use [showAdvanced] to open this.
 *
 * TODO: Cleanup this complex mess
 */
class ProfileEditorFragment : DialogFragment() {
    private lateinit var binding : FragmentProfileEditorBinding

    private val viewModel by activityViewModels<HomeViewModel>()
    private var profile = ServerProfile()

    private fun loadProfile(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        profile = savedInstanceState?.getParcelable("EditorProfile")
                  ?: viewModel.editProfileEvent.value
                  ?: profile
    }

    private fun saveProfile() {
        if (profile.ID == 0L)
            viewModel.insertProfile(profile)
        else
            viewModel.updateProfile(profile)
    }

    private fun getTitle(): Int {
        return if (profile.ID == 0L) R.string.title_add_server_profile
        else R.string.title_edit_server_profile
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("EditorProfile", profile)
    }

    /**************************************************************************
     * Normal Mode (Dialog)
     **************************************************************************/

    fun show(manager: FragmentManager) {
        show(manager, "ProfileEditor")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentProfileEditorBinding.inflate(layoutInflater, null, false)
        val p = profile

        // binding.lifecycleOwner is not set for Dialog mode because we don't
        // have access to viewLifecycleOwner. We could use `this` but our layout
        // is simple/static enough that we don't really need to set binding.lifecycleOwner.

        loadProfile(savedInstanceState)
        binding.profile = profile
        isCancelable = false

        binding.apply {
            keyImportBtn.setOnClickListener { keyFilePicker.launch(arrayOf("*/*")) }
            useSshTunnel.isChecked = (p.channelType == CHANNEL_SSH_TUNNEL)
            sshAuthTypePassword.isChecked = (p.sshAuthType == SSH_AUTH_PASSWORD)
            sshAuthTypeKey.isChecked = (p.sshAuthType == SSH_AUTH_KEY)

            isPrivateKeyEncrypted = isKeyEncrypted(p.sshPrivateKey)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog_Dimmed)
                .setTitle(getTitle())
                .setView(binding.root)
                .setPositiveButton(R.string.title_save) { _, _ -> /* See below */ }
                .setNegativeButton(R.string.title_cancel) { _, _ -> dismiss() }
                .setNeutralButton(R.string.title_advanced) { _, _ -> showAdvanced(parentFragmentManager) }
                .setBackgroundInsetTop(0)
                .setBackgroundInsetBottom(0)
                .create()

        fun validatePrivateKey(): Boolean {
            if (binding.sshAuthTypeKey.isChecked) {
                if (profile.sshPrivateKey.isEmpty()) {
                    binding.keyImportBtn.error = "Required"
                    return false
                }

                if (binding.isPrivateKeyEncrypted && binding.sshKeyPassword.length() == 0) {
                    binding.sshKeyPassword.error = "Password is required for encrypted Private Key"
                    return false
                }
            }
            return true
        }

        fun validate(): Boolean {
            var result = validateNotEmpty(binding.host) and validateNotEmpty(binding.port)
            if (binding.useSshTunnel.isChecked) {
                result = result and
                        validateNotEmpty(binding.sshHost) and
                        validateNotEmpty(binding.sshUsername) and
                        validateNotEmpty(binding.sshPassword, binding.sshAuthTypePassword.isChecked) and
                        validatePrivateKey()
            }
            return result
        }

        // Customize Save button directly to avoid Dialog dismissal if validation fails
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {

                if (validate()) {
                    profile.apply {
                        channelType = if (binding.useSshTunnel.isChecked) CHANNEL_SSH_TUNNEL else CHANNEL_TCP
                        sshAuthType = if (binding.sshAuthTypeKey.isChecked) SSH_AUTH_KEY else SSH_AUTH_PASSWORD
                    }
                    saveProfile()
                    dismiss()
                }
            }
        }

        return dialog
    }

    /**************************************************************************
     * Private Key
     **************************************************************************/
    private val keyFilePicker = registerForActivityResult(OpenableDocument()) { importPrivateKey(it) }

    private fun importPrivateKey(uri: Uri?) {
        if (uri == null)
            return

        lifecycleScope.launch(Dispatchers.IO) {

            val text = requireContext().contentResolver.openInputStream(uri).use { it?.reader()?.readText() ?: "" }
            val pem = runCatching { PEMDecoder.parsePEM(text.toCharArray()) }
            val encrypted = runCatching { PEMDecoder.isPEMEncrypted(pem.getOrNull()) }.getOrNull() ?: false

            lifecycleScope.launchWhenCreated {
                if (pem.isSuccess) {
                    profile.sshPrivateKey = text
                    binding.keyImportBtn.setText(R.string.title_change)
                    binding.keyImportBtn.error = null
                    binding.isPrivateKeyEncrypted = encrypted
                    Snackbar.make(requireView(), R.string.msg_imported, Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), R.string.msg_invalid_key_file, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isKeyEncrypted(key: String): Boolean {
        return runCatching {
            PEMDecoder.isPEMEncrypted(PEMDecoder.parsePEM(key.toCharArray()))
        }.getOrNull() ?: false
    }


    /**************************************************************************
     * Advanced mode
     *
     * For the most part, we use two-way data binding to update [profile] but
     * some fields (e.g. private key) require manual UI handling.
     **************************************************************************/

    private lateinit var bindingAdvanced: FragmentProfileEditorAdvancedBinding

    /**
     * Shows Profile Editor in advanced mode
     */
    fun showAdvanced(manager: FragmentManager) {
        manager.beginTransaction()
                .replace(android.R.id.content, ProfileEditorFragment(), "ProfileEditorAdvanced")
                .addToBackStack(null)
                .commit()
    }

    /**
     * Creates View for advanced mode.
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (showsDialog)
            return null

        loadProfile(savedInstanceState)

        bindingAdvanced = FragmentProfileEditorAdvancedBinding.inflate(inflater, container, false)

        val p = profile
        bindingAdvanced.apply {
            lifecycleOwner = viewLifecycleOwner
            profile = p
            toolbar.title = getString(getTitle())
            saveBtn.setOnClickListener { saveAdvanced() }
            toolbar.setNavigationOnClickListener { dismiss() }
            keyCompatModeHelpBtn.setOnClickListener {
                MsgDialog.show(parentFragmentManager,
                               getString(R.string.title_key_compat_mode),
                               getString(R.string.msg_key_compat_mode_help))
            }

            // Setup initial values of some CheckBox views.
            // We can't use Data Binding to initialize these because it breaks
            // the inter-dependency among views.
            useRepeater.isChecked = p.useRepeater

            // TODO Move it to proper place
            val securityTypes = mapOf(
                    getString(R.string.title_automatic) to 0,
                    getString(R.string.title_none) to 1,
                    "VncAuth" to 2,
                    "AnonTLS" to 18,
                    "VeNCrypt" to 19
            )

            security.setEntries(securityTypes, p.securityType) { p.securityType = it }

            //Setup Gesture Style
            val gestureStyleItems = listOf(
                    mapOf("name" to getString(R.string.pref_gesture_style_auto),
                          "description" to getString(R.string.pref_gesture_style_auto_summary),
                          "value" to "auto"),
                    mapOf("name" to getString(R.string.pref_gesture_style_touchscreen),
                          "description" to getString(R.string.pref_gesture_style_touchscreen_summary),
                          "value" to "touchscreen"),
                    mapOf("name" to getString(R.string.pref_gesture_style_touchpad),
                          "description" to getString(R.string.pref_gesture_style_touchpad_summary),
                          "value" to "touchpad"),
            )

            val adapter = SimpleAdapter(requireContext(), gestureStyleItems, android.R.layout.simple_list_item_1,
                                        arrayOf("name", "description"), intArrayOf(android.R.id.text1, android.R.id.text2))

            adapter.setDropDownViewResource(android.R.layout.simple_list_item_2)
            gestureStyle.adapter = adapter
            gestureStyle.setSelection(gestureStyleItems.indexOfFirst { it["value"] == p.gestureStyle })
            gestureStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    p.gestureStyle = gestureStyleItems[position]["value"]!!
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        return bindingAdvanced.root
    }

    private fun saveAdvanced() {
        if (!validateAdvanced())
            return

        profile.apply {
            useRepeater = bindingAdvanced.useRepeater.isChecked
            idOnRepeater = bindingAdvanced.idOnRepeater.text.toString().toIntOrNull() ?: 0
        }

        saveProfile()
        dismiss()
    }

    /**************************************************************************
     * Validation
     **************************************************************************/

    private fun validateAdvanced(): Boolean {
        return validateNotEmpty(bindingAdvanced.host) and
                validateNotEmpty(bindingAdvanced.port) and
                validateNotEmpty(bindingAdvanced.idOnRepeater, bindingAdvanced.useRepeater.isChecked)
    }

    /**
     * If [preCondition] is `true`, validates that [target] is not empty.
     */
    private fun validateNotEmpty(target: EditText, preCondition: Boolean = true, msg: String = "Required"): Boolean {
        if (preCondition && target.length() == 0) {
            target.error = msg
            return false
        }
        return true
    }


}
