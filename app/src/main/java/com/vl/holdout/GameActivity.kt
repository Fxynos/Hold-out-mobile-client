package com.vl.holdout

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.vl.barview.BarView
import com.vl.holdout.pull.*
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class GameActivity: AppCompatActivity(), ChoiceHandler.OnChoiceListener {
    private lateinit var cardView: CardView
    private lateinit var text: TextView
    private lateinit var actor: TextView
    private lateinit var name: TextView
    private lateinit var counter: TextView
    private lateinit var counterDetail: TextView
    private lateinit var dispatcher: PullDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        init(savedInstanceState)
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

        demonstrate(findViewById(R.id.character_bar), 3000)
        demonstrate(findViewById(R.id.health_bar), 5000)
        demonstrate(findViewById(R.id.knowledge_bar), 1000)
        demonstrate(findViewById(R.id.wealth_bar), 8000)
    }

    override fun onChoice(choiceId: Int) = when (choiceId) {
        ChoiceHandler.CHOICE_LEFT, ChoiceHandler.CHOICE_RIGHT -> {
            Toast.makeText(
                this,
                "choice: ${if (choiceId == ChoiceHandler.CHOICE_LEFT) "left" else "right"}",
                Toast.LENGTH_SHORT
            ).show()
            //dispatcher.lock(object: Lock {})
            Unit
        }
        else -> throw IllegalArgumentException("unknown choice id")
    }

    private fun init(savedInstanceState: Bundle?) {
        text = findViewById(R.id.text)
        actor = findViewById(R.id.actor)
        name = findViewById(R.id.self_name)
        counter = findViewById(R.id.counter)
        counterDetail = findViewById(R.id.counter_detail)
        cardView = findViewById(R.id.event_card)
    }

    private fun demonstrate(view: BarView, duration: Long) {
        val anim = ValueAnimator.ofFloat(0f, 1f)
        anim.interpolator = LinearInterpolator()
        anim.duration = duration
        anim.repeatCount = ValueAnimator.INFINITE
        anim.repeatMode = ValueAnimator.REVERSE
        anim.addUpdateListener {
                value -> view.progress = value.animatedValue as Float
        }
        anim.start()
    }
}