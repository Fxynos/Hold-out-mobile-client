package com.vl.holdout.quests

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.vl.holdout.R

class BinaryAskDialog(
    private val title: String,
    private val text: String,
    private val positiveAnswer: String,
    private val negativeAnswer: String,
    private val cancelable: Boolean,
    private val onChoice: (positive: Boolean)->Unit
): DialogFragment() {
    private lateinit var titleView: TextView
    private lateinit var textView: TextView
    private lateinit var positiveButton: Button
    private lateinit var negativeButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireDialog().setCancelable(cancelable)
        val view = inflater.inflate(R.layout.dialog_binary_ask, container, false)
        titleView = view.findViewById<TextView>(R.id.title).also { it.text = title }
        textView = view.findViewById<TextView>(R.id.text).also { it.text = text }
        positiveButton = view.findViewById<Button>(R.id.pos_button).also {
            it.text = positiveAnswer
            it.setOnClickListener {
                onChoice(true)
                dismiss()
            }
        }
        negativeButton = view.findViewById<Button>(R.id.neg_button).also {
            it.text = negativeAnswer
            it.setOnClickListener {
                onChoice(false)
                dismiss()
            }
        }
        return view
    }

    override fun onResume() {
        super.onResume()
        requireDialog().window!!.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(null)
        }
    }
}