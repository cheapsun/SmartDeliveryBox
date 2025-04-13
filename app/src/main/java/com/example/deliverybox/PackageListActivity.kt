package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.adapter.PackageAdapter
import com.example.deliverybox.model.Package
import com.example.deliverybox.model.PackageItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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

                adapter = PackageAdapter(packageList.toList()) { selectedItem ->
                    val intent = Intent(this, PackageEditActivity::class.java)
                    intent.putExtra("boxId", boxId)
                    intent.putExtra("packageId", selectedItem.id)
                    startActivity(intent)
                }

                recyclerView.adapter = adapter
            }
            .addOnFailureListener { e ->
                Log.e("PackageList", "패키지 불러오기 실패: $e")
            }
    }
}