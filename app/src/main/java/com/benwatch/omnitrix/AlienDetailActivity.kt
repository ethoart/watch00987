package com.benwatch.omnitrix

import android.animation.ValueAnimator
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlienDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SLOT = "extra_slot"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var glowAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alien_detail)
        hideSystemBars()

        val slot = intent.getIntExtra(EXTRA_SLOT, 1)
        val alien = AlienRoster.ALIENS.firstOrNull { it.slot == slot } ?: AlienRoster.ALIENS.first()

        val imageAlien = findViewById<ImageView>(R.id.imageAlienBig)
        val imageGlow  = findViewById<ImageView>(R.id.imageGlow)
        val textName   = findViewById<TextView>(R.id.textName)

        // Load the actual alien diamond-silhouette image
        imageAlien.setImageResource(AlienRoster.resolveDrawable(this, alien.imageResName))
        textName.text = alien.displayName

        // Glow colour matches current mode
        val glowRes = when (ModeManager.load(this)) {
            WatchMode.DNA_SCAN  -> R.drawable.glow_radial_yellow
            WatchMode.LOW_POWER -> R.drawable.glow_radial_red
            WatchMode.NORMAL    -> R.drawable.glow_radial_green
        }
        imageGlow.setImageResource(glowRes)
        textName.setTextColor(when (ModeManager.load(this)) {
            WatchMode.DNA_SCAN  -> 0xFFFFD500.toInt()
            WatchMode.LOW_POWER -> 0xFFFF1A1A.toInt()
            WatchMode.NORMAL    -> 0xFF39FF14.toInt()
        })

        startGlowPulse(imageGlow, imageAlien)
        playAlienSound(alien)

        // Tap anywhere to go back
        findViewById<View>(R.id.root).setOnClickListener { finish() }
    }

    private fun startGlowPulse(glow: ImageView, alienImg: ImageView) {
        glowAnimator = ValueAnimator.ofFloat(0f, 1f, 0.5f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                glow.alpha = v * 0.9f + 0.1f
                val scale = 0.92f + 0.08f * v
                alienImg.scaleX = scale; alienImg.scaleY = scale
            }
            start()
        }
    }

    private fun playAlienSound(alien: Alien) {
        val id = AlienRoster.resolveSound(this, alien.soundResName)
        if (id == 0) return
        try {
            mediaPlayer = MediaPlayer.create(this, id)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (_: Exception) {}
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

    override fun onDestroy() {
        super.onDestroy()
        glowAnimator?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
