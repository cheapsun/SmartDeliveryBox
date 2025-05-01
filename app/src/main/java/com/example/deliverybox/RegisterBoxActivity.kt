package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.databinding.ActivityRegisterBoxBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.UUID

class RegisterBoxActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBoxBinding
    private val db   by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBoxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔁 툴바 뒤로가기 버튼 → MainActivity로 복귀 처리
        binding.toolbarRegisterBox.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        /* 단일 버튼: 코드가 있으면 claim, 없으면 새 박스 생성 */
        binding.btnRegisterOrClaim.setOnClickListener {
            val code  = binding.etBoxCode.text.toString().trim().uppercase()
            val alias = binding.etBoxAlias.text.toString().trim()

            if (code.isNotEmpty()) {
                claimBox(code, alias)    // 코드 우선 실행
            } else {
                createNewBox(alias)      // 코드 없으면 새 박스
            }
        }
    }

    /** ← 시스템 뒤로가기 버튼 처리: 앱 종료 방지 */
    override fun onBackPressed() {
        if (isTaskRoot) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else {
            super.onBackPressed()
        }
    }

    /** 코드 등록 */
    private fun claimBox(code: String, alias: String) {
        val uid = auth.currentUser?.uid ?: return
        val codeRef = db.collection("boxCodes").document(code)

        db.runTransaction { tx ->
            val snap = tx.get(codeRef)
            if (!snap.exists())        throw Exception("존재하지 않는 코드입니다.")
            if (snap.getBoolean("active") == false) throw Exception("이미 사용된 코드입니다.")
            val boxId = snap.getString("boxId") ?: throw Exception("boxId 없음")

            tx.update(codeRef, "active", false)

            val boxRef = db.collection("boxes").document(boxId)
            tx.set(
                boxRef,
                mapOf(
                    "members.$uid" to "owner",        // owner로 추가
                    "ownerUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            // 별칭은 사용자가 원하면 저장 (빈값이면 건너뜀)
            val userRef = db.collection("users").document(uid)
            val userData = mutableMapOf<String, Any>("mainBoxId" to boxId)
            if (alias.isNotEmpty()) userData["boxAliases.$boxId"] = alias
            tx.set(userRef, userData, SetOptions.merge())
            boxId
        }.addOnSuccessListener { boxId ->
            Toast.makeText(this, "박스($boxId) 등록 완료!", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, it.message ?: "등록 실패", Toast.LENGTH_SHORT).show()
        }
    }

    /** 새 박스 생성 (코드 입력 안 한 경우) */
    private fun createNewBox(alias: String) {
        if (alias.isEmpty()) {
            binding.etBoxAlias.error = "새 박스를 만들려면 별칭이 필요합니다"
            return
        }

        val uid   = auth.currentUser?.uid ?: return
        val boxId = UUID.randomUUID().toString()

        val batch = db.batch()

        // boxes 문서 생성
        val boxRef = db.collection("boxes").document(boxId)
        batch.set(
            boxRef,
            mapOf(
                "ownerUid" to uid,
                "members"  to mapOf(uid to "owner"),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        // users 문서 갱신
        val userRef = db.collection("users").document(uid)
        batch.set(
            userRef,
            mapOf(
                "mainBoxId" to boxId,
                "boxAliases" to mapOf(boxId to alias)
            ),
            SetOptions.merge()
        )

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "새 박스 생성 완료!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "생성 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
