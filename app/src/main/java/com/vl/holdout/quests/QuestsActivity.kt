package com.vl.holdout.quests

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.vl.holdout.GameActivity
import com.vl.holdout.InfoToast
import com.vl.holdout.MenuActivity
import com.vl.holdout.R
import com.vl.holdout.SettingsShared
import kotlinx.coroutines.CoroutineScope
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.collections.ArrayList
import kotlin.streams.toList

class QuestsActivity: AppCompatActivity(), OnQuestActionListener {
    private lateinit var adapter: QuestsListAdapter
    private lateinit var stateHolder: QuestsStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quests)
        adapter = QuestsListAdapter(this, this)
        findViewById<RecyclerView>(R.id.stories_list).adapter = adapter
        stateHolder = ViewModelProvider(this, QuestsStateHolderFactory(
            QuestsRepository(
                File(applicationInfo.dataDir, "quests").also { if (!it.exists()) it.mkdir() },
                File(applicationInfo.dataDir, "downloads").also { if (!it.exists()) it.mkdir() },
                SettingsShared(this).host
            )
        ))[QuestsStateHolder::class.java]
        stateHolder.quests.observe(this@QuestsActivity) { adapter.quests = it }
        launch {
            if (!stateHolder.areAvailableQuestsFetched && !stateHolder.updateQuestsList())
                withContext(Dispatchers.Main) { onConnectionError() }
        }
        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@QuestsActivity, MenuActivity::class.java))
                finish()
            }
        })
    }

    private fun onConnectionError() =
        InfoToast(
            this,
            getString(R.string.no_connection),
            R.drawable.ic_wifi_off
        ).show()

    override fun onDownloadClick(quest: AvailableQuest) {
        val dialog = DownloadingDialog(quest.name)
        dialog.show(supportFragmentManager.beginTransaction(), null)
        launch {
            stateHolder.downloadQuest(quest) { text: String, isFinal: Boolean ->
                dialog.text = text
                if (isFinal) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        delay(2000)
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    override fun onDeleteClick(quest: LoadedQuest) =
        BinaryAskDialog(
            "Удалить квест?",
            "\"${quest.name}\" будет безвозвратно удалён",
            "Удалить",
            "Отмена",
            true
        ) {
            if (it)
                launch { stateHolder.removeQuest(quest) }
        }.show(supportFragmentManager, null)

    override fun onPlayClick(quest: LoadedQuest) {
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra("quest", quest.name)
        })
        finish()
    }

    private fun launch(job: suspend CoroutineScope.() -> Unit) = lifecycleScope.launch(block = job)
}

private class QuestsStateHolderFactory(
    private val questsRepository: QuestsRepository
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (QuestsStateHolder::class.java.isAssignableFrom(modelClass))
            QuestsStateHolder(questsRepository) as T
        else
            throw IllegalArgumentException("Factory doesn't support creation of ${modelClass.name}")
}

private class QuestsStateHolder(
    private val questsRepository: QuestsRepository
): ViewModel() { // TODO inject app context for R.string res

    val quests = MutableLiveData<MutableList<Quest>>()
    val areAvailableQuestsFetched: Boolean
        get() = availableQuests != null

    private var availableQuests: List<AvailableQuest>? = null



    /**
     * @return result of fetching available quests list
     */
    suspend fun updateQuestsList(): Boolean {
        val loadedQuests = ArrayList<Quest>(questsRepository.loadDownloadedQuests())
        quests.value = loadedQuests
        availableQuests = questsRepository.loadAvailableQuests()
            ?: return false
        withContext(Dispatchers.Main) {
            availableQuests!!.stream()
                .filter { !loadedQuests.contains(it) }
                .forEach { quests.value!!.add(it) }
            quests.value = quests.value
        }
        return true
    }

    suspend fun removeQuest(quest: LoadedQuest) {
        questsRepository.deleteDownloadedQuest(quest)
        withContext(Dispatchers.Main) {
            val index = quests.value!!.indexOf(quest).takeIf { it != -1 }
                ?: return@withContext
            quests.value!!.removeAt(index)
            val availableQuest = AvailableQuest(quest.name)
            if (availableQuests?.contains(availableQuest) == true)
                quests.value!!.add(index, availableQuest)
            quests.value = quests.value // workaround to observe list changes
        }
    }

    suspend fun downloadQuest(quest: AvailableQuest, onStateChanged: (String, Boolean) -> Unit) {
        withContext(Dispatchers.Main) { onStateChanged("Скачивание...", false) }
        questsRepository.downloadQuest(quest)
        withContext(Dispatchers.Main) { onStateChanged("Распаковка...", false) }
        questsRepository.decompressQuest(quest.name)
        withContext(Dispatchers.Main) {
            onStateChanged("Установлено", true)
            val index = quests.value!!.indexOf(quest).takeIf { it != -1 }
                ?.also { quests.value!!.removeAt(it) } ?: 0
            quests.value!!.add(index, LoadedQuest(quest.name))
            quests.value = quests.value // workaround to observe list changes
        }
    }
}

private class QuestsRepository(
    private val questsDir: File,
    private val downloadsDir: File,
    private val host: String
) {
    private val client: QuestsAPI by lazy {
        Retrofit.Builder().baseUrl("http://${host}")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .writeTimeout(1, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(QuestsAPI::class.java)
    }

    suspend fun deleteDownloadedQuest(quest: LoadedQuest) =
        withContext(Dispatchers.IO) {
            File(questsDir, quest.name).deleteRecursively()
        }

    fun loadDownloadedQuests() =
        Arrays.stream(questsDir.listFiles()!!)
            .filter { it.isDirectory }
            .map { LoadedQuest(it.name) }.toList()

    suspend fun loadAvailableQuests(): List<AvailableQuest>? = // returns null on network error
        withContext(Dispatchers.IO) {
            try {
                return@withContext client.getList().execute().body()!!.stream().map { AvailableQuest(it) }.toList()
            } catch (exception: IOException) {
                exception.printStackTrace()
                return@withContext null
            }
        }

    suspend fun downloadQuest(quest: AvailableQuest) =
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

    suspend fun decompressQuest(quest: String) {
        withContext(Dispatchers.IO) {
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
            File(downloadsDir, "$quest.zip").delete()
        }
    }
}