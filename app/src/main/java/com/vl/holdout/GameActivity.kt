package com.vl.holdout

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.Drawable
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
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vl.barview.BarView
import com.vl.holdout.parser.Parser
import com.vl.holdout.parser.pojo.Bar
import com.vl.holdout.parser.pojo.Card
import com.vl.holdout.parser.pojo.Choice
import com.vl.holdout.parser.pojo.Core
import com.vl.holdout.pull.*
import java.io.File
import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.streams.toList

/**
 * Lifecycle (Hide-Update-Show):
 * 1. Card is released (swiped by user)
 * 2. Texts faded
 * 3. Card flew away
 * 4. Texts is updated
 * 5. Card image is updated
 * 6. Card flew backward
 * 7. Texts appeared
 */
@SuppressLint("ClickableViewAccessibility")
class GameActivity: AppCompatActivity(), ChoiceHandler.OnChoiceListener, Lock {
    companion object {
        const val DURATION_ANSWER_APPEARING = 250L
        const val DURATION_TEXT_APPEARING = 250L
        const val DURATION_BACKGROUND_CHANGING = 500L
    }

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

    private val fadeAppearAnimator = ValueAnimator()
    private val answerAnimator = ValueAnimator()
    private val backgroundAnimator = ValueAnimator()

    private lateinit var soundPlayer: SoundPlayer
    private lateinit var stateHolder: GameStateHolder

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
        stateHolder = ViewModelProvider(this, GameStateHolderFactory(
            QuestRepository(applicationContext, intent.getStringExtra("quest")!!)
        ))[GameStateHolder::class.java]
        stateHolder.uiState.observe(this) {
            when (it) {
                is GameStateHolder.UiState.Error -> onError(it)
                is GameStateHolder.UiState.UpdateState -> update(it)
                is GameStateHolder.UiState.NavigateToMenu -> {
                    startActivity(Intent(this, MenuActivity::class.java))
                    finish()
                }
            }
        }
        stateHolder.isColorsInverted.observe(this, this::updateColors)
        for (i in barViews.indices)
            barViews[i].setDrawable(stateHolder.barDrawables[i])
        cardView.post {
            initPullDispatcher()
            stateHolder.areTextsVisible.observe(this) {
                if (it)
                    startTextsAppearing()
                else
                    startTextsFading()
            }
        }
    }

    override fun onBackPressed() {
        startActivity(Intent(this, MenuActivity::class.java))
        finish()
    }

    override fun onChoice(choiceId: Int) {
        soundPlayer.play(
            soundPlayer.soundCardFlyAway,
            if (choiceId == ChoiceHandler.CHOICE_LEFT) 1f else 0.5f,
            if (choiceId == ChoiceHandler.CHOICE_RIGHT) 1f else 0.5f
        )
        stateHolder.onChoice(stateHolder.availableChoices.let {
            when (choiceId) {
                ChoiceHandler.CHOICE_LEFT -> it[0]
                ChoiceHandler.CHOICE_RIGHT -> it[1]
                else -> throw RuntimeException() // unreachable
            }
        })
    }

    /* UI updates */

    private fun onError(error: GameStateHolder.UiState.Error) {
        dispatcher.lock(this)
        AlertDialog.Builder(this)
            .setTitle("Ошибка")
            .setMessage(error.message)
            .show()
    }

    private fun update(state: GameStateHolder.UiState.UpdateState) {
        actor.text = state.title
        text.text = state.text
        image.setImageBitmap(state.image)
        BarsAffectAnimator(this,
            IntStream.range(0, barViews.size)
                .mapToObj { i ->
                    barViews[i] to (barViews[i].progress to state.barValues[i].toFloat())
                }.filter { it.second.first != it.second.second }
                .collect(Collectors.toMap ({ it.first }, { it.second }))
        ).start()
    }

    private fun updateColors(inverted: Boolean) {
        listOf(actor, text).forEach {
            it.setTextColor(
                getColor(
                    if (inverted)
                        R.color.kinda_light_brown
                    else
                        R.color.kinda_dark_brown
                )
            )
        }
        backgroundAnimator.run {
            setFloatValues(*(if (inverted) floatArrayOf(0f, 1f) else floatArrayOf(1f, 0f)))
            start()
        }
    }

    /* Initialization */

    private fun resolveValues() {
        val typed = TypedValue()
        fun resolveInt(@AttrRes attr: Int) = typed.let {
            theme.resolveAttribute(attr, it, true)
            it.data
        }
        eventBackgroundColors =
            resolveInt(com.google.android.material.R.attr.colorSecondary) to
            resolveInt(android.R.attr.windowBackground)
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
                    answer.text = stateHolder.availableChoices[it].hint
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
                                stateHolder.update()
                                soundPlayer.play(soundPlayer.soundCardArrive, 1f, 1f)
                            }
                            FlyAwayPullAnimator.ANIMATION_ARRIVED -> {
                                stateHolder.onShown()
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
        fadeAppearAnimator.run {
            duration = DURATION_TEXT_APPEARING
            interpolator = AccelerateInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Float
                text.alpha = value
                actor.alpha = value
            }
            addListener(object: AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dispatcher.unlock(this@GameActivity)
                }
            })
        }
        answerAnimator.run {
            duration = DURATION_ANSWER_APPEARING
            interpolator = LinearInterpolator()
            addUpdateListener {
                answerContainer.alpha = it.animatedValue as Float
            }
        }
        backgroundAnimator.run {
            duration = DURATION_BACKGROUND_CHANGING
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                eventBackground.setBackgroundColor(
                    calculateGradient(eventBackgroundColors.first, eventBackgroundColors.second, value)
                )
            }
        }
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

    /* Animations */

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
    private val context: Context,
    private val affects: Map<BarView, Pair<Float, Float>>
): ValueAnimator(), AnimatorUpdateListener, Lock {
    init {
        duration = 1000
        setFloatValues(0f, 1f)
        addUpdateListener(this)
        interpolator = DecelerateInterpolator()
    }

    override fun onAnimationUpdate(animator: ValueAnimator) {
        val percent = animator.animatedValue as Float
        affects.forEach { (barView, startToTarget) ->
            barView.progress = startToTarget.first + (startToTarget.second - startToTarget.first) * percent
            barView.setActiveColor(calculateGradient(
               context.getColor(if (startToTarget.first > startToTarget.second) R.color.red else R.color.green),
                context.getColor(R.color.kinda_light_brown), percent)
            )
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

private fun calculateGradient(@ColorInt from: Int, @ColorInt to: Int, fraction: Float): Int {
    val colors = Array(2) { j ->
        val clr = (if (j == 0) from else to).let {
            if (it < 0) (Int.MAX_VALUE.toLong() + it - Int.MIN_VALUE + 1) else it.toLong()
        }
        LongArray(4) { i ->
            (clr / 0x100.toDouble().pow(i.toDouble()) % 0x100).toLong()
        }
    }
    return IntStream.range(0, colors[0].size).mapToLong {
            i -> round((colors[1][i] - colors[0][i]) * fraction).toLong() + colors[0][i]
    }.toArray().let { arr ->
        IntStream.range(0, arr.size)
            .mapToLong { (arr[it] * 0x100.toDouble().pow(it)).toLong() }
            .reduce(Long::plus).asLong.toInt()
    }
}

private class GameStateHolderFactory(
    private val questRepository: QuestRepository
): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (GameStateHolder::class.java.isAssignableFrom(modelClass))
            GameStateHolder(questRepository) as T
        else throw IllegalArgumentException("${modelClass.name} class is not supported")
}

private class GameStateHolder(private val questRepository: QuestRepository): ViewModel() { // TODO pass only quest name (for catching error state on parsing)
    companion object {
        private object MenuChoice: QuestRepository.ChoiceWrapper {
            override val hint = "Главное меню"
        }

        private object PlayAgainChoice: QuestRepository.ChoiceWrapper {
            override val hint = "Ещё раз"
        }

        const val IMAGE_SIZE = 720
        val gameOverChoices: Array<QuestRepository.ChoiceWrapper> =
            arrayOf(MenuChoice, PlayAgainChoice)
    }

    val areTextsVisible = MutableLiveData<Boolean>()
    val isColorsInverted = MutableLiveData(false)
    val uiState = MutableLiveData<UiState>()

    val barDrawables: Array<Drawable> =
        Arrays.stream(questRepository.bars).map { it.drawable }.toList().toTypedArray()
    val availableChoices: Array<QuestRepository.ChoiceWrapper>
        get() =  if (isCardLast) gameOverChoices else card.choices

    private val isCardLast: Boolean
        get() = card.choices.size != 2
    private var card = questRepository.firstCard
    private val bitmap = Bitmap.createBitmap(IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888) // TODO A/B buffering
    private val canvas = Canvas(bitmap)


    init { update() }

    fun onChoice(choice: QuestRepository.ChoiceWrapper) { // card is swiped away
        areTextsVisible.value = false
        when (choice) {
            is MenuChoice -> uiState.value = UiState.NavigateToMenu
            is PlayAgainChoice -> {
                card = questRepository.firstCard
                questRepository.resetBars()
            }
            else -> card = questRepository.applyChoice(choice)
        }
    }

    fun update() { // actually called when card has flown away
        card.draw(canvas) // TODO move from main thread
        uiState.value = UiState.UpdateState(
            card.title,
            card.text,
            bitmap,
            Arrays.stream(questRepository.bars).mapToDouble { it.value }.toArray()
        )
        if (isColorsInverted.value != isCardLast)
            isColorsInverted.value = isCardLast
    }

    fun onShown() { // actually called when card has flown backward
        areTextsVisible.value = true
    }

    sealed interface UiState {
        class Error(val message: String): UiState

        class UpdateState(
            val title: String,
            val text: String,
            val image: Bitmap,
            val barValues: DoubleArray
        ): UiState

        object NavigateToMenu: UiState
    }
}

private class QuestRepository(
    context: Context, // app context
    name: String
) {
    companion object {
        private val rand = Random(System.currentTimeMillis())
        private fun chooseCard(choice: Choice) = choice.cards.let { it[rand.nextInt(it.size)] } // TODO check triggers
        private fun (ChoiceWrapper).unwrap() = (this as ChoiceWrapperImpl).choice
        private fun (BarWrapper).unwrap() = (this as BarWrapperImpl).bar
    }

    private val quest: Core =
        Parser.load(
            File(
                File(
                    context.applicationInfo.dataDir,
                    "quests"
                ),
                name
            )
        ).core["main"]

    val bars: Array<BarWrapper> = Arrays.stream(quest.bars).map(::BarWrapperImpl)
        .toList().toTypedArray()
    val firstCard: CardWrapper = CardWrapperImpl(chooseCard(quest.choice))

    fun applyChoice(choice: ChoiceWrapper): CardWrapper {
        choice.unwrap().affects.forEach { (bar, affect) ->
            val barWrapper = Arrays.stream(bars).filter { it.unwrap() == bar }.findAny().get()
            when (affect.type) {
                Choice.Affect.Type.EXPLICIT -> barWrapper.value = affect.value
                Choice.Affect.Type.MASK -> barWrapper.value += affect.value
            }
        }
        return CardWrapperImpl(chooseCard(choice.unwrap()))
    }

    fun resetBars() {
        for (i in bars.indices)
            bars[i].value = quest.bars[i].value
    }

    sealed interface ChoiceWrapper {
        val hint: String
    }

    sealed interface CardWrapper {
        val title: String
        val text: String
        val choices: Array<ChoiceWrapper>
        fun draw(canvas: Canvas)
    }

    sealed interface BarWrapper {
        var value: Double
        val drawable: Drawable
    }

    private class ChoiceWrapperImpl(val choice: Choice): ChoiceWrapper {
        override val hint by choice::title
    }

    private class CardWrapperImpl(val card: Card): CardWrapper {
        override val title by card::title
        override val text by card::text
        override val choices: Array<ChoiceWrapper> =
            Arrays.stream(card.choices).map(::ChoiceWrapperImpl).toList().toTypedArray()
        override fun draw(canvas: Canvas) {
            card.picture.layers.forEach { it.accept(canvas) }
        }
    }

    private class BarWrapperImpl(val bar: Bar): BarWrapper {
        override var value = bar.value
        override val drawable by bar::image
    }
}