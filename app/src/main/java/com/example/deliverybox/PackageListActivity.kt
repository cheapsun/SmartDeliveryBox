package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PackageListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PackageListAdapter
    private val packageList = mutableListOf<PackageItem>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var boxId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_list)

        recyclerView = findViewById(R.id.rv_package_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadUserBoxIdAndPackages()
    }

    private fun loadUserBoxIdAndPackages() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    boxId = document.getString("boxId") ?: return@addOnSuccessListener
                    loadPackagesFromBox(boxId)
                }
                .addOnFailureListener { e ->
                    Log.e("PackageListActivity", "Failed to load user data", e)
                }
        }
    }

    private fun loadPackagesFromBox(boxId: String) {
        db.collection("boxes")
            .document(boxId)
            .collection("packages")
            .get()
            .addOnSuccessListener { result ->
                packageList.clear()
                for (document in result) {
                    val item = PackageItem(
                        trackingNumber = document.getString("trackingNumber") ?: "",
                        courierCompany = document.getString("courierCompany") ?: "",
                        info = document.getString("info") ?: "",
                        category = document.getString("category") ?: "",
                        origin = document.getString("origin") ?: "",
                        createdAt = document.getLong("createdAt") ?: 0L,
                        docId = document.id
                    )
                    packageList.add(item)
                }

                // 어댑터 생성 (boxId 및 onItemClick 전달)
                adapter = PackageListAdapter(packageList, boxId) { item, boxId ->
                    val intent = Intent(this, PackageEditActivity::class.java).apply {
                        putExtra("boxId", boxId)
                        putExtra("docId", item.docId)
                        putExtra("trackingNumber", item.trackingNumber)
                        putExtra("courierCompany", item.courierCompany)
                        putExtra("info", item.info)
                        putExtra("category", item.category)
                        putExtra("origin", item.origin)
                        putExtra("createdAt", item.createdAt)
                    }
                    startActivity(intent)
                }

                recyclerView.adapter = adapter
            }
            .addOnFailureListener { e ->
                Log.e("PackageListActivity", "Failed to load packages", e)
            }
    }
}
