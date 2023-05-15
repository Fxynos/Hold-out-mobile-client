package com.vl.holdout

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity: AppCompatActivity() {
    private lateinit var soundFlag: CheckBox
    private lateinit var shared: SettingsShared

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        shared = SettingsShared(this)
        soundFlag = findViewById<CheckBox?>(R.id.sound_flag)
            .also {
                it.isChecked = shared.isSoundEnabled
                it.setOnCheckedChangeListener { _, v -> shared.isSoundEnabled = v }
            }
    }

    override fun onBackPressed() {
        startActivity(Intent(this, MenuActivity::class.java))
        finish()
    }

}