package com.example.deliverybox

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityRegisterBoxBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterBoxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBoxBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBoxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 툴바 뒤로가기 버튼 처리
        binding.toolbarRegisterBox.setNavigationOnClickListener {
            finish()
        }

        // 등록 버튼 클릭
        binding.btnRegisterBox.setOnClickListener {
            val boxId = binding.etBoxId.text.toString().trim()
            val alias = binding.etBoxAlias.text.toString().trim()

            if (boxId.isEmpty() || alias.isEmpty()) {
                Toast.makeText(this, "모든 항목을 입력해주세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            checkAndRegisterBox(boxId, alias)
        }
    }

    private fun checkAndRegisterBox(boxId: String, alias: String) {
        val userUid = auth.currentUser?.uid ?: return

        db.collection("boxes").document(boxId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    Toast.makeText(this, "이미 등록된 택배함입니다", Toast.LENGTH_SHORT).show()
                } else {
                    val boxData = mapOf(
                        "boxName" to boxId,
                        "ownerUid" to userUid,
                        "sharedUserUids" to listOf<String>()
                    )
                    db.collection("boxes").document(boxId).set(boxData)
                        .addOnSuccessListener {
                            val aliasMap = mapOf("boxAliases.$boxId" to alias)
                            db.collection("users").document(userUid).update(aliasMap)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "택배함 등록 완료!", Toast.LENGTH_SHORT).show()
                                    setResult(RESULT_OK)
                                    finish()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "별칭 저장 실패", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "택배함 등록 실패", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "중복 확인 실패", Toast.LENGTH_SHORT).show()
            }
    }
}
