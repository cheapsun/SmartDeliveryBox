package com.example.deliverybox

import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class AddSharedUserActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var boxId: String
    private lateinit var adapter: SharedUserAdapter
    private val sharedUsers = mutableListOf<Pair<String, String>>() // (uid, email)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_shared_user)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.et_shared_email)
        val btnAdd = findViewById<Button>(R.id.btn_add_shared_user)
        val recyclerView = findViewById<RecyclerView>(R.id.shared_user_recyclerview)

        boxId = intent.getStringExtra("boxId") ?: return
        val uid = auth.currentUser?.uid ?: return

        val boxRef = db.collection("boxes").document(boxId)

        boxRef.get().addOnSuccessListener { doc ->
            val ownerUid = doc.getString("ownerUid")
            if (uid != ownerUid) {
                Toast.makeText(this, "공유 사용자 추가 권한이 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }

            adapter = SharedUserAdapter(sharedUsers) { sharedUid ->
                removeSharedUser(sharedUid)
            }

            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = adapter

            loadSharedUsers()

            btnAdd.setOnClickListener {
                val email = etEmail.text.toString().trim()
                if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, "올바른 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 먼저 박스 문서에서 sharedUserEmails 확인
                boxRef.get().addOnSuccessListener { boxDoc ->
                    val existingEmails = boxDoc.get("sharedUserEmails") as? List<*> ?: listOf<Any>()

                    if (existingEmails.contains(email)) {
                        Toast.makeText(this, "이미 등록된 이메일입니다.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // 사용자 UID 조회
                    db.collection("users").whereEqualTo("email", email).get()
                        .addOnSuccessListener { snapshot ->
                            if (snapshot.isEmpty) {
                                Toast.makeText(this, "해당 이메일의 사용자가 없습니다.", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            val sharedUid = snapshot.documents[0].id

                            // 이메일, UID 모두 추가
                            boxRef.update(
                                mapOf(
                                    "sharedUserUids" to FieldValue.arrayUnion(sharedUid),
                                    "sharedUserEmails" to FieldValue.arrayUnion(email)
                                )
                            ).addOnSuccessListener {
                                Toast.makeText(this, "공유 사용자 추가 완료", Toast.LENGTH_SHORT).show()
                                etEmail.text.clear()
                                loadSharedUsers()
                            }.addOnFailureListener {
                                Toast.makeText(this, "추가 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "사용자 조회 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }

    private fun loadSharedUsers() {
        sharedUsers.clear()
        val boxRef = db.collection("boxes").document(boxId)
        boxRef.get().addOnSuccessListener { boxDoc ->
            val uids = boxDoc.get("sharedUserUids") as? List<*> ?: listOf<String>()
            if (uids.isEmpty()) {
                adapter.notifyDataSetChanged()
                return@addOnSuccessListener
            }

            db.collection("users").whereIn(FieldPath.documentId(), uids).get()
                .addOnSuccessListener { result ->
                    result.documents.forEach { doc ->
                        val uid = doc.id
                        val email = doc.getString("email") ?: ""
                        sharedUsers.add(uid to email)
                    }
                    adapter.notifyDataSetChanged()
                }
        }
    }

    private fun removeSharedUser(uid: String) {
        val boxRef = db.collection("boxes").document(boxId)

        // UID로 이메일 조회 후 이메일도 함께 삭제
        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val email = userDoc.getString("email")

            val updates = mutableMapOf<String, Any>(
                "sharedUserUids" to FieldValue.arrayRemove(uid)
            )
            if (!email.isNullOrEmpty()) {
                updates["sharedUserEmails"] = FieldValue.arrayRemove(email)
            }

            boxRef.update(updates).addOnSuccessListener {
                Toast.makeText(this, "삭제 완료", Toast.LENGTH_SHORT).show()
                loadSharedUsers()
            }
        }
    }
}
