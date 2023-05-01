package com.vl.holdout.quests

interface OnQuestActionListener {
    fun onDownloadClick(quest: AvailableQuest)
    fun onDeleteClick(quest: LoadedQuest)
    fun onPlayClick(quest: LoadedQuest)
}