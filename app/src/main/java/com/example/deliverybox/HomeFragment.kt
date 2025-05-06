package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deliverybox.adapter.BoxListAdapter
import com.example.deliverybox.databinding.FragmentHomeBinding
import com.example.deliverybox.model.BoxInfo
import com.example.deliverybox.dialog.RegisterBoxMethodDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BoxListAdapter
    private val boxList = mutableListOf<BoxInfo>()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var boxesListener: ListenerRegistration? = null

    private val registerBoxLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == android.app.Activity.RESULT_OK) {
                // 이미 실시간 리스너가 있으므로 자동으로 업데이트됨
                updateEmptyState()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        loadBoxList()
    }

    private fun setupRecyclerView() {
        adapter = BoxListAdapter(boxList) { boxInfo ->
            // 택배함 클릭 시 상세 화면으로 이동
            val intent = Intent(requireContext(), BoxDetailActivity::class.java).apply {
                putExtra("boxId", boxInfo.boxId)
                putExtra("boxName", boxInfo.boxName)
                putExtra("boxAlias", boxInfo.alias)
            }
            startActivity(intent)
        }

        binding.recyclerViewBoxes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        // 상단 + 버튼 클릭 - 택배함 추가 다이얼로그 표시
        binding.btnAddBox.setOnClickListener {
            showRegisterBoxDialog()
        }

        // 빈 상태 화면의 버튼 클릭
        binding.btnEmptyAddBox.setOnClickListener {
            showRegisterBoxDialog()
        }
    }

    private fun showRegisterBoxDialog() {
        val dialog = RegisterBoxMethodDialogFragment()
        dialog.setOnRegisterBoxSelectedListener {
            val intent = Intent(requireContext(), RegisterBoxActivity::class.java)
            registerBoxLauncher.launch(intent)
        }
        dialog.show(parentFragmentManager, "RegisterBoxMethodDialog")
    }

    private fun loadBoxList() {
        val userUid = auth.currentUser?.uid ?: return

        // 이전 리스너 해제
        boxesListener?.remove()

        // 실시간 업데이트를 위한 리스너 설정
        boxesListener = db.collection("users").document(userUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "택배함 정보 로드 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                boxList.clear()

                val boxAliases = snapshot?.get("boxAliases") as? Map<String, String> ?: emptyMap()

                if (boxAliases.isEmpty()) {
                    updateEmptyState(true)
                    return@addSnapshotListener
                }

                // 각 박스 정보 가져오기
                for ((boxId, alias) in boxAliases) {
                    db.collection("boxes").document(boxId)
                        .get()
                        .addOnSuccessListener { boxDoc ->
                            val boxName = boxDoc.getString("boxName") ?: "택배함"

                            // 현재 보관 중인 택배 수 계산
                            db.collection("boxes").document(boxId)
                                .collection("packages")
                                .whereEqualTo("isDelivered", false)
                                .get()
                                .addOnSuccessListener { packagesSnapshot ->
                                    val packageCount = packagesSnapshot.size()

                                    val boxInfo = BoxInfo(
                                        boxId = boxId,
                                        alias = alias,
                                        boxName = boxName,
                                        packageCount = packageCount
                                    )

                                    // 리스트 업데이트 및 UI 갱신
                                    boxList.add(boxInfo)
                                    adapter.notifyDataSetChanged()
                                    updateEmptyState(boxList.isEmpty())
                                }
                        }
                }
            }
    }

    private fun updateEmptyState(isEmpty: Boolean = boxList.isEmpty()) {
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewBoxes.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 리스너 해제
        boxesListener?.remove()
        _binding = null
    }
}