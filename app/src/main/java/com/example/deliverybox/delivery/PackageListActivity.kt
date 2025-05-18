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
                    packageList.add(PackageItem(id, pkg))
                }

                if (packageList.isEmpty()) {
                    emptyMessage.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyMessage.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    adapter = PackageAdapter(packageList.toList()) { selectedItem ->
                        val intent = Intent(this, PackageEditActivity::class.java).apply {
                            putExtra("boxId", boxId)
                            putExtra("packageId", selectedItem.id)
                        }
                        editLauncher.launch(intent)
                    }

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
}
