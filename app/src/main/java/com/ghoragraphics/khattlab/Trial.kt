package com.ghoragraphics.khattlab

import android.content.Context

/**
 * Test version: the app can be used for [DAYS] days from the day it was first installed,
 * then it closes itself and points people to the Telegram channel for the official Play Store version.
 */
object Trial {
    const val DAYS = 10
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val PREFS = "trial"
    private const val K_START = "start"
    private const val K_SEEN = "last_seen"

    private fun start(c: Context): Long {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installed = try {
            @Suppress("DEPRECATION")
            c.packageManager.getPackageInfo(c.packageName, 0).firstInstallTime
        } catch (e: Exception) { 0L }
        val saved = p.getLong(K_START, 0L)
        // the earliest known date counts, so clearing the app data doesn't restart the 10 days
        val s = listOf(installed, saved).filter { it > 0L }.minOrNull() ?: System.currentTimeMillis()
        if (saved != s) p.edit().putLong(K_START, s).apply()
        return s
    }

    /** "Now", but never earlier than the latest time the app was opened (turning the phone clock back doesn't help). */
    private fun now(c: Context): Long {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = System.currentTimeMillis()
        val seen = p.getLong(K_SEEN, 0L)
        return if (t > seen) { p.edit().putLong(K_SEEN, t).apply(); t } else seen
    }

    fun expired(c: Context): Boolean = now(c) - start(c) >= DAYS * DAY_MS

    /** Whole days still left (0 when it has ended). */
    fun daysLeft(c: Context): Int {
        val left = DAYS * DAY_MS - (now(c) - start(c))
        if (left <= 0) return 0
        return ((left + DAY_MS - 1) / DAY_MS).toInt()
    }
}
