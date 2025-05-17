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
import com.example.deliverybox.box.QrCodeValidationService
import com.example.deliverybox.box.UserBoxInfo
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Context

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
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // 🆕 결과와 관계없이 다이얼로그 닫기
            dismissRegisterBoxDialog()

            if (result.resultCode == android.app.Activity.RESULT_OK) {
                updateEmptyState()
            }
        }

    // 🆕 HomeFragment에 다이얼로그 닫기 메서드 추가
    private fun dismissRegisterBoxDialog() {
        try {
            val dialogFragment = parentFragmentManager.findFragmentByTag("RegisterBoxMethodDialog")
            if (dialogFragment is RegisterBoxMethodDialogFragment) {
                dialogFragment.dismiss()
            }
        } catch (e: Exception) {
            Log.d("HomeFragment", "다이얼로그 닫기 실패 (정상): ${e.message}")
        }
    }

    // 🆕 Fragment가 다시 보여질 때도 다이얼로그 상태 확인
    override fun onResume() {
        super.onResume()

        // Activity로부터 등록 성공 신호를 받았는지 확인
        if (requireActivity().intent?.getBooleanExtra("refresh_boxes", false) == true) {
            // 다이얼로그 닫기
            dismissRegisterBoxDialog()
        }
    }

    private lateinit var validationService: QrCodeValidationService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        validationService = QrCodeValidationService()

        Log.d("HomeFragment", "onViewCreated 시작")
        setupRecyclerView()
        setupClickListeners()
        smartLoadBoxList()
        Log.d("HomeFragment", "onViewCreated 완료")
    }

    private fun setupRecyclerView() {
        Log.d("HomeFragment", "🔧 RecyclerView 설정 시작")

        adapter = BoxListAdapter(
            boxList = boxList,
            onItemClick = { boxInfo ->
                Log.d("HomeFragment", "📱 아이템 클릭: ${boxInfo.alias}")
                val intent = Intent(requireContext(), BoxDetailActivity::class.java).apply {
                    putExtra("boxId", boxInfo.boxId)
                    putExtra("boxName", boxInfo.boxName)
                    putExtra("boxAlias", boxInfo.alias)
                }
                startActivity(intent)
            },
            onMainBoxToggle = { boxInfo, setAsMain ->
                Log.d("HomeFragment", "🎯 메인 박스 토글 콜백 호출: ${boxInfo.alias} -> $setAsMain")

                // Fragment 상태 확인
                if (!isAdded || context == null || isDetached || isRemoving) {
                    Log.w("HomeFragment", "❌ Fragment 상태가 유효하지 않음")
                    return@BoxListAdapter
                }

                // 메인 박스 토글 처리
                handleMainBoxToggle(boxInfo, setAsMain)
            }
        )

        binding.recyclerViewBoxes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
            setHasFixedSize(true)

            // 아이템 애니메이션 개선
            itemAnimator?.apply {
                changeDuration = 300
                moveDuration = 300
            }
        }
        Log.d("HomeFragment", "RecyclerView 및 Adapter 설정 완료")
    }

    private fun setupClickListeners() {
        binding.btnAddBox.setOnClickListener { showRegisterBoxDialog() }
        binding.btnEmptyAddBox.setOnClickListener { showRegisterBoxDialog() }
    }

    private fun showRegisterBoxDialog() {
        // 기존 다이얼로그가 있으면 먼저 닫기
        val existingDialog = parentFragmentManager.findFragmentByTag("RegisterBoxMethodDialog")
        if (existingDialog is RegisterBoxMethodDialogFragment) {
            existingDialog.dismiss()
        }

        val dialog = RegisterBoxMethodDialogFragment()
        dialog.setOnRegisterBoxSelectedListener {
            val intent = Intent(requireContext(), RegisterBoxActivity::class.java)
            registerBoxLauncher.launch(intent)
        }
        dialog.show(parentFragmentManager, "RegisterBoxMethodDialog")
    }

    /**
     * 메인 박스 설정/해제 처리
     */
    private fun handleMainBoxToggle(boxInfo: BoxInfo, setAsMain: Boolean) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.e("HomeFragment", "사용자가 로그인되어 있지 않음")
            Toast.makeText(
                requireContext(),
                "로그인이 필요합니다",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 현재 상태와 동일하면 처리하지 않음
        val isCurrentlyMain = boxInfo.boxId == mainBoxId
        if (setAsMain == isCurrentlyMain) {
            Log.d("HomeFragment", "메인 박스 상태 변경 없음: $setAsMain")
            return
        }

        Log.d("HomeFragment", "메인 박스 변경: ${boxInfo.alias} -> setAsMain: $setAsMain")

        val updateData = if (setAsMain) {
            mapOf("mainBoxId" to boxInfo.boxId)
        } else {
            mapOf("mainBoxId" to "")  // 메인 박스 해제시 빈 문자열로 설정
        }

        // UI 즉시 업데이트 (낙관적 업데이트)
        val previousMainBoxId = mainBoxId
        mainBoxId = if (setAsMain) boxInfo.boxId else ""
        adapter.updateMainBoxId(mainBoxId)

        // 버튼 비활성화로 중복 클릭 방지
        adapter.updateMainBoxId("updating") // 임시로 업데이트 중 상태 표시

        db.collection("users").document(uid)
            .update(updateData)
            .addOnSuccessListener {
                // 성공 시 로컬 상태 업데이트
                mainBoxId = if (setAsMain) boxInfo.boxId else ""
                adapter.updateMainBoxId(mainBoxId)

                Log.d("HomeFragment", "✅ 메인 박스 설정 성공: -> $mainBoxId")

                // 리스트 재정렬
                sortBoxList()

                // 사용자에게 피드백
                Toast.makeText(
                    requireContext(),
                    if (setAsMain) "${boxInfo.alias}이(가) 메인 택배함으로 설정되었습니다"
                    else "메인 택배함 설정이 해제되었습니다",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "메인 박스 설정 실패: ${e.message}")

                // 실패 시 원래 상태로 복구
                mainBoxId = previousMainBoxId
                adapter.updateMainBoxId(mainBoxId)
                sortBoxList()

                Toast.makeText(
                    requireContext(),
                    "설정 변경 실패: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun loadBoxList() {
        // Fragment가 이미 분리되었는지 확인
        if (!isAdded || _binding == null) return

        val userUid = auth.currentUser?.uid ?: return
        Log.d("HomeFragment", "사용자 UID: $userUid 데이터 로딩 시작")

        boxesListener?.remove()

        boxesListener = db.collection("users").document(userUid)
            .addSnapshotListener { snapshot, error ->
                Log.d("HomeFragment", "🎯 스냅샷 리스너 호출됨")

                if (error != null) {
                    Log.e("HomeFragment", "❌ 데이터 로드 실패: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w("HomeFragment", "⚠️ 사용자 문서 없음")
                    updateEmptyState(true)
                    return@addSnapshotListener
                }

                Log.d("HomeFragment", "📄 사용자 문서 존재함: ${snapshot.exists()}")
                Log.d("HomeFragment", "📊 전체 문서 데이터: ${snapshot.data}")

                // mainBoxId 직접 확인
                val rawMainBoxId = snapshot.get("mainBoxId")
                Log.d("HomeFragment", "🔍 RAW mainBoxId: $rawMainBoxId (타입: ${rawMainBoxId?.javaClass?.simpleName})")

                val newMainBoxId = snapshot.getString("mainBoxId") ?: ""
                Log.d("HomeFragment", "🔍 Firestore에서 로드된 mainBoxId: '$newMainBoxId' (길이: ${newMainBoxId.length})")
                Log.d("HomeFragment", "🔍 이전 mainBoxId: '$mainBoxId' (길이: ${mainBoxId.length})")

                // mainBoxId 업데이트
                val oldMainBoxId = mainBoxId
                mainBoxId = newMainBoxId

                if (oldMainBoxId != newMainBoxId) {
                    Log.d("HomeFragment", "✅ mainBoxId 변경됨: '$oldMainBoxId' -> '$newMainBoxId'")
                } else {
                    Log.d("HomeFragment", "🔄 mainBoxId 변경 없음: '$mainBoxId'")
                }

                // boxAliases 확인
                val boxAliases = snapshot.get("boxAliases") as? Map<String, String> ?: emptyMap()
                Log.d("HomeFragment", "📦 boxAliases: $boxAliases")

                if (boxAliases.isEmpty()) {
                    Log.w("HomeFragment", "⚠️ 등록된 택배함 없음")
                    updateEmptyState(true)
                    return@addSnapshotListener
                }

                // 박스 리스트 구성
                boxList.clear()
                boxAliases.forEach { (boxId, alias) ->
                    val boxInfo = BoxInfo(
                        boxId = boxId,
                        alias = alias,
                        boxName = "로딩 중...",
                        packageCount = 0,
                        doorLocked = true
                    )
                    boxList.add(boxInfo)
                    Log.d("HomeFragment", "📦 박스 추가: $boxId -> $alias")
                }

                // 어댑터 업데이트
                Log.d("HomeFragment", "🔄 어댑터에 mainBoxId 전달 전: '$mainBoxId'")
                adapter.updateMainBoxId(mainBoxId)
                Log.d("HomeFragment", "🔄 어댑터에 mainBoxId 전달 완료")

                sortBoxList()
                updateEmptyState(false)
                loadBoxDetails()
            }
    }

    // 개선된 메서드: 박스 세부 정보 로드 (동시성 이슈 해결)
    private fun loadBoxDetails() {
        if (boxList.isEmpty()) return

        val boxIds = boxList.map { it.boxId }

        // 배치로 박스 정보 조회 (성능 최적화)
        if (boxIds.isNotEmpty()) {
            // Firestore는 whereIn으로 최대 10개까지만 조회 가능
            val batches = boxIds.chunked(10)

            batches.forEach { batch ->
                db.collection("boxes")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!isAdded || _binding == null) return@addOnSuccessListener

                        val boxMap = querySnapshot.documents.associate { doc ->
                            doc.id to (doc.getString("boxName") ?: "택배함")
                        }

                        // UI 업데이트는 메인 스레드에서
                        requireActivity().runOnUiThread {
                            if (isAdded && _binding != null) {
                                boxList.forEachIndexed { index, boxInfo ->
                                    boxMap[boxInfo.boxId]?.let { boxName ->
                                        boxList[index] = boxList[index].copy(boxName = boxName)
                                    }
                                }
                                adapter.notifyDataSetChanged()

                                // 패키지 수 로드
                                loadPackageCountsBatch()
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("HomeFragment", "박스 배치 조회 실패", e)
                        // 실패 시 개별 조회로 폴백
                        loadBoxDetailsIndividually()
                    }
            }
        }
    }

    // 폴백용 개별 조회 메서드
    private fun loadBoxDetailsIndividually() {
        boxList.forEachIndexed { index, boxInfo ->
            db.collection("boxes").document(boxInfo.boxId)
                .get()
                .addOnSuccessListener { boxDoc ->
                    if (!isAdded || _binding == null) return@addOnSuccessListener

                    // index 범위 체크 및 boxId 재확인 (동시성 이슈 방지)
                    if (index < boxList.size && boxList[index].boxId == boxInfo.boxId) {
                        if (boxDoc.exists()) {
                            val boxName = boxDoc.getString("boxName") ?: "택배함"

                            // UI 업데이트는 메인 스레드에서
                            requireActivity().runOnUiThread {
                                if (isAdded && _binding != null && index < boxList.size) {
                                    boxList[index] = boxList[index].copy(boxName = boxName)
                                    adapter.notifyItemChanged(index)
                                }
                            }
                        }

                        // 패키지 수 로드
                        loadPackageCount(boxInfo.boxId, index)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("HomeFragment", "박스 정보 로드 실패: ${boxInfo.boxId} - ${e.message}")
                    // 실패해도 패키지 수는 로드 시도
                    loadPackageCount(boxInfo.boxId, index)
                }
        }
    }

    // 개선된 메서드: 패키지 수 로드 (동시성 이슈 해결)
    private fun loadPackageCount(boxId: String, index: Int) {
        db.collection("boxes").document(boxId)
            .collection("packages")
            .whereEqualTo("isDelivered", false)
            .get()
            .addOnSuccessListener { packagesSnapshot ->
                if (!isAdded || _binding == null) return@addOnSuccessListener

                val packageCount = packagesSnapshot.size()

                // index 범위 체크 및 boxId 재확인
                if (index < boxList.size && boxList[index].boxId == boxId) {
                    // UI 업데이트는 메인 스레드에서
                    requireActivity().runOnUiThread {
                        if (isAdded && _binding != null && index < boxList.size) {
                            boxList[index] = boxList[index].copy(packageCount = packageCount)
                            adapter.notifyItemChanged(index)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "패키지 수 로드 실패: $boxId - ${e.message}")
            }
    }

    // 단순화된 정렬 메서드
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

    // 새로운 메서드: 패키지 수 배치 로드 (성능 최적화)
    private fun loadPackageCountsBatch() {
        boxList.forEachIndexed { index, boxInfo ->
            loadPackageCount(boxInfo.boxId, index)
        }
    }

    // 새로운 메서드: QrCodeValidationService를 사용한 박스 목록 로드
    private fun loadBoxListWithValidationService() {
        if (!isAdded || _binding == null) return

        lifecycleScope.launch {
            try {
                val result = validationService.getUserBoxes()

                result.fold(
                    onSuccess = { userBoxes ->
                        if (!isAdded || _binding == null) return@fold

                        requireActivity().runOnUiThread {
                            if (isAdded && _binding != null) {
                                // UserBoxInfo를 BoxInfo로 변환
                                val newBoxList = userBoxes.map { userBox ->
                                    BoxInfo(
                                        boxId = userBox.boxCode,
                                        alias = userBox.alias,
                                        boxName = userBox.batchName,
                                        packageCount = 0, // 패키지 수는 별도 로드
                                        doorLocked = true
                                    )
                                }

                                boxList.clear()
                                boxList.addAll(newBoxList)

                                sortBoxList()
                                updateEmptyState(false)

                                // 패키지 수 로드
                                loadPackageCountsBatch()
                            }
                        }
                    },
                    onFailure = { error ->
                        Log.e("HomeFragment", "ValidationService로 박스 목록 로드 실패", error)
                        // 기존 방식으로 폴백
                        loadBoxList()
                    }
                )
            } catch (e: Exception) {
                Log.e("HomeFragment", "ValidationService 네트워크 오류", e)
                // 기존 방식으로 폴백
                loadBoxList()
            }
        }
    }

    // 새로운 메서드: 외부에서 호출 가능한 새로고침 (MainActivity에서 사용)
    fun refreshBoxList() {
        if (!isAdded || _binding == null) return

        Log.d("HomeFragment", "박스 목록 새로고침 시작")

        // ValidationService로 먼저 시도, 실패시 기존 방식으로 폴백
        loadBoxListWithValidationService()
    }

    // 새로운 메서드: 빈 상태 확인 및 적절한 로딩 방식 선택
    private fun smartLoadBoxList() {
        Log.d("HomeFragment", "🚀 smartLoadBoxList 시작")

        if (boxList.isEmpty()) {
            Log.d("HomeFragment", "📦 빈 상태 - ValidationService로 로드 시도")
            // 빈 상태면 ValidationService로 빠른 로드
            loadBoxListWithValidationService()
        } else {
            Log.d("HomeFragment", "📦 기존 데이터 있음 - 실시간 리스너 사용")
            // 기존 데이터가 있으면 실시간 리스너 사용
            loadBoxList()
        }

        // 강제로 Firestore 리스너도 시작
        Log.d("HomeFragment", "🔄 강제 Firestore 리스너 시작")
        loadBoxList()
    }

    override fun onDestroyView() {
        // 리스너 제거 및 바인딩 해제
        boxesListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}