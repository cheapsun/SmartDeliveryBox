package com.example.deliverybox.ui.box

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.R

class BoxDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_box_detail)

        val boxName = intent.getStringExtra("boxName") ?: "이름 없음"

        val tvBoxName = findViewById<TextView>(R.id.tv_box_name_detail)
        tvBoxName.text = boxName
    }
}
