package com.example.vismart

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

class HomeScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ms_but = findViewById<Button>(R.id.ms_but);

        ms_but.setOnClickListener {
            val intent = Intent(this, MainScreen::class.java);
            startActivity(intent);
        }
    }
}