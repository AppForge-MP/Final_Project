package com.example.welly

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⭐ 1. Apply theme early in the lifecycle using the saved preference
        val themePref = PreferenceManager.getDefaultSharedPreferences(this).getString("theme_preference", "dark")
        applyCustomTheme(this, themePref ?: "dark")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity) // Assuming this is your layout file name

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment()) // Assuming 'settings' is the FrameLayout ID
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    // Handle the Up button (back arrow) click
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Utility function to calculate and set the theme ID (must match themes.xml names)
    private fun applyCustomTheme(context: Context, themeValue: String) {
        val themeResId = when (themeValue) {
            // Note: Light theme has been removed based on previous context
            "pink" -> R.style.Theme_Welly_Pink
            "purple" -> R.style.Theme_Welly_Purple
            "dark" -> R.style.Theme_Welly_Dark
            else -> R.style.Theme_Welly_Dark
        }
        context.setTheme(themeResId)
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey) // Your preference definitions
        }

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        // ⭐ 2. Listener to handle theme selection and set the change flag
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "theme_preference") {
                val themeValue = sharedPreferences?.getString(key, "dark")

                // Set the Night Mode: Since we only have dark/colored themes, we force Night Mode.
                when (themeValue) {
                    "dark", "pink", "purple" -> {
                        // Forces the app into Dark Mode regardless of system setting
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }

                // Set the flag to TRUE so MainActivity knows it needs to reload
                sharedPreferences?.edit()?.putBoolean("theme_changed_flag", true)?.apply()

                // Force SettingsActivity to reload to show the theme change instantly
                activity?.recreate()
            }
        }
    }
}