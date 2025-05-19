package com.example.deliverybox.shareduser

import android.os.Bundle
import android.util.Patterns
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.R
import com.example.deliverybox.databinding.ActivityAddSharedUserBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddSharedUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddSharedUserBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentBoxId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSharedUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 툴바 뒤로가기
        binding.toolbarAddSharedUser.setNavigationOnClickListener {
            finish()
        }

        // 선택된 박스 ID 불러오기
        fetchSelectedBoxId()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_add_shared_user, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_send -> {
                val email = binding.etEmail.text.toString().trim()
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "올바른 이메일을 입력하세요.", Toast.LENGTH_SHORT).show()
                    false
                } else {
                    checkEmailAndAddUser(email)
                    true
                }
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchSelectedBoxId() {
        val userUid = auth.currentUser?.uid ?: return
        db.collection("users").document(userUid).get()
            .addOnSuccessListener { doc ->
                currentBoxId = doc.getString("selectedBoxId") ?: ""
                if (currentBoxId.isEmpty()) {
                    Toast.makeText(this, "선택된 택배함이 없습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "박스 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun checkEmailAndAddUser(email: String) {
        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "존재하지 않는 이메일입니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val targetUid = snapshot.documents.first().id
                db.collection("boxes").document(currentBoxId).get()
                    .addOnSuccessListener { boxDoc ->
                        val sharedList = boxDoc.get("sharedUserUids") as? List<*> ?: emptyList<String>()
                        if (sharedList.contains(targetUid)) {
                            Toast.makeText(this, "이미 추가된 사용자입니다.", Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        db.collection("boxes").document(currentBoxId)
                            .update("sharedUserUids", sharedList + targetUid)
                            .addOnSuccessListener {
                                Toast.makeText(this, "공유 사용자 추가 완료", Toast.LENGTH_SHORT).show()
                                setResult(RESULT_OK)
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "추가에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "이메일 확인 실패", Toast.LENGTH_SHORT).show()
            }
    }
}
