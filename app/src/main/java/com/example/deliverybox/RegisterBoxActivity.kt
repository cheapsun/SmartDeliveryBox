package com.example.deliverybox

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityRegisterBoxBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterBoxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBoxBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBoxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarRegisterBox.setNavigationOnClickListener {
            finish()
        } else {
            )
            .addOnSuccessListener {
                finish()
            }
            .addOnFailureListener {
            }
    }
}
