/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentLibrariesBinding

internal class LibrariesFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        val binding = FragmentLibrariesBinding.inflate(inflater, container, false)

        // ListView would be better for this, but I am having layout issues with it.
        var indexInParent = binding.root.indexOfChild(binding.librariesDivider) + 1
        for (library in libraries) {
            val textView = inflater.inflate(android.R.layout.simple_list_item_1, binding.root, false) as TextView

            textView.text = library.name
            textView.setOnClickListener { openUrl(library.homepage) }

            //Apply ripple background
            with(TypedValue()) {
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
                textView.setBackgroundResource(resourceId)
            }

            binding.root.addView(textView, indexInParent)
            ++indexInParent
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.title_open_source_libraries)
    }

    private fun openUrl(url: String) {
        if (url.isNotEmpty()) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }


    private data class Library(
            val name: String,
            val homepage: String
    )

    private val libraries = listOf(
            Library("LibVNCClient",
                    "https://github.com/LibVNC/libvncserver"),

            Library("Libjpeg-turbo",
                    "https://github.com/libjpeg-turbo/libjpeg-turbo"),

            Library("LibreSSL Portable",
                    "https://github.com/libressl-portable/portable"),

            Library("ConnectBot's SSH library",
                    "https://github.com/connectbot/sshlib/"),

            Library("Android Jetpack (Androidx)",
                    "https://github.com/libjpeg-turbo/libjpeg-turbo"),

            Library("Material Components for Android",
                    "https://github.com/material-components/material-components-android"),

            Library("Material Icons",
                    "https://fonts.google.com/icons"),
    )
}
