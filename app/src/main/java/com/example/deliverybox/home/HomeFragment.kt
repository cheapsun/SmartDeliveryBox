package com.example.deliverybox.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import adapter.BoxListAdapter
import com.example.deliverybox.box.BoxDetailActivity
import com.example.deliverybox.databinding.FragmentHomeBinding
import com.example.deliverybox.box.BoxInfo
import com.example.deliverybox.box.RegisterBoxActivity
import com.example.deliverybox.dialog.RegisterBoxMethodDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BoxListAdapter
    private val boxList = mutableListOf<BoxInfo>()
    private var mainBoxId: String = ""

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var boxesListener: ListenerRegistration? = null

    private val registerBoxLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == android.app.Activity.RESULT_OK) {
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
        binding.btnAddBox.setOnClickListener { showRegisterBoxDialog() }
        binding.btnEmptyAddBox.setOnClickListener { showRegisterBoxDialog() }
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
        // Fragment가 이미 분리되었는지 확인
        if (!isAdded || _binding == null) return

        val userUid = auth.currentUser?.uid ?: return
        Log.d("HomeFragment", "사용자 UID: $userUid 데이터 로딩 시작")

        boxesListener?.remove()

        boxesListener = db.collection("users").document(userUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HomeFragment", "데이터 로드 실패: ${error.message}")
                    Toast.makeText(requireContext(), "택배함 정보 로드 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w("HomeFragment", "사용자 문서 없음")
                    updateEmptyState(true)
                    return@addSnapshotListener
                }

                Log.d("HomeFragment", "사용자 문서 데이터: ${snapshot.data}")

                // ✅ 단순화: 직접 맵으로 가져오기
                val boxAliases = snapshot.get("boxAliases") as? Map<String, String> ?: emptyMap()
                mainBoxId = snapshot.getString("mainBoxId") ?: ""

                Log.d("HomeFragment", "boxAliases: $boxAliases")
                Log.d("HomeFragment", "mainBoxId: $mainBoxId")

                if (boxAliases.isEmpty()) {
                    Log.w("HomeFragment", "등록된 택배함 없음")
                    updateEmptyState(true)
                    return@addSnapshotListener
                }

                // ✅ 단순화: 직접 boxList에 추가하고 비동기로 세부 정보 업데이트
                boxList.clear()

                // 모든 박스를 기본 정보로 먼저 추가
                boxAliases.forEach { (boxId, alias) ->
                    val boxInfo = BoxInfo(
                        boxId = boxId,
                        alias = alias,
                        boxName = "로딩 중...",
                        packageCount = 0,
                        doorLocked = true
                    )
                    boxList.add(boxInfo)
                }

                // UI 즉시 갱신 (기본 정보로)
                sortBoxList()
                updateEmptyState(false)

                // ✅ 단순화: 각 박스의 세부 정보를 비동기로 로드
                loadBoxDetails()
            }
    }

    // ✅ 새로운 메서드: 박스 세부 정보 로드
    private fun loadBoxDetails() {
        boxList.forEachIndexed { index, boxInfo ->
            // 박스 정보 로드
            db.collection("boxes").document(boxInfo.boxId)
                .get()
                .addOnSuccessListener { boxDoc ->
                    if (boxDoc.exists()) {
                        val boxName = boxDoc.getString("boxName") ?: "택배함"
                        boxList[index] = boxList[index].copy(boxName = boxName)
                        adapter.notifyItemChanged(index)
                    }

                    // 패키지 수 로드
                    loadPackageCount(boxInfo.boxId, index)
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "박스 정보 로드 실패: ${boxInfo.boxId} - ${e.message}")
                    // 실패해도 기본값 유지
                    loadPackageCount(boxInfo.boxId, index)
                }
        }
    }

    // ✅ 새로운 메서드: 패키지 수 로드
    private fun loadPackageCount(boxId: String, index: Int) {
        db.collection("boxes").document(boxId)
            .collection("packages")
            .whereEqualTo("isDelivered", false)
            .get()
            .addOnSuccessListener { packagesSnapshot ->
                val packageCount = packagesSnapshot.size()
                if (index < boxList.size && boxList[index].boxId == boxId) {
                    boxList[index] = boxList[index].copy(packageCount = packageCount)
                    adapter.notifyItemChanged(index)
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "패키지 수 로드 실패: $boxId - ${e.message}")
            }
    }

    // ✅ 단순화된 정렬 메서드
    private fun sortBoxList() {
        Log.d("HomeFragment", "박스 리스트 정렬 시작 - 총 ${boxList.size}개")

        // 메인 박스를 최상단에 배치
        boxList.sortWith(compareBy { it.boxId != mainBoxId })

        // 어댑터 업데이트
        adapter.updateMainBoxId(mainBoxId)
        adapter.notifyDataSetChanged()

        Log.d("HomeFragment", "박스 리스트 정렬 완료")
    }

    private fun updateEmptyState(isEmpty: Boolean = boxList.isEmpty()) {
        _binding?.let { binding ->
            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerViewBoxes.visibility = if (isEmpty) View.GONE else View.VISIBLE

            // 현재 택배함 수 로깅 (디버깅용)
            Log.d("HomeFragment", "빈 상태 업데이트: isEmpty=$isEmpty, boxList.size=${boxList.size}")
        }
    }

    override fun onDestroyView() {
        // 리스너 제거 및 바인딩 해제
        boxesListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
