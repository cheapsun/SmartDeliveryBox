package com.example.deliverybox.delivery

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.delivery.adapter.PackageAdapter
import com.example.deliverybox.delivery.adapter.PackageItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PackageListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private lateinit var adapter: PackageAdapter
    private val packageList = mutableListOf<PackageItem>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var boxId: String

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("PackageList", "📦 수정/삭제 결과 수신 → 새로고침")
            loadPackages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_list)

        recyclerView = findViewById(R.id.recycler_view_packages)
        emptyMessage = findViewById(R.id.tv_empty_message)

        recyclerView.layoutManager = LinearLayoutManager(this)

        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { userDoc ->
                    boxId = userDoc.getString("boxId") ?: return@addOnSuccessListener
                    loadPackages()
                }
        }
    }

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

                    // Package를 PackageInfo로 변환 ✅
                    val packageInfo = convertToPackageInfo(pkg, id)
                    packageList.add(PackageItem(id, packageInfo))
                }

                if (packageList.isEmpty()) {
                    emptyMessage.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyMessage.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    adapter = PackageAdapter(
                        onItemClick = { selectedItem ->
                            val intent = Intent(this, PackageEditActivity::class.java).apply {
                                putExtra("boxId", boxId)
                                putExtra("packageId", selectedItem.id)
                            }
                            editLauncher.launch(intent)
                        },
                        onStatusChange = { packageItem, newStatus ->
                            // 상태 변경 로직 (선택사항)
                            updatePackageStatus(packageItem.id, newStatus)
                        },
                        onDeleteClick = { packageItem ->
                            // 삭제 로직 (선택사항)
                            deletePackage(packageItem.id)
                        }
                    )

                    adapter.submitList(packageList.toList()) // ✅ submitList 사용
                    recyclerView.adapter = adapter
                }
            }
            .addOnFailureListener {
                Log.e("PackageList", "🔥 패키지 불러오기 실패: ${it.message}")
                emptyMessage.text = "데이터를 불러올 수 없습니다."
                emptyMessage.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    /**
     * Package 객체를 PackageInfo 객체로 변환
     */
    private fun convertToPackageInfo(pkg: Package, documentId: String): PackageInfo {
        return PackageInfo(
            id = documentId,
            trackingNumber = pkg.trackingNumber,
            courierCompany = pkg.courierCompany,
            itemName = pkg.info.takeIf { it.isNotEmpty() }, // info를 itemName으로 매핑
            category = pkg.category,
            memo = null, // Package에는 memo 필드가 없음
            origin = pkg.origin,
            destination = "", // Package에는 destination 필드가 없음
            status = DeliveryStatus.REGISTERED, // 기본값 설정
            deliverySteps = emptyList(), // 기본값
            registeredAt = pkg.createdAt,
            registeredBy = pkg.registeredBy,
            boxId = boxId,
            lastUpdated = pkg.createdAt,
            isDelivered = false, // 기본값
            deliveredAt = null,
            estimatedDelivery = null,
            isAutoDetected = false, // 기본값
            confidence = 1.0f // 기본값
        )
    }

    /**
     * 패키지 상태 업데이트 (선택사항)
     */
    private fun updatePackageStatus(packageId: String, newStatus: DeliveryStatus) {
        db.collection("boxes").document(boxId)
            .collection("packages").document(packageId)
            .update("status", newStatus.name)
            .addOnSuccessListener {
                Log.d("PackageList", "상태 업데이트 성공: $newStatus")
                loadPackages() // 새로고침
            }
            .addOnFailureListener { e ->
                Log.e("PackageList", "상태 업데이트 실패: ${e.message}")
            }
    }

    /**
     * 패키지 삭제 (선택사항)
     */
    private fun deletePackage(packageId: String) {
        db.collection("boxes").document(boxId)
            .collection("packages").document(packageId)
            .update("valid", false) // 소프트 삭제
            .addOnSuccessListener {
                Log.d("PackageList", "패키지 삭제 성공")
                loadPackages() // 새로고침
            }
            .addOnFailureListener { e ->
                Log.e("PackageList", "패키지 삭제 실패: ${e.message}")
            }
    }
}