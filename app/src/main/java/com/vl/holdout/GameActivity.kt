package com.vl.holdout

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.vl.barview.BarView
import com.vl.holdout.parser.Parser
import com.vl.holdout.parser.pojo.Bar
import com.vl.holdout.parser.pojo.Card
import com.vl.holdout.parser.pojo.Choice
import com.vl.holdout.pull.*
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class GameActivity: AppCompatActivity(), ChoiceHandler.OnChoiceListener {
    companion object {
        const val IMAGE_SIZE = 480 // FIXME
    }

    private val rand = Random(System.currentTimeMillis())
    private lateinit var currentCard: Card
    private lateinit var cardCanvas: Canvas

    private lateinit var cardView: CardView
    private lateinit var image: ImageView
    private lateinit var text: TextView
    private lateinit var actor: TextView
    private lateinit var name: TextView
    private lateinit var counter: TextView
    private lateinit var counterDetail: TextView
    private lateinit var dispatcher: PullDispatcher
    private lateinit var barViews: Array<BarView>
    private lateinit var bars: Map<Bar, BarView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        initViews(savedInstanceState)
        Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888).also {
            cardCanvas = Canvas(it)
            image.setImageBitmap(it)
        }
        initQuest(intent.getStringExtra("quest")!!)
        cardView.post {
            dispatcher = PullDispatcher(
                findViewById(R.id.pull_area),
                cardView,
                cardView.width.toFloat(),
                cardView.height.toFloat()
            )
            dispatcher.addOnPullListeners(
                RotatePullAnimator(30f),
                MovePullAnimator(1f, 2f)
            )
            dispatcher.addOnReleaseListeners(
                ConditionalPullHandler(
                    listOf( // On choice
                        FlyAwayPullAnimator(
                            1000,
                            cardView.width * 2f,
                            60f,
                            3000,
                            cardView.height * 4f
                        ),
                        ChoiceHandler(this)
                    ),
                    listOf( // On return
                        ReleasePullAnimator(250)
                    )
                ) { event -> abs(event.dx) >= event.rangeHorizontal / 2 }
            )
        }

        /*demonstrate(findViewById(R.id.character_bar), 3000)
        demonstrate(findViewById(R.id.health_bar), 5000)
        demonstrate(findViewById(R.id.knowledge_bar), 1000)
        demonstrate(findViewById(R.id.wealth_bar), 8000)*/ //TODO
    }

    override fun onChoice(choiceId: Int) {
        when (choiceId) {
            ChoiceHandler.CHOICE_LEFT -> applyChoice(currentCard.choices[0])
            ChoiceHandler.CHOICE_RIGHT -> applyChoice(currentCard.choices[1])
        }
        /*when (choiceId) {
            ChoiceHandler.CHOICE_LEFT, ChoiceHandler.CHOICE_RIGHT -> {
                Toast.makeText(
                    this,
                    "choice: ${if (choiceId == ChoiceHandler.CHOICE_LEFT) "left" else "right"}",
                    Toast.LENGTH_SHORT
                ).show()
                //dispatcher.lock(object: Lock {})
            }
            else -> throw IllegalArgumentException("unknown choice id")
        }*/
    }

    private fun initQuest(questName: String) {
        val questRoot = File(File(this.applicationInfo.dataDir, "quests"), questName)
        val main = parse(questRoot).core["main"]
        bars = IntStream.range(0, main.bars.size).mapToObj { i ->
            val pair = main.bars[i] to barViews[i]
            pair.apply{ second.progress = first.value.toFloat() }
        }.collect(Collectors.toMap(
            { it.first },
            { it.second }
        ))
        applyChoice(main.choice)
    }

    private fun initViews(savedInstanceState: Bundle?) {
        barViews = arrayOf(
            findViewById(R.id.bar1),
            findViewById(R.id.bar2),
            findViewById(R.id.bar3),
            findViewById(R.id.bar4)
        )
        text = findViewById(R.id.text)
        actor = findViewById(R.id.actor)
        name = findViewById(R.id.self_name)
        counter = findViewById(R.id.counter)
        counterDetail = findViewById(R.id.counter_detail)
        cardView = findViewById(R.id.event_card)
        image = findViewById(R.id.cardImage)
    }

    private fun parse(root: File) = Parser.load(root)

    /*private fun demonstrate(view: BarView, duration: Long) {
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.interpolator = LinearInterpolator()
        anim.duration = duration
        anim.repeatCount = ValueAnimator.INFINITE
        anim.repeatMode = ValueAnimator.REVERSE
        anim.addUpdateListener {
                value -> view.progress = value.animatedValue as Float
        }
        anim.start()
    }*/

    /* ... */

    private fun applyChoice(choice: Choice) {
        choice.affects.forEach { (bar, affect) ->
            bars[bar]!!.apply {
                progress = if (affect.type == Choice.Affect.Type.EXPLICIT)
                    affect.value.toFloat()
                else
                    maxOf(minOf(progress + affect.value.toFloat(), 1f), 0f)
            }
        }
        loadCard(choice.cards.let { it[rand.nextInt(it.size)] })
    }

    private fun loadCard(card: Card) {
        currentCard = card
        text.text = card.text
        actor.text = card.title
        Arrays.stream(card.picture.layers).forEach { it.accept(cardCanvas) }
    }
}