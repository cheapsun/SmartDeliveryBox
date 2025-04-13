package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.adapter.PackageAdapter
import com.example.deliverybox.model.Package
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// 문서 ID와 실제 패키지 데이터를 함께 담는 클래스
data class PackageItem(
    val id: String,
    val data: Package
)

class PackageListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PackageAdapter
    private val packageList = mutableListOf<PackageItem>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var boxId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_list)

        recyclerView = findViewById(R.id.recycler_view_packages)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 현재 로그인한 사용자 UID로부터 boxId 가져오기
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { userDoc ->
                    boxId = userDoc.getString("boxId") ?: return@addOnSuccessListener
                    loadPackages()
                }
                .addOnFailureListener { e ->
                    Log.e("PackageList", "사용자 정보 가져오기 실패: $e")
                }
        }
    }

    // 해당 boxId의 패키지 리스트 불러오기
    private fun loadPackages() {
        db.collection("boxes").document(boxId)
            .collection("packages")
            .whereEqualTo("valid", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                packageList.clear()
                for (document in result) {
                    val pkg = document.toObject(Package::class.java)
                    val id = document.id
                    packageList.add(PackageItem(id, pkg))
                }

                // 어댑터 연결 + 아이템 클릭 시 수정화면으로 이동
                adapter = PackageAdapter(packageList) { selectedItem ->
                    val intent = Intent(this, PackageEditActivity::class.java)
                    intent.putExtra("boxId", boxId)
                    intent.putExtra("packageId", selectedItem.id)  // 문서 ID 전달
                    startActivity(intent)
                }

                recyclerView.adapter = adapter
            }
            .addOnFailureListener { e ->
                Log.e("PackageList", "패키지 불러오기 실패: $e")
            }
    }
}
