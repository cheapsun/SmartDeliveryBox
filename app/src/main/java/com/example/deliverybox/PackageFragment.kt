package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.adapter.PackageAdapter
import com.example.deliverybox.model.Package
import com.example.deliverybox.model.PackageItem
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PackageFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyMessage: TextView
    private lateinit var adapter: PackageAdapter
    private lateinit var fabAddPackage: FloatingActionButton

    private val packageList = mutableListOf<PackageItem>()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var boxId: String = ""

    private val editLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Log.d("PackageFragment", "📦 수정/삭제 결과 수신 → 새로고침")
            loadPackages()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_package, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recycler_view_packages)
        emptyMessage = view.findViewById(R.id.tv_empty_message)
        fabAddPackage = view.findViewById(R.id.fab_add_package)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        setupFabClickListener()

        // 사용자 및 박스 ID 가져오기
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { userDoc ->
                    boxId = userDoc.getString("mainBoxId") ?: return@addOnSuccessListener
                    loadPackages()
                }
        } else {
            emptyMessage.text = "로그인이 필요합니다."
            emptyMessage.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    private fun setupFabClickListener() {
        fabAddPackage.setOnClickListener {
            if (boxId.isNotEmpty()) {
                val intent = Intent(requireContext(), RegisterPackageActivity::class.java).apply {
                    putExtra("boxId", boxId)
                }
                startActivity(intent)
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
                        val intent = Intent(requireContext(), PackageEditActivity::class.java).apply {
                            putExtra("boxId", boxId)
                            putExtra("packageId", selectedItem.id)
                        }
                        editLauncher.launch(intent)
                    }

                    recyclerView.adapter = adapter
                }
            }
            .addOnFailureListener { e ->
                Log.e("PackageFragment", "🔥 패키지 불러오기 실패: ${e.message}")
                emptyMessage.text = "데이터를 불러올 수 없습니다."
                emptyMessage.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에서 돌아올 때 데이터 새로고침
        if (boxId.isNotEmpty()) {
            loadPackages()
        }
    }

}