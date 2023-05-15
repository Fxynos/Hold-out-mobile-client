package com.vl.holdout.quests

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.vl.holdout.GameActivity
import com.vl.holdout.MenuActivity
import com.vl.holdout.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.collections.ArrayList
import kotlin.streams.toList

class QuestsActivity: AppCompatActivity(), OnQuestActionListener {
    companion object {
        const val baseUrl = "http://192.168.0.10"
    }

    private lateinit var questsDir: File
    private lateinit var downloadsDir: File

    private lateinit var adapter: QuestsListAdapter
    private val client = Retrofit.Builder().baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create()).build()
        .create(QuestsAPI::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quests)
        questsDir = File(applicationInfo.dataDir, "quests")
            .also { if (!it.exists()) it.mkdir() }
        downloadsDir = File(applicationInfo.dataDir, "downloads")
            .also { if (!it.exists()) it.mkdir() }
        adapter = QuestsListAdapter(this, this)
        findViewById<RecyclerView>(R.id.stories_list).adapter = adapter
        fillList()
    }

    override fun onBackPressed() {
        startActivity(Intent(this, MenuActivity::class.java))
        finish()
    }

    override fun onDownloadClick(quest: AvailableQuest) {
        val dialog = DownloadingDialog(quest.name)
        dialog.show(supportFragmentManager.beginTransaction(), null)
        download(quest) { text: String, isFinal: Boolean ->
            dialog.text = text
            if (isFinal) {
                File(downloadsDir, "${quest.name}.zip").delete()
                lifecycleScope.launch(Dispatchers.Main) {
                    delay(2000)
                    dialog.dismiss()
                }
            }
        }
    }

    override fun onDeleteClick(quest: LoadedQuest) =
        BinaryAskDialog(
            "Удалить квест?",
            "\"${quest.name}\" будет безвозвратно удалён",
            "Удалить",
            "Отмена"
        ) {
            if (it) lifecycleScope.launch {
                delete(quest)
            }
        }.show(supportFragmentManager, null)

    override fun onPlayClick(quest: LoadedQuest) {
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra("quest", quest.name)
        })
        finish()
    }

    private suspend fun delete(quest: LoadedQuest) {
        withContext(Dispatchers.IO) {
            File(questsDir, quest.name).deleteRecursively()
        }
        withContext(Dispatchers.Main) {
            removeFromList(quest)
        }
    }

    private fun download(quest: AvailableQuest, onStateChanged: (text: String, finalState: Boolean)->Unit) {
        onStateChanged("Скачивание...", false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { // TODO handle exceptions
                val content = client.getQuest(quest.name).execute().body()!!.byteStream()
                File(downloadsDir, "${quest.name}.zip").outputStream().use {
                        stream ->
                    val buf = ByteArray(2048)
                    var size: Int
                    while (content.read(buf).also { size = it } != -1)
                        stream.write(buf, 0, size)
                }
            }
            withContext(Dispatchers.Main) {
                onStateChanged("Распаковка...", false)
            }
            withContext(Dispatchers.IO) {
                decompressQuest(quest.name)
            }
            withContext(Dispatchers.Main) {
                onStateChanged("Установлено", true)
                insertToList(LoadedQuest(quest.name))
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun insertToList(quest: LoadedQuest) {
        adapter.quests.add(quest)
        adapter.quests = kotlin.collections.ArrayList(adapter.quests.stream().filter {
            it is LoadedQuest || it.name != quest.name
        }.toList())
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun removeFromList(quest: LoadedQuest) {
        adapter.quests = kotlin.collections.ArrayList(adapter.quests.stream().filter {
            it != quest && it is LoadedQuest
        }.toList())
        lifecycleScope.launch {
            adapter.quests.addAll(loadAvailableQuests())
            withContext(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun fillList() {
        val quests = mutableListOf<Quest>()
        Arrays.stream(questsDir.listFiles()!!)
            .filter { it.isDirectory }
            .map { LoadedQuest(it.name) }.toList().also { quests.addAll(it) }
        lifecycleScope.launch {
            quests.addAll(loadAvailableQuests().stream().filter { !quests.contains(it) }.toList())
            withContext(Dispatchers.Main) {
                adapter.quests = quests
                adapter.notifyItemRangeInserted(0, quests.size)
            }
        }
    }

    private suspend fun loadAvailableQuests(): List<AvailableQuest> {
        val list: List<AvailableQuest>
        withContext(Dispatchers.IO) {
            list = client.getList().execute().body()!!.stream()
                .map { AvailableQuest(it) }.toList()
        }
        return list
    }

    private fun decompressQuest(quest: String) {
        File(questsDir, quest).mkdirs()
        val zipInput = ZipInputStream(BufferedInputStream(File(downloadsDir, "$quest.zip").inputStream()))
        var entry: ZipEntry?
        while (zipInput.nextEntry.also { entry = it } != null) {
            val file = File(questsDir, "$quest/${entry!!.name}")
            if (entry!!.isDirectory)
                file.mkdirs()
            else {
                val outputStream = FileOutputStream(file)
                val buf = ByteArray(1024)
                var count: Int
                while (zipInput.read(buf).also { count = it } != -1)
                    outputStream.write(buf, 0, count)
                outputStream.close()
                zipInput.closeEntry()
            }
        }
        zipInput.close()
    }
}