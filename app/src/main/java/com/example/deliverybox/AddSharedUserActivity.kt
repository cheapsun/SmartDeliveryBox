package com.example.deliverybox

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class AddSharedUserActivity : AppCompatActivity() {
    private val TAG = "SharedUserLog"

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var boxId: String
    private lateinit var adapter: SharedUserAdapter
    private val sharedUsers = mutableListOf<Pair<String, String>>() // (uid or pending_key, email)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_shared_user)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val etEmail = findViewById<EditText>(R.id.et_shared_email)
        val btnAdd = findViewById<Button>(R.id.btn_add_shared_user)
        val recyclerView = findViewById<RecyclerView>(R.id.shared_user_recyclerview)

        val passedBoxId = intent.getStringExtra("boxId")
        if (passedBoxId.isNullOrEmpty()) {
            Toast.makeText(this, "boxId가 전달되지 않았습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        boxId = passedBoxId

        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val boxRef = db.collection("boxes").document(boxId)

        boxRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                Toast.makeText(this, "해당 boxId가 존재하지 않습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }

            val ownerUid = doc.getString("ownerUid")
            if (uid != ownerUid) {
                Toast.makeText(this, "공유 사용자 추가 권한이 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return@addOnSuccessListener
            }

            adapter = SharedUserAdapter(sharedUsers) { identifier ->
                removeSharedUser(identifier)
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

                boxRef.get().addOnSuccessListener { boxDoc ->
                    val existingEmails = boxDoc.get("sharedUserEmails") as? List<*> ?: listOf<Any>()
                    if (existingEmails.contains(email)) {
                        Toast.makeText(this, "이미 등록된 이메일입니다.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    boxRef.update("sharedUserEmails", FieldValue.arrayUnion(email))
                        .addOnSuccessListener {
                            Toast.makeText(this, "공유 이메일 등록 완료", Toast.LENGTH_SHORT).show()
                            etEmail.text.clear()
                            loadSharedUsers()
                        }.addOnFailureListener {
                            Toast.makeText(this, "추가 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }

    private fun loadSharedUsers() {
        Log.d(TAG, "공유 사용자 불러오는 중...")
        sharedUsers.clear()
        val boxRef = db.collection("boxes").document(boxId)

        boxRef.get().addOnSuccessListener { boxDoc ->
            val uids = (boxDoc.get("sharedUserUids") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val emails = (boxDoc.get("sharedUserEmails") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            Log.d(TAG, "공유 UID: $uids, 이메일: $emails")

            if (uids.isEmpty() && emails.isEmpty()) {
                sharedUsers.add("info" to "등록된 공유 사용자가 없습니다.")
                adapter.notifyDataSetChanged()
                return@addOnSuccessListener
            }

            val uidChunks = uids.chunked(10)
            var pending = uidChunks.size

            if (pending == 0) {
                emails.forEach { email ->
                    sharedUsers.add("pending_$email" to "$email (가입 대기)")
                }
                if (sharedUsers.isEmpty()) {
                    sharedUsers.add("info" to "등록된 공유 사용자가 없습니다.")
                }
                adapter.notifyDataSetChanged()
                return@addOnSuccessListener
            }

            uidChunks.forEach { chunk ->
                db.collection("users").whereIn(FieldPath.documentId(), chunk).get()
                    .addOnSuccessListener { result ->
                        result.documents.forEach { doc ->
                            val uid = doc.id
                            val email = doc.getString("email") ?: ""
                            sharedUsers.add(uid to email)
                        }

                        if (--pending == 0) {
                            emails.forEach { email ->
                                if (sharedUsers.none { it.second.startsWith(email) }) {
                                    sharedUsers.add("pending_$email" to "$email (가입 대기)")
                                }
                            }
                            if (sharedUsers.isEmpty()) {
                                sharedUsers.add("info" to "등록된 공유 사용자가 없습니다.")
                            }
                            adapter.notifyDataSetChanged()
                        }
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "UID 조각 쿼리 실패: ${it.message}", it)
                        if (--pending == 0) adapter.notifyDataSetChanged()
                    }
            }
        }
    }

    private fun removeSharedUser(identifier: String) {
        val boxRef = db.collection("boxes").document(boxId)

        if (identifier.startsWith("pending_")) {
            val email = identifier.removePrefix("pending_")
            boxRef.update("sharedUserEmails", FieldValue.arrayRemove(email))
                .addOnSuccessListener {
                    Toast.makeText(this, "이메일 삭제 완료", Toast.LENGTH_SHORT).show()
                    loadSharedUsers()
                }
        } else {
            db.collection("users").document(identifier).get().addOnSuccessListener { userDoc ->
                val email = userDoc.getString("email")
                val updates = mutableMapOf<String, Any>(
                    "sharedUserUids" to FieldValue.arrayRemove(identifier)
                )
                if (!email.isNullOrEmpty()) {
                    updates["sharedUserEmails"] = FieldValue.arrayRemove(email)
                }

                boxRef.update(updates).addOnSuccessListener {
                    Toast.makeText(this, "공유 사용자 삭제 완료", Toast.LENGTH_SHORT).show()
                    loadSharedUsers()
                }
            }
        }
    }
}
