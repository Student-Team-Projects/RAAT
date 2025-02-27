/*
 * Copyright (c) 2025  Viktar Dubovik.
 * Copyright (c) 2025  Bogdan Tolstik.
 * Copyright (c) 2025  Daniil Zabauski.
 * Copyright (c) 2025  Dzmitry Maslionchanka.
 * Copyright (c) 2023  Hubert Zięba.
 * Copyright (c) 2023  Justyna Jaworska.
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.tcs.raat.ui.home

import android.util.Log
import android.view.*
import androidx.core.view.forEach
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.tcs.raat.R
import com.tcs.raat.databinding.ServerDiscoveryBinding
import com.tcs.raat.databinding.ServerDiscoveryItemBinding
import com.tcs.raat.databinding.ServerSavedBinding
import com.tcs.raat.databinding.ServerSavedItemBinding
import com.tcs.raat.model.ServerProfile
import com.tcs.raat.ui.home.ServerTabs.PagerAdapter.ViewHolder
import com.tcs.raat.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun getSessionStatus(profile: ServerProfile): Boolean {
    profile.isSessionAlive = false
    runBlocking {
        withContext(Dispatchers.IO) {
            try {
                val jsch = JSch()
                val session = jsch.getSession(profile.sshUsername, profile.sshHost, profile.sshPort)

                session.setPassword(profile.sshPassword)
                session.setConfig("StrictHostKeyChecking", "no")
                session.timeout = 10000

                session.connect()

                val channel = session.openChannel("exec") as ChannelExec
                val port = if (profile.port <= 5900) profile.port + 5900 else profile.port

                // For getting status:
                channel.setCommand("raat-server-request get-session-status --rfb_port=$port")

                // Capture the output
                val inputStream = channel.inputStream
                channel.connect()

                // Read server response
                val output = inputStream.bufferedReader().readText()
                if (output.contains("Session alive.")) {
                    profile.isSessionAlive = true
                    Log.d("getSessionStatus", "Alive")

                } else {
                    profile.isSessionAlive = false
                    Log.d("getSessionStatus", "Dead")
                }
                Log.d(
                    "getSessionStatus",
                    "Server response: $output for ${profile.sshUsername} ${profile.sshHost}, ${profile.sshPort}"
                )

                channel.disconnect()
                session.disconnect()
            } catch (e: Exception) {
                Log.e(
                    "Get session status err",
                    "Error getting session status for ${profile.sshUsername} ${profile.sshHost}, ${profile.sshPort}",
                    e
                )
            }
        }
    }
    return  profile.isSessionAlive
}



/**
 * This class creates and manages tabs in [HomeActivity].
 * Tabs:
 *        1. Saved servers
 *        2. Discovered servers
 */
class ServerTabs(val activity: HomeActivity) {

    lateinit var savedServersTab: TabLayout.Tab
    lateinit var discoveredServersTab: TabLayout.Tab

    /**
     * Creates and initializes tabs
     *
     * [tabLayout] Hosts tabs
     * [pager] Hosts actual content views
     */
    fun create(tabLayout: TabLayout, pager: ViewPager2) {
        pager.adapter = PagerAdapter()
        pager.offscreenPageLimit = 1  // Tell pager to initialize & keep both tabs in memory

        TabLayoutMediator(tabLayout, pager) { _, _ -> }
                .attach()

        savedServersTab = tabLayout.getTabAt(0)!!
        savedServersTab.setIcon(R.drawable.ic_computer)
        savedServersTab.setContentDescription(R.string.desc_saved_servers_tab)

        discoveredServersTab = tabLayout.getTabAt(1)!!
        discoveredServersTab.setIcon(R.drawable.ic_search)
        discoveredServersTab.setContentDescription(R.string.desc_discovered_servers_tab)

        //ViewPager2 uses a RecyclerView internally, and sets it's DescendantFocusability
        //to 'FOCUS_BEFORE_DESCENDANTS', effectively breaking navigation via D-pad or arrow keys.
        pager.forEach { (it as ViewGroup).descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS }
    }

    fun showSavedServers() {
        savedServersTab.select()
    }

    fun updateDiscoveryBadge(count: Int) {
        //Currently, we are not showing the actual count in the badge.
        //But maybe we could implement a preference???
        discoveredServersTab.getOrCreateBadge().isVisible = (count != 0)
    }


    /**
     * Adapter for our pager.
     *
     * We have fixed number of static views so our implementation is really simple.
     * We override [getItemViewType] to use the given position itself as view type.
     * Then, in [onCreateViewHolder], that view type (i.e. position) is used to
     * generate corresponding view.
     *
     * As a result, nothing else needs to be done by [ViewHolder] & [bindViewHolder].
     */
    private inner class PagerAdapter : RecyclerView.Adapter<PagerAdapter.ViewHolder>() {

        private inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v)

        override fun getItemCount() = 2
        override fun getItemViewType(position: Int) = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return when (viewType) {
                0 -> ViewHolder(createSavedServersView(parent))
                1 -> ViewHolder(createDiscoveredServersView(parent))
                else -> throw IllegalStateException("Unexpected view type: [$viewType]")
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {}
    }


    /**********************************************************************************************
     * Saved servers
     **********************************************************************************************/

