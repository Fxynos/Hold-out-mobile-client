package com.vl.barview

import android.content.Context
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.properties.Delegates

class BarView(context: Context, attrs: AttributeSet): FrameLayout(context, attrs) {
    private var background: ImageView? = null
    private var foreground: ImageView? = null
    private var drawable: Drawable? = null
    @ColorInt private var backgroundColor: Int? = null
    @ColorInt private var foregroundColor: Int? = null
    private var areResGot = false
    var progress by Delegates.observable(0f) { // values [ 0.0 - 1.0 ]
        _, _, _ ->
        if (areResGot)
            updateForeground()
    }

    init {
        val view = inflate(context, R.layout.view_bar, this)
        background = view.findViewById(R.id.background)
        foreground = view.findViewById(R.id.foreground)
        val attributes = context.theme.obtainStyledAttributes(attrs, R.styleable.BarView, 0, 0)
        try {
            backgroundColor = attributes.getColor(R.styleable.BarView_color, 0x000000)
            foregroundColor = attributes.getColor(R.styleable.BarView_active_color, 0xFFFFFF)
            drawable = attributes.getDrawable(R.styleable.BarView_src)
            progress = attributes.getFloat(R.styleable.BarView_progress, 0f)
        } finally {
            attributes.recycle()
        }
        updateBackground()
        updateForeground()
        areResGot = true
    }

    fun setColor(@ColorInt color: Int) {
        backgroundColor = color
        updateBackground()
    }

    fun setActiveColor(@ColorInt color: Int) {
        foregroundColor = color
        updateForeground()
    }

    fun setDrawable(drawable: Drawable) {
        this.drawable = drawable
        updateBackground()
        updateForeground()
    }

    private fun updateForeground() {
        val drawable = ClipDrawable(drawable!!.deepCopy()!!, Gravity.BOTTOM, ClipDrawable.VERTICAL)
        drawable.level = (progress * 10_000).toInt()
        drawable.setTint(foregroundColor!!)
        foreground!!.setImageDrawable(drawable)
    }

    private fun updateBackground() { // TODO avoid such many non-null assertions
        val drawable = drawable!!.deepCopy()!!
        drawable.setTint(backgroundColor!!)
        background!!.setImageDrawable(drawable)
    }

    private fun Drawable.deepCopy() = this.constantState?.newDrawable()?.mutate()
}