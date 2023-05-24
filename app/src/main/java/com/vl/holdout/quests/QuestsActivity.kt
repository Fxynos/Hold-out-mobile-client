package com.vl.holdout.quests

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.vl.holdout.GameActivity
import com.vl.holdout.InfoToast
import com.vl.holdout.MenuActivity
import com.vl.holdout.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.IntStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.NoSuchElementException
import kotlin.collections.ArrayList
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.toList

class QuestsActivity: AppCompatActivity(), OnQuestActionListener {
    companion object {
        const val baseUrl = "http://192.168.0.10"
    }

    private lateinit var questsDir: File
    private lateinit var downloadsDir: File

    private lateinit var adapter: QuestsListAdapter
    private var availableQuests: List<AvailableQuest>? = null
    private val client = Retrofit.Builder().baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        )
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

    private fun insertToList(quest: LoadedQuest) { // list must already contain AvailableQuest with the same name
        IntStream.range(0, adapter.quests.size)
            .filter { i -> adapter.quests[i].let { it is AvailableQuest && it.name == quest.name } }
            .findAny().asInt.let {
                adapter.quests[it] = quest
                adapter.notifyItemChanged(it)
            }
    }

    private fun removeFromList(quest: LoadedQuest) {
        adapter.quests.indexOf(quest).takeUnless { it < 0 }?.also { index ->
            availableQuests?.stream()?.filter { aQuest -> aQuest.name == quest.name }
                ?.findAny()?.getOrNull()?.also {
                    adapter.quests[index] = it
                    adapter.notifyItemChanged(index)
                } ?: kotlin.run {
                    adapter.quests.removeAt(index)
                    adapter.notifyItemRemoved(index)
            }
        } ?: throw NoSuchElementException("No such quest: \"${quest.name}\"")
    }

    private fun fillList() {
        adapter.quests = ArrayList(Arrays.stream(questsDir.listFiles()!!)
            .filter { it.isDirectory }
            .map { LoadedQuest(it.name) }.toList())
        val localLoadedCount = adapter.quests.size
        adapter.notifyItemRangeInserted(0, localLoadedCount) // loaded quests from package

        lifecycleScope.launch {
            availableQuests = loadAvailableQuests()?.also {
                adapter.quests.addAll(it.stream().filter {
                        availableQuest -> !adapter.quests.contains(availableQuest)
                }.toList())
            }
            withContext(Dispatchers.Main) {
                availableQuests?.also {
                    if (adapter.quests.size > localLoadedCount)
                        adapter.notifyItemRangeInserted(localLoadedCount, adapter.quests.size)
                } ?: InfoToast(
                    this@QuestsActivity,
                    getString(R.string.no_connection),
                    R.drawable.ic_wifi_off
                ).show()
            }
        }
    }

    private suspend fun loadAvailableQuests(): List<AvailableQuest>? { // returns null on network error
        var list: List<AvailableQuest>? = null
        withContext(Dispatchers.IO) {
            try {
                list = client.getList().execute().body()!!.stream()
                    .map { AvailableQuest(it) }.toList()
            } catch (exception: IOException) {
                exception.printStackTrace()
            }
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