    private fun createSavedServersView(parent: ViewGroup): View {
        val binding = ServerSavedBinding.inflate(activity.layoutInflater, parent, false)
        binding.lifecycleOwner = activity
        binding.viewModel = activity.viewModel

        val adapter = SavedServerAdapter(activity.viewModel)
        binding.serversRv.layoutManager = LinearLayoutManager(activity)
        binding.serversRv.adapter = adapter
        binding.serversRv.setHasFixedSize(true)

        activity.viewModel.serverProfiles.observe(activity) { profiles ->
            profiles.forEach { profile ->
                Log.d("SavedServers2", "Profile added: $profile")
                getSessionStatus(profile)
                Log.d("SavedServers23", "${profile.isSessionAlive}")
            }
            adapter.submitList(profiles)
        }
        return binding.root
    }


    /**
     * Adapter for saved servers
     */
    class SavedServerAdapter(val viewModel: HomeViewModel)
        : ListAdapter<ServerProfile, SavedServerAdapter.ViewHolder>(Differ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ServerSavedItemBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val profile = getItem(position)
            holder.profile = profile
            holder.binding.viewModel = profile
            holder.binding.indicator.setup(profile, viewModel.rediscoveredProfiles)
        }

        inner class ViewHolder(val binding: ServerSavedItemBinding)
            : ProfileViewHolder(viewModel, binding.root, R.menu.saved_server)

        object Differ : DiffUtil.ItemCallback<ServerProfile>() {
            override fun areItemsTheSame(old: ServerProfile, new: ServerProfile) = (old.ID == new.ID)
            override fun areContentsTheSame(old: ServerProfile, new: ServerProfile) = (old == new)
        }

        // Add this method to refresh the data
        fun updateData(newProfiles: List<ServerProfile>) {
            submitList(newProfiles)
        }
    }


    /**********************************************************************************************
     * Discovered servers
     **********************************************************************************************/

    private fun createDiscoveredServersView(parent: ViewGroup): View {
        val binding = ServerDiscoveryBinding.inflate(activity.layoutInflater, parent, false)
        binding.lifecycleOwner = activity
        binding.viewModel = activity.viewModel

        val adapter = DiscoveredServerAdapter(activity.viewModel)
        binding.discoveredRv.layoutManager = LinearLayoutManager(activity)
        binding.discoveredRv.adapter = adapter
        binding.discoveredRv.setHasFixedSize(true)

        activity.viewModel.serverProfiles.observe(activity) { profiles ->
            profiles.forEach { profile ->
                profile.isSessionAlive = getSessionStatus(profile)
            }
            adapter.submitList(profiles)
        }

        return binding.root
    }

    class DiscoveredServerAdapter(val viewModel: HomeViewModel)
        : ListAdapter<ServerProfile, DiscoveredServerAdapter.ViewHolder>(Differ) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ServerDiscoveryItemBinding.inflate(inflater, parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val profile = getItem(position)
            holder.profile = profile
            holder.binding.viewModel = profile
        }

        inner class ViewHolder(val binding: ServerDiscoveryItemBinding) :
                ProfileViewHolder(viewModel, binding.root, R.menu.discovered_server) {

            init {
                binding.saveBtn.setOnClickListener { viewModel.onNewProfile(profile) }
            }
        }

        /**
         * Profiles generated by discovery don't have unique IDs, so we compare the whole profile.
         */
        object Differ : DiffUtil.ItemCallback<ServerProfile>() {
            override fun areItemsTheSame(old: ServerProfile, new: ServerProfile) = (old == new)
            override fun areContentsTheSame(old: ServerProfile, new: ServerProfile) = (old == new)
        }
    }


    /**
     * Base ViewHolder for [ServerProfile], used by both adapters.
     */
    open class ProfileViewHolder(
            private val homeViewModel: HomeViewModel,
            private val rootView: View,
            private val contextMenuId: Int)
        : RecyclerView.ViewHolder(rootView) {

        /**
         * Points to the profile being rendered by this view holder.
         * Updated by adapters during onBindViewHolder().
         */
        var profile = ServerProfile()

        init {
            // StartConnection on click
            rootView.setOnClickListener { homeViewModel.startConnection(profile) }

            rootView.setOnCreateContextMenuListener { contextMenu, view, _ ->
                MenuInflater(view.context).inflate(contextMenuId, contextMenu)
                contextMenu.forEach { item ->
                    item.setOnMenuItemClickListener { onContextMenuItemClick(it) }
                }
            }
        }

        private fun onContextMenuItemClick(item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.edit -> homeViewModel.onEditProfile(profile)
                R.id.duplicate -> homeViewModel.onDuplicateProfile(profile)
                R.id.close_session -> homeViewModel.onCloseSessionProfile(profile)
                R.id.delete -> homeViewModel.deleteProfile(profile)
                R.id.copy_host -> copyToClipboard(profile.host)
                R.id.copy_name -> copyToClipboard(profile.name)
            }
            return true
        }

        private fun copyToClipboard(text: String) {
            homeViewModel.setClipboardText(text)
            Snackbar.make(rootView, R.string.msg_copied_to_clipboard, Snackbar.LENGTH_SHORT).show()
        }
    }
}
