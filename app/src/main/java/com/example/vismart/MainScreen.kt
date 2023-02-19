package com.example.vismart

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout

class MainScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_screen)

        val mfs_but = findViewById<Button>(R.id.mfs_button);

        mfs_but.setOnClickListener{

            //lets get the text from the edit text here
            var txtbox = findViewById<EditText>(R.id.prod_name)

            val intent = Intent(this, MainFindScreen::class.java);
            intent.putExtra("prod_name", txtbox.text.toString())
            startActivity(intent);
        }
    }


}