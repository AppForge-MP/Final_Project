package com.example.welly

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.widget.TextView
// RESTORED Imports for Journaling
import android.widget.Button
import android.widget.EditText
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import androidx.preference.PreferenceManager
import android.content.pm.PackageManager
// END RESTORED Imports
import com.codepath.asynchttpclient.AsyncHttpClient
import com.codepath.asynchttpclient.RequestParams
import com.codepath.asynchttpclient.callback.TextHttpResponseHandler
import okhttp3.Headers
import org.json.JSONObject
import android.util.Log

class MainActivity : AppCompatActivity() {

    // Quote UI elements
    private lateinit var quoteTextView: TextView
    private lateinit var authorTextView: TextView
    private lateinit var quoteCard: androidx.cardview.widget.CardView

    // Weather UI elements
    private lateinit var weatherIcon: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherCondition: TextView

    // ⭐ Journal UI elements
    private lateinit var journalInput: EditText
    private lateinit var saveEntryButton: Button

    // Temporary state to store metadata from the latest successful fetches
    private var lastQuote = ""
    private var lastAuthor = ""
    private var lastWeatherMood = ""
    private var lastTemperature = ""


    // API URLs
    // ⭐ FIX 1: Using a more stable ZenQuotes endpoint
    private val API_URL = "https://zenquotes.io/api/quotes/"

    // WEATHER API SETUP: Key injected and URL defined
    private val WEATHER_API_KEY = "17848ef932bd4af4bf7193259250412"
    private val WEATHER_BASE_URL = "https://api.weatherapi.com/v1/current.json"


    override fun onCreate(savedInstanceState: Bundle?) {
        // ⭐ 1. Apply Theme: Must be done before super.onCreate() and setContentView()
        val themePref = PreferenceManager.getDefaultSharedPreferences(this).getString("theme_preference", "dark")
        applyCustomTheme(themePref ?: "dark")

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 2. EDGE-TO-EDGE SETUP
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 3. INITIALIZATION CODE
        quoteTextView = findViewById(R.id.quoteTextView)
        authorTextView = findViewById(R.id.authorTextView)
        quoteCard = findViewById(R.id.quoteCard)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherTemp = findViewById(R.id.weatherTemp)
        weatherCondition = findViewById(R.id.weatherCondition)

        journalInput = findViewById(R.id.journalInput)
        saveEntryButton = findViewById(R.id.saveEntryButton)


        // 4. Fetch data on startup
        fetchQuote()
        fetchWeather()

        // 5. Set up click listeners
        quoteCard.setOnClickListener {
            fetchQuote()
        }
        findViewById<androidx.cardview.widget.CardView>(R.id.weatherCard).setOnClickListener {
            fetchWeather()
        }

        saveEntryButton.setOnClickListener {
            saveJournalEntry()
        }
    }

    // ⭐ FIX 2A: Override onResume to check the simple boolean flag
    override fun onResume() {
        super.onResume()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        // Check if the Settings Activity set the flag (meaning the theme was selected)
        val themeChanged = prefs.getBoolean("theme_changed_flag", false)

        if (themeChanged) {
            // Reset the flag immediately to prevent the loop
            prefs.edit().putBoolean("theme_changed_flag", false).apply()

            // Recreate the Activity to apply the new theme from onCreate
            recreate()
        }
    }

    // ⭐ Utility function to apply theme based on preference value (Unchanged)
    private fun applyCustomTheme(themeValue: String) {
        val themeResId = when (themeValue) {
            "pink" -> R.style.Theme_Welly_Pink
            "purple" -> R.style.Theme_Welly_Purple
            "dark" -> R.style.Theme_Welly_Dark
            else -> R.style.Theme_Welly_Dark
        }
        setTheme(themeResId)
    }

    /**
     * Maps the weather condition text to a mood suggestion and icon.
     */
    private fun getWeatherMood(conditionText: String, temperatureF: Double): Pair<String, String> {
        val icon: String
        val mood: String
        val lowerCaseCondition = conditionText.lowercase()

        when {
            lowerCaseCondition.contains("sun") || lowerCaseCondition.contains("clear") -> {
                icon = "☀️"
                mood = if (temperatureF > 75) "Feeling bright and motivated!" else "A perfect day for calm focus."
            }
            lowerCaseCondition.contains("cloud") || lowerCaseCondition.contains("overcast") -> {
                icon = "☁️"
                mood = "A quiet, reflective day for deep journaling."
            }
            lowerCaseCondition.contains("rain") || lowerCaseCondition.contains("drizzle") -> {
                icon = "🌧️"
                mood = "Time for hygge. Inside time is self-care time."
            }
            lowerCaseCondition.contains("snow") || lowerCaseCondition.contains("sleet") -> {
                icon = "❄️"
                mood = "Bundle up and find gratitude in the stillness."
            }
            lowerCaseCondition.contains("fog") || lowerCaseCondition.contains("mist") -> {
                icon = "🌫️"
                mood = "Slow down and embrace the quiet mystery."
            }
            lowerCaseCondition.contains("thunder") -> {
                icon = "⛈️"
                mood = "Channel that raw energy into powerful action."
            }
            else -> {
                icon = "?"
                mood = "Focus on your inner climate today."
            }
        }
        return Pair(icon, mood)
    }

    /**
     * Fetches a random inspirational quote from the ZenQuotes API.
     */
    private fun fetchQuote() {
        quoteTextView.text = "Loading inspiration..."
        authorTextView.text = ""

        val client = AsyncHttpClient()

        // ⭐ Use the fixed API URL
        client.get(API_URL, object : TextHttpResponseHandler() {
            override fun onSuccess(statusCode: Int, headers: Headers, jsonString: String) {
                try {
                    // ZenQuotes returns an array containing a single object: [{"q":..., "a":...}]
                    val jsonArray = org.json.JSONArray(jsonString)

                    if (jsonArray.length() > 0) {
                        val quoteObject = jsonArray.getJSONObject(0)

                        val quoteText = quoteObject.getString("q").trim()
                        var quoteAuthor = quoteObject.getString("a").trim()

                        if (quoteAuthor.isEmpty() || quoteAuthor.equals("null", ignoreCase = true)) {
                            quoteAuthor = "Unknown"
                        }

                        // Update temporary state upon successful fetch
                        lastQuote = quoteText
                        lastAuthor = quoteAuthor

                        quoteTextView.text = "\"$quoteText\""
                        authorTextView.text = "— $quoteAuthor"
                    } else {
                        quoteTextView.text = "Error."
                        authorTextView.text = "No quote found in response."
                    }

                } catch (e: Exception) {
                    Log.e("WellyApp", "Error parsing quote JSON: ${e.message}")
                    quoteTextView.text = "Error parsing data."
                    authorTextView.text = "The server returned malformed JSON or the response was not an array."
                }
            }

            override fun onFailure(statusCode: Int, headers: Headers?, responseString: String?, throwable: Throwable?) {
                Log.e("WellyApp", "Quote fetch failed: $statusCode - ${throwable?.message}")
                quoteTextView.text = "Failed to load quote."
                authorTextView.text = "Check connection ($statusCode)."
                throwable?.printStackTrace()
            }
        })
    }

    /**
     * Fetches weather data and provides a wellness mood suggestion using WeatherAPI.com.
     */
    private fun fetchWeather() {
        weatherIcon.text = "..."
        weatherTemp.text = "--°F"
        weatherCondition.text = "Getting data..."

        val client = AsyncHttpClient()
        val params = RequestParams()
        params.put("key", WEATHER_API_KEY)
        params.put("q", "New York") // Default location for testing

        client.get(WEATHER_BASE_URL, params, object : TextHttpResponseHandler() {
            override fun onSuccess(statusCode: Int, headers: Headers, jsonString: String) {
                try {
                    val jsonObject = JSONObject(jsonString)
                    val current = jsonObject.getJSONObject("current")

                    val temperatureF = current.getDouble("temp_f")
                    val conditionText = current.getJSONObject("condition").getString("text")

                    val tempFormatted = String.format("%.0f°F", temperatureF)

                    val (icon, mood) = getWeatherMood(conditionText, temperatureF)

                    // Update temporary state upon successful fetch
                    lastWeatherMood = mood
                    lastTemperature = tempFormatted

                    // Update UI
                    weatherIcon.text = icon
                    weatherTemp.text = tempFormatted
                    weatherCondition.text = mood

                } catch (e: Exception) {
                    Log.e("WellyApp", "Error parsing weather JSON: ${e.message}")
                    weatherIcon.text = "⚠️"
                    weatherTemp.text = "--°F"
                    weatherCondition.text = "Data Error"
                }
            }

            override fun onFailure(statusCode: Int, headers: Headers?, responseString: String?, throwable: Throwable?) {
                Log.e("WellyApp", "Weather fetch failed: $statusCode - ${throwable?.message}")
                weatherIcon.text = "❌"
                weatherTemp.text = "Err"
                weatherCondition.text = "No Weather"
            }
        })
    }

    /**
     * Gathers all metadata and the journal text, and logs/saves the entry.
     */
    private fun saveJournalEntry() {
        val entryText = journalInput.text.toString()

        if (entryText.isBlank()) {
            Toast.makeText(this, "Please write something before saving!", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        // Compile all entry metadata
        val journalEntry = """
            --- Journal Entry ---
            Timestamp: $timestamp
            Quote: "$lastQuote" by $lastAuthor
            Weather: $lastTemperature | Mood Suggestion: $lastWeatherMood
            Entry ID: ${UUID.randomUUID()}
            ---------------------
            Entry:
            $entryText
            ---------------------
        """.trimIndent()

        // Log the entry (Simulates saving to a database)
        Log.i("WellyJournal", journalEntry)

        // Clear the input and notify the user
        journalInput.setText("")
        Toast.makeText(this, "Entry saved successfully!", Toast.LENGTH_LONG).show()
    }


    // --- TOOLBAR MENU INFLATION ---
    override fun onCreateOptionsMenu(menu: Menu?) : Boolean {
        // Replace 'toolbar_menu' with the actual name of your XML file if different
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    // --- TOOLBAR CLICK HANDLING ---
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                Toast.makeText(this, "Search initiated", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true // Return true to signal that the event was handled
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}