/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.ActivityHomeBinding
import com.gaurav.avnc.model.Bookmark
import com.gaurav.avnc.model.VncProfile
import com.gaurav.avnc.ui.prefs.PrefsActivity
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.viewmodel.HomeViewModel
import com.google.android.material.snackbar.Snackbar

/**
 * Primary activity of the app.
 *
 * It Provides access to bookmarks, recently connections and automatically
 * discovered servers. Most of these tasks are delegated to separate fragments.
 */
class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val viewModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //View Inflation
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home)
        binding.lifecycleOwner = this

        //Navigation
        val navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        NavigationUI.setupWithNavController(binding.navView, navController)

        //Observers
        viewModel.bookmarkEditEvent.observe(this) { showBookmarkEditor() }
        viewModel.bookmarkDeletedEvent.observe(this) { showBookmarkDeletedMsg(it) }
        viewModel.newConnectionEvent.observe(this) { startVncActivity(it) }
        viewModel.discovery.servers.observe(this, Observer { updateDiscoveryBadge(it) })

        viewModel.startDiscovery()
    }

    /**
     * Inflate main menu
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /**
     * Handle MenuItem selection
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> showSettings()
            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    /**
     * Launches Settings activity
     */
    private fun showSettings() {
        startActivity(Intent(this, PrefsActivity::class.java))
    }

    /**
     * Starts Bookmark editor dialog.
     */
    private fun showBookmarkEditor() {
        BookmarkEditorFragment().show(supportFragmentManager, "BookmarkEditor")
    }

    /**
     * Shows delete confirmation snackbar.
     */
    private fun showBookmarkDeletedMsg(bookmark: Bookmark) {
        Snackbar.make(binding.root, getString(R.string.msg_bookmark_deleted), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.title_undo)) { viewModel.insertBookmark(bookmark) }
                .show()
    }

    /**
     * Starts VNC Activity with given bookmark.
     */
    private fun startVncActivity(bookmark: Bookmark) {
        val intent = Intent(this, VncActivity::class.java)
        intent.putExtra(VncActivity.KEY.BOOKMARK, bookmark)
        startActivity(intent)
    }

    /**
     * Show number of found servers as badge.
     */
    private fun updateDiscoveryBadge(list: List<VncProfile>) {
        if (list.isNotEmpty()) {
            binding.navView.getOrCreateBadge(R.id.nav_discovery).number = list.size
        } else {
            //Ideally we would just hide the badge but due to a bug we have to remove it.
            //https://github.com/material-components/material-components-android/issues/1247
            binding.navView.removeBadge(R.id.nav_discovery)
        }
    }
}