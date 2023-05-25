package com.vl.holdout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.SoundPool
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.vl.barview.BarView
import com.vl.holdout.parser.Parser
import com.vl.holdout.parser.pojo.Bar
import com.vl.holdout.parser.pojo.Card
import com.vl.holdout.parser.pojo.Choice
import com.vl.holdout.pull.*
import java.io.File
import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

// FIXME almost God object anti-pattern
@SuppressLint("ClickableViewAccessibility")
class GameActivity: AppCompatActivity(), ChoiceHandler.OnChoiceListener, Lock {
    companion object {
        const val IMAGE_SIZE = 480
        const val DURATION_ANSWER_APPEARING = 250L
        const val DURATION_TEXT_APPEARING = 250L
        const val DURATION_BACKGROUND_CHANGING = 500L
    }

    private val rand = Random(System.currentTimeMillis())
    private lateinit var currentCard: Card
    private var pendingCard: Card? = null // card that will be loaded after previous one flew away
    private lateinit var cardCanvas: Canvas

    private lateinit var eventBackground: View
    private lateinit var cardView: CardView
    private lateinit var image: ImageView
    private lateinit var answer: TextView
    private lateinit var answerContainer: ViewGroup
    private lateinit var text: TextView
    private lateinit var actor: TextView
    private lateinit var name: TextView
    private lateinit var counter: TextView
    private lateinit var counterDetail: TextView
    private lateinit var dispatcher: PullDispatcher
    private lateinit var barViews: Array<BarView>
    private lateinit var bars: Map<Bar, BarView>

    private val fadeAppearAnimator = ValueAnimator()
    private val answerAnimator = ValueAnimator()
    private lateinit var soundPlayer: SoundPlayer

    private var cardReleased = true // used to track start of card pulling by answer container animator
    private var currentShownChoice: Int? = null // used to change choice titles (values {0, 1})

    private lateinit var eventBackgroundColors: Pair<Int, Int> // default color to color for last card

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        resolveValues()
        soundPlayer = SoundPlayer(this, 3)
        initViews()
        initAnimators()
        Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888).also {
            cardCanvas = Canvas(it)
            image.setImageBitmap(it)
        }
        cardView.post {
            initPullDispatcher()
            initQuest(intent.getStringExtra("quest")!!)
        }
    }

    override fun onBackPressed() {
        startActivity(Intent(this, MenuActivity::class.java))
        finish()
    }

    override fun onChoice(choiceId: Int) {
        when (choiceId) {
            ChoiceHandler.CHOICE_LEFT -> applyChoice(currentCard.choices[0])
            ChoiceHandler.CHOICE_RIGHT -> applyChoice(currentCard.choices[1])
            else -> throw RuntimeException() // unreachable
        }
        startTextsFading()
        soundPlayer.play(
            soundPlayer.soundCardFlyAway,
            if (choiceId == ChoiceHandler.CHOICE_LEFT) 1f else 0.5f,
            if (choiceId == ChoiceHandler.CHOICE_RIGHT) 1f else 0.5f
        )
    }

    /* Initialization */

    private fun resolveValues() {
        val typed = TypedValue()
        fun resolveInt(@AttrRes attr: Int) = typed.let {
            theme.resolveAttribute(attr, it, true)
            it.data
        }
        eventBackgroundColors =
            resolveInt(androidx.appcompat.R.attr.colorPrimary) to
            resolveInt(com.google.android.material.R.attr.colorPrimaryVariant)
    }

    private fun initQuest(questName: String) {
        val questRoot = File(File(this.applicationInfo.dataDir, "quests"), questName)
        val main = parse(questRoot).core["main"]
        bars = IntStream.range(0, main.bars.size).mapToObj { i ->
            val pair = main.bars[i] to barViews[i]
            pair.apply{
                second.progress = first.value.toFloat()
                second.setDrawable(first.image)
            }
        }.collect(Collectors.toMap(
            { it.first },
            { it.second }
        ))
        applyChoice(main.choice)
        pendingCard!!.also {
            preloadCard(it)
            loadCard(it)
        }
    }

    private fun initPullDispatcher() {
        dispatcher = PullDispatcher(
            findViewById(R.id.pull_area),
            cardView,
            cardView.width.toFloat(),
            cardView.height.toFloat()
        )
        dispatcher.addOnPullListeners(
            RotatePullAnimator(30f),
            MovePullAnimator(1f, 2f),
            { event ->
                answer.rotation = -cardView.rotation
                if (cardReleased) {
                    cardReleased = false
                    startAnswerAlphaAnimation(1f)
                }
                (if (event.dx < 0f) 0 else 1).takeIf { currentShownChoice != it }?.also {
                    currentShownChoice = it
                    answer.text = currentCard.choices[it].title
                }
            }
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
                    ) { // On fly animation end
                        when (it) {
                            FlyAwayPullAnimator.ANIMATION_FLEW_AWAY -> {
                                preloadCard(pendingCard!!)
                                soundPlayer.play(soundPlayer.soundCardArrive, 1f, 1f)
                            }
                            FlyAwayPullAnimator.ANIMATION_ARRIVED -> {
                                loadCard(pendingCard!!)
                                startTextsAppearing()
                            }
                        }
                    },
                    ChoiceHandler(this)
                ),
                listOf( // On return
                    ReleasePullAnimator(250)
                )
            ) { event -> abs(event.dx) >= event.rangeHorizontal / 2 },
            {
                cardReleased = true
                currentShownChoice = null
                startAnswerAlphaAnimation(0f)
            }
        )
    }

    private fun initAnimators() {
        fadeAppearAnimator.addUpdateListener {
            val value = it.animatedValue as Float
            text.alpha = value
            actor.alpha = value
        }
        fadeAppearAnimator.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                dispatcher.unlock(this@GameActivity)
            }
        })
        fadeAppearAnimator.duration = DURATION_TEXT_APPEARING
        fadeAppearAnimator.interpolator = AccelerateInterpolator()

        answerAnimator.addUpdateListener {
            answerContainer.alpha = it.animatedValue as Float
        }
        answerAnimator.duration = DURATION_ANSWER_APPEARING
        answerAnimator.interpolator = LinearInterpolator()
    }

    private fun initViews() {
        barViews = arrayOf(
            findViewById(R.id.bar1),
            findViewById(R.id.bar2),
            findViewById(R.id.bar3),
            findViewById(R.id.bar4)
        )
        eventBackground = findViewById(R.id.event_background)
        answer = findViewById(R.id.answer)
        answerContainer = findViewById(R.id.answer_shadow)
        text = findViewById(R.id.text)
        actor = findViewById(R.id.actor)
        name = findViewById(R.id.self_name)
        counter = findViewById(R.id.counter)
        counterDetail = findViewById(R.id.counter_detail)
        cardView = findViewById(R.id.event_card)
        image = findViewById(R.id.cardImage)
    }

    private fun parse(root: File) = Parser.load(root)

    /* Card updates */

    private fun onLastCardLoaded() {
        dispatcher.lock(object: Lock {}) // won't be unlocked after

        fun calculateGradient(@ColorInt from: Int, @ColorInt to: Int, fraction: Float): Int {
            val colors = Array(2) { j ->
                val clr = eventBackgroundColors.let { if (j == 0) from else to }.let {
                    if (it < 0) Integer.MAX_VALUE + it.toLong() - Int.MIN_VALUE else it.toLong()
                }
                IntArray(4) { i ->
                    (clr / 0x100.toDouble().pow(i.toDouble()) % 0x100).toInt()
                }
            }
            return IntStream.range(0, colors[0].size).map {
                    i -> round((colors[1][i] - colors[0][i]) * fraction).toInt() + colors[0][i]
            }.toArray().let { arr ->
                IntStream.range(0, arr.size)
                    .mapToLong { (arr[it] * 0x100.toDouble().pow(it)).toLong() }
                    .reduce(Long::plus).asLong.toInt()
            }
        }

        ValueAnimator().apply {
            duration = DURATION_BACKGROUND_CHANGING
            setFloatValues(1f)
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                eventBackground.setBackgroundColor(
                    calculateGradient(eventBackgroundColors.first, eventBackgroundColors.second, value)
                )
            }
            start()
        }

        text.setTextColor(eventBackgroundColors.first)
        actor.setTextColor(eventBackgroundColors.first)
    }

    /**
     * Apply affects (with animation) and prepare next card to be loaded
     */
    private fun applyChoice(choice: Choice) {
        val affects = HashMap<BarView, Pair<Float, Float>>()
        choice.affects.forEach { (bar, affect) ->
            val barView = bars[bar]!!
            val targetProgress = if (affect.type == Choice.Affect.Type.EXPLICIT)
                affect.value.toFloat()
            else
                maxOf(minOf(barView.progress + affect.value.toFloat(), 1f), 0f)
            affects[barView] = barView.progress to targetProgress
        }
        val animator = BarsAffectAnimator(affects)
        dispatcher.lock(animator)
        animator.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                dispatcher.unlock(animator)
            }
        })
        animator.start()
        pendingCard = choice.cards.let { it[rand.nextInt(it.size)] }
    }

    private fun preloadCard(card: Card) {
        currentCard = card
        Arrays.stream(card.picture.layers).forEach { it.accept(cardCanvas) } // drawing
        if (currentCard.choices.size != 2)
            onLastCardLoaded()
    }

    private fun loadCard(card: Card) {
        text.text = card.text
        actor.text = card.title
    }

    private fun cancelIfAnimation(animator: ValueAnimator) {
        if (animator.isRunning) {
            animator.pause()
            animator.cancel()
        }
    }

    private fun startAnswerAlphaAnimation(alpha: Float) {
        cancelIfAnimation(answerAnimator)
        answerAnimator.setFloatValues(answerAnimator.animatedValue as Float? ?: 0f, alpha)
        answerAnimator.start()
    }

    private fun startTextsFading() {
        cancelIfAnimation(fadeAppearAnimator)
        fadeAppearAnimator.setFloatValues(1f, 0f)
        fadeAppearAnimator.start()
    }

    private fun startTextsAppearing() {
        dispatcher.lock(this)
        cancelIfAnimation(fadeAppearAnimator)
        fadeAppearAnimator.setFloatValues(0f, 1f)
        fadeAppearAnimator.start()
    }
}

private class BarsAffectAnimator(
    private val affects: Map<BarView, Pair<Float, Float>>
): ValueAnimator(), AnimatorUpdateListener, Lock {
    init {
        duration = 250
        setFloatValues(0f, 1f)
        addUpdateListener(this)
        interpolator = DecelerateInterpolator()
    }

    override fun onAnimationUpdate(animator: ValueAnimator) {
        val percent = animator.animatedValue as Float
        affects.forEach { (bar, startToTarget) ->
            bar.progress = startToTarget.first + (startToTarget.second - startToTarget.first) * percent
        }
    }
}

private class SoundPlayer(context: Context, maxStreams: Int) { // on its own checks whether sound is enabled
    private val soundPool = SoundPool.Builder().setMaxStreams(maxStreams).build()

    var soundCardFlyAway: Int = -1
        private set
    var soundCardArrive: Int = -1
        private set

    val isSoundEnabled = SettingsShared(context).isSoundEnabled

    init {
        if (isSoundEnabled) {
            soundCardFlyAway = soundPool.load(context, R.raw.click, 1)
            soundCardArrive = soundPool.load(context, R.raw.paper_turn, 1)
        }
    }

    fun play(sound: Int, lVol: Float, rVol: Float) {
        if (isSoundEnabled)
            soundPool.play(sound, lVol, rVol, 1, 0 /* no loop */, 1f /* speed */)
    }
}