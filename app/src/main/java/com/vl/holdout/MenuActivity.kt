package com.vl.holdout

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.vl.holdout.quests.QuestsActivity

class MenuActivity: AppCompatActivity(), OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        listOf<Button>(findViewById(R.id.button_play), findViewById(R.id.button_settings))
            .forEach { it.setOnClickListener(this) }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.button_play -> {
                startActivity(Intent(this, QuestsActivity::class.java))
                finish()
            }
            R.id.button_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            }
        }
    }
}