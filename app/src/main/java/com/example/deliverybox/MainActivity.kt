package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var boxId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnGoAddUser = findViewById<Button>(R.id.btn_go_add_user)
        val btnRegisterPackage = findViewById<Button>(R.id.btn_go_register_package)

        // 🔧 [추가] 택배 리스트 보기 버튼
        val btnGoPackageList = findViewById<Button>(R.id.btn_go_package_list)

        // 공유 사용자 버튼은 기본적으로 숨김 처리
        btnGoAddUser.visibility = View.GONE

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 사용자 문서에서 boxId 조회
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                boxId = userDoc.getString("boxId") ?: run {
                    Toast.makeText(this, "택배함 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 해당 박스의 소유자인 경우에만 공유 사용자 버튼 표시
                db.collection("boxes").document(boxId).get()
                    .addOnSuccessListener { boxDoc ->
                        val ownerUid = boxDoc.getString("ownerUid")

                        if (uid == ownerUid) {
                            btnGoAddUser.visibility = View.VISIBLE
                            btnGoAddUser.setOnClickListener {
                                val intent = Intent(this, AddSharedUserActivity::class.java)
                                intent.putExtra("boxId", boxId)
                                startActivity(intent)
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "박스 정보 불러오기 실패", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "사용자 정보 불러오기 실패", Toast.LENGTH_SHORT).show()
            }

        // 택배 등록 화면으로 이동
        btnRegisterPackage.setOnClickListener {
            val intent = Intent(this, RegisterPackageActivity::class.java)
            startActivity(intent)
        }

        // 🔧 [추가] 택배 리스트 보기 화면으로 이동
        btnGoPackageList.setOnClickListener {
            val intent = Intent(this, PackageListActivity::class.java)
            startActivity(intent)
        }
    }
}
