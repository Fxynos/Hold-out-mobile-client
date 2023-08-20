package com.vl.holdout

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity: AppCompatActivity() {
    private lateinit var soundFlag: CheckBox
    private lateinit var host: EditText
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
        host = findViewById<EditText>(R.id.host).also {
            it.setText(shared.host)
            it.setOnEditorActionListener { _, code, _ ->
                if (code == EditorInfo.IME_ACTION_DONE)
                    host.clearFocus()
                false
            }
        }
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@SettingsActivity, MenuActivity::class.java))
                finish()
            }
        })
    }

    override fun onPause() {
        super.onPause()
        shared.host = host.text.toString()
    }
}