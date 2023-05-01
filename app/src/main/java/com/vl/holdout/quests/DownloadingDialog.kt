package com.vl.holdout.quests

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.vl.holdout.R

class DownloadingDialog(private val quest: String): DialogFragment() {
    private lateinit var header: TextView
    private lateinit var title: TextView
    private var description: TextView? = null

    private var lateText: String = "Подождите..."

    var text: String
        get() = description.let { it?.text?.toString() ?: lateText }
        set(value) { if (description == null) lateText = value else description!!.text = value }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        requireDialog().setCanceledOnTouchOutside(false) // isCancelable = false
        val view = inflater.inflate(R.layout.dialog_downloading, container, false)
        header = view.findViewById(R.id.downloading_dialog_header)
        title = view.findViewById(R.id.downloading_dialog_title)
        description = view.findViewById(R.id.downloading_dialog_description)

        description!!.text = lateText
        title.text = getString(R.string.downloading_story_title, quest)
        return view
    }

    override fun onResume() {
        super.onResume()
        dialog!!.window!!.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(null)
        }
    }
}