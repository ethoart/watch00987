package com.benwatch.omnitrix

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var dialView: OmnitrixDialView
    private lateinit var textMode: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()

        dialView = findViewById(R.id.dialView)
        textMode = findViewById(R.id.textMode)

        val savedMode = ModeManager.load(this)
        dialView.mode = savedMode
        updateModeLabel(savedMode)

        // Tap the core symbol → open the alien grid picker
        dialView.onActivate = {
            startActivity(Intent(this, AlienSelectActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Tap directly on an alien icon on the ring → jump straight to detail
        dialView.onAlienSelected = { slot ->
            val intent = Intent(this, AlienDetailActivity::class.java)
            intent.putExtra(AlienDetailActivity.EXTRA_SLOT, slot)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Long-press core → cycle mode
        dialView.onModeCycle = { newMode ->
            ModeManager.save(this, newMode)
            updateModeLabel(newMode)
        }
    }

    private fun updateModeLabel(mode: WatchMode) {
        textMode.text = mode.label
        textMode.setTextColor(when (mode) {
            WatchMode.NORMAL    -> 0xFF39FF14.toInt()
            WatchMode.DNA_SCAN  -> 0xFFFFD500.toInt()
            WatchMode.LOW_POWER -> 0xFFFF1A1A.toInt()
        })
    }

    override fun onResume() {
        super.onResume()
        val mode = ModeManager.load(this)
        dialView.mode = mode
        updateModeLabel(mode)
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}
