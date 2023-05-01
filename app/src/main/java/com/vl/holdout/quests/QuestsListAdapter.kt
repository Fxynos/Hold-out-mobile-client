package com.vl.holdout.quests

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.vl.holdout.*
import java.util.*

// TODO use paging (it's not necessary yet)
class QuestsListAdapter(
    context: Context,
    questActionListener: OnQuestActionListener,
    vararg quests: Quest
): RecyclerView.Adapter<QuestsListAdapter.ViewHolder>(), OnQuestActionListener by questActionListener  {
    enum class ViewType(val id: Int, @LayoutRes val layout: Int) {
        LOADED(0, R.layout.stories_list_item_loaded),
        AVAILABLE(1, R.layout.stories_list_item_not_loaded); // not downloaded

        companion object {
            fun valueOf(id: Int) = Arrays.stream(ViewType.values()).filter { it.id == id }.findAny().get()

            fun `for`(quest: Quest) = when (quest) {
                is LoadedQuest -> LOADED
                is AvailableQuest -> AVAILABLE
                else -> throw IllegalArgumentException("Adapter doesn't support this type of quests (${quest::class.java.name})")
            }
        }
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    var quests: MutableList<Quest> = quests.toMutableList()

    override fun getItemViewType(position: Int) = ViewType.`for`(quests[position]).id

    override fun getItemCount() = quests.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewType.valueOf(viewType).let {
            val view = inflater.inflate(it.layout, parent, false)
            when (it) {
                ViewType.LOADED -> LoadedQuestViewHolder(view)
                ViewType.AVAILABLE -> AvailableQuestViewHolder(view)
            } as ViewHolder
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(quests[position])

    abstract inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        abstract fun bind(quest: Quest)
    }

    inner class LoadedQuestViewHolder(view: View): ViewHolder(view), View.OnClickListener {
        private val name: TextView = view.findViewById(R.id.name)
        private val delete: ImageButton = view.findViewById(R.id.button_delete)
        private val play: ImageButton = view.findViewById(R.id.button_play)

        init {
            delete.setOnClickListener(this)
            play.setOnClickListener(this)
        }

        override fun bind(quest: Quest) {
            name.text = quest.name
        }

        override fun onClick(view: View) {
            val quest = quests[adapterPosition] as LoadedQuest
            when (view) {
                delete -> onDeleteClick(quest)
                play -> onPlayClick(quest)
            }
        }
    }

    inner class AvailableQuestViewHolder(view: View): ViewHolder(view), View.OnClickListener {
        private val name: TextView = view.findViewById(R.id.name)
        private val download: ImageButton = view.findViewById(R.id.button_download)

        init {
            download.setOnClickListener(this)
        }

        override fun bind(quest: Quest) {
            name.text = quest.name
        }

        override fun onClick(view: View) {
            if (view === download)
                onDownloadClick(quests[adapterPosition] as AvailableQuest)
        }
    }
}