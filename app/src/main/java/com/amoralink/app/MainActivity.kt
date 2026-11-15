package com.amoralink.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val backgroundColor = Color.rgb(20, 20, 35)
    private val cardColor = Color.rgb(38, 38, 58)
    private val accentColor = Color.rgb(220, 120, 170)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 24)
            setBackgroundColor(backgroundColor)
        }

        root.addView(text("Amora Link", 32f, Color.WHITE))
        root.addView(text("Your AI Companion Connection", 14f, accentColor))
        root.addView(space(28))

        addCard(root, "AI Companions", "Manage your connected AI companions")
        addCard(root, "Usage Access", "Allow Amora Link to understand app usage")
        addCard(root, "Smart Reminders", "Receive helpful interaction reminders")
        addCard(root, "App Management", "Manage your companion app preferences")

        root.addView(space(24))
        root.addView(
            text("Your connections. Your choices. Your control.", 13f, Color.LTGRAY)
        )

        setContentView(root)
    }

    private fun addCard(root: LinearLayout, title: String, description: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(cardColor)
        }

        card.addView(text(title, 19f, Color.WHITE))
        card.addView(space(8))
        card.addView(text(description, 13f, Color.LTGRAY))

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = 14
        root.addView(card, params)
    }

    private fun text(value: String, size: Float, color: Int): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }
    }
}
