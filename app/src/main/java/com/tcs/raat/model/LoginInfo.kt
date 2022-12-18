/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.tcs.raat.model

/**
 * Simple model used by login auto-completion.
 */
data class LoginInfo(
        val name: String, //Profile name
        val host: String,
        val username: String,
        val password: String,
)