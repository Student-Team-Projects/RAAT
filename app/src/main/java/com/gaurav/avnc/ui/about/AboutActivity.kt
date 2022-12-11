/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gaurav.avnc.R

/**
 * Host activity for app details.
 */
class AboutActivity : AppCompatActivity() {

    companion object {
        const val GIT_REPO_URL = "https://github.com/gujjwal00/avnc"
        const val BUG_REPORT_URL = "https://github.com/gujjwal00/avnc/issues/new"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_about)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, AboutFragment())
                    .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}