package com.julogic.jukavpn

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.julogic.jukavpn.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Create activity_settings.xml layout
        // binding = ActivitySettingsBinding.inflate(layoutInflater)
        // setContentView(binding.root)
        
        // For now, just finish - placeholder activity
        finish()
    }
}
