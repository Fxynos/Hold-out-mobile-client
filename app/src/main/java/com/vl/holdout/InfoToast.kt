package com.vl.holdout

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes

class InfoToast(
    context: Context,
    private val text: String,
    @DrawableRes private val resId: Int
): Toast(context) {
    init {
        view = LayoutInflater.from(context).inflate(R.layout.toast_info, null)?.apply {
            findViewById<TextView>(R.id.info).text = text
            findViewById<ImageView>(R.id.icon).setImageResource(resId)
        }
        duration = LENGTH_LONG
    }
}