package com.julogic.jukavpn

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.julogic.jukavpn.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: Create activity_about.xml layout
        // binding = ActivityAboutBinding.inflate(layoutInflater)
        // setContentView(binding.root)
        
        // For now, just finish - placeholder activity
        finish()
    }
}
