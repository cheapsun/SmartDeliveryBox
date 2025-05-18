package com.example.deliverybox.app

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
import com.example.deliverybox.box.BoxListAdapter
import com.example.deliverybox.box.BoxDetailActivity
import com.example.deliverybox.databinding.FragmentHomeBinding
import com.example.deliverybox.box.DeliveryBox
import com.example.deliverybox.box.RegisterBoxActivity
import com.example.deliverybox.box.dialog.RegisterBoxMethodDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.example.deliverybox.box.QrCodeValidationService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BoxListAdapter
    private val boxList = mutableListOf<DeliveryBox>()
    private var mainBoxId: String = ""

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var boxesListener: ListenerRegistration? = null

    // UI 상태 관리를 위한 enum
    private enum class UiState {
        LOADING,    // 로딩 중
        EMPTY,      // 빈 상태
        DATA        // 데이터 있음
    }

    private var currentUiState = UiState.LOADING

    private val registerBoxLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("HomeFragment", "registerBoxLauncher 결과 수신: ${result.resultCode}")

            // 결과와 관계없이 다이얼로그 닫기
            dismissRegisterBoxDialog()

            when (result.resultCode) {
                android.app.Activity.RESULT_OK -> {
                    Log.d("HomeFragment", "등록 성공")
                    updateEmptyState()
                }
                android.app.Activity.RESULT_CANCELED -> {
                    Log.d("HomeFragment", "등록 취소됨")
                }
                else -> {
                    Log.d("HomeFragment", "기타 결과: ${result.resultCode}")
                }
            }
        }

    // 다이얼로그 닫기 메서드
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

    // Fragment가 다시 보여질 때도 다이얼로그 상태 확인
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

        // 초기 로딩 상태로 시작 (빈 상태 아님)
        updateUiState(UiState.LOADING)

        setupRecyclerView()
        setupClickListeners()
        smartLoadBoxList()

        Log.d("HomeFragment", "onViewCreated 완료")
    }

    private fun updateUiState(newState: UiState) {
        // 같은 상태면 업데이트하지 않음
        if (currentUiState == newState) {
            Log.d("HomeFragment", "🔄 상태 변경 없음: $newState")
            return
        }

        Log.d("HomeFragment", "🎯 UI 상태 변경: $currentUiState -> $newState, boxList.size=${boxList.size}")
        currentUiState = newState

        // 메인 스레드에서 실행 보장
        requireActivity().runOnUiThread {
            _binding?.let { binding ->
                when (newState) {
                    UiState.LOADING -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.recyclerViewBoxes.visibility = View.GONE
                        Log.d("HomeFragment", "✅ 로딩 상태 표시 (progress: VISIBLE, empty: GONE, recycler: GONE)")
                    }
                    UiState.EMPTY -> {
                        binding.progressBar.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.VISIBLE
                        binding.recyclerViewBoxes.visibility = View.GONE
                        Log.d("HomeFragment", "✅ 빈 상태 표시 (progress: GONE, empty: VISIBLE, recycler: GONE)")
                    }
                    UiState.DATA -> {
                        binding.progressBar.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.recyclerViewBoxes.visibility = View.VISIBLE
                        Log.d("HomeFragment", "✅ 데이터 상태 표시 (progress: GONE, empty: GONE, recycler: VISIBLE)")

                        // 🔥 추가: RecyclerView 크기 강제 확인 및 재설정
                        binding.recyclerViewBoxes.post {
                            Log.d("HomeFragment", "📏 RecyclerView 크기 확인: width=${binding.recyclerViewBoxes.width}, height=${binding.recyclerViewBoxes.height}")
                            Log.d("HomeFragment", "📏 RecyclerView visibility: ${binding.recyclerViewBoxes.visibility}")
                            Log.d("HomeFragment", "📏 RecyclerView adapter: ${binding.recyclerViewBoxes.adapter}")

                            // Adapter가 연결되어 있는지 확인
                            if (binding.recyclerViewBoxes.adapter == null) {
                                Log.w("HomeFragment", "❌ RecyclerView adapter가 null! 다시 설정합니다.")
                                binding.recyclerViewBoxes.adapter = adapter
                            }

                            // 강제로 레이아웃 재계산
                            binding.recyclerViewBoxes.requestLayout()

                            // 다시 한번 notifyDataSetChanged 호출
                            adapter.notifyDataSetChanged()
                            Log.d("HomeFragment", "🔄 RecyclerView 강제 재설정 완료")
                        }
                    }
                }

                // 강제로 레이아웃 업데이트
                binding.root.requestLayout()
                Log.d("HomeFragment", "🔄 레이아웃 업데이트 완료")
            } ?: run {
                Log.w("HomeFragment", "❌ 바인딩이 null이어서 UI 상태 업데이트 불가")
            }
        }
    }

    // 기존 updateEmptyState 메서드를 대체
    private fun updateEmptyState(isEmpty: Boolean = boxList.isEmpty()) {
        val newState = if (isEmpty) UiState.EMPTY else UiState.DATA
        updateUiState(newState)
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
            },
            onUnregisterBox = { boxInfo ->
                Log.d("HomeFragment", "🗑️ 택배함 등록 해제 요청: ${boxInfo.alias}")
                showUnregisterBoxDialog(boxInfo)
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

            Log.d("HomeFragment", "📱 RecyclerView 설정 완료 - adapter: $adapter, layoutManager: $layoutManager")
            Log.d("HomeFragment", "📊 초기 boxList 크기: ${boxList.size}")
        }
        Log.d("HomeFragment", "RecyclerView 및 Adapter 설정 완료")
    }

    /**
     * 택배함 등록 해제 확인 다이얼로그
     */
    private fun showUnregisterBoxDialog(boxInfo: DeliveryBox) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("택배함 등록 해제")
            .setMessage("정말로 '${boxInfo.alias}' 택배함 등록을 해제하시겠습니까?\n\n등록 해제 시:\n• 택배함 목록에서 제거됩니다\n• 등록된 택배 정보는 유지됩니다\n• 언제든지 다시 등록할 수 있습니다")
            .setPositiveButton("해제") { _, _ ->
                unregisterBox(boxInfo)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 택배함 등록 해제 실행
     */
    private fun unregisterBox(boxInfo: DeliveryBox) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        // 프로그레스 다이얼로그 표시
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setMessage("택배함 등록을 해제하는 중...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val boxAliases = userDoc.get("boxAliases") as? MutableMap<String, String>
                        ?: mutableMapOf()
                    val currentMainBoxId = userDoc.getString("mainBoxId") ?: ""

                    // 해당 박스 제거
                    boxAliases.remove(boxInfo.boxId)

                    // 업데이트할 데이터 준비
                    val updateData = mutableMapOf<String, Any>(
                        "boxAliases" to boxAliases
                    )

                    // 메인 박스였다면 메인 박스 설정도 변경
                    if (currentMainBoxId == boxInfo.boxId) {
                        val newMainBoxId = if (boxAliases.isNotEmpty()) {
                            boxAliases.keys.first() // 첫 번째 박스를 새 메인으로
                        } else {
                            "" // 박스가 없으면 빈 문자열
                        }
                        updateData["mainBoxId"] = newMainBoxId
                        mainBoxId = newMainBoxId
                    }

                    // Firestore 업데이트
                    db.collection("users").document(uid)
                        .update(updateData)
                        .addOnSuccessListener {
                            progressDialog.dismiss()

                            // boxes 컬렉션의 해당 박스 문서에서도 사용자 제거
                            removeUserFromBox(uid, boxInfo.boxId) {
                                Toast.makeText(requireContext(), "'${boxInfo.alias}' 택배함 등록이 해제되었습니다", Toast.LENGTH_SHORT).show()

                                // 로컬 목록에서 제거
                                val index = boxList.indexOfFirst { it.boxId == boxInfo.boxId }
                                if (index >= 0) {
                                    boxList.removeAt(index)
                                    adapter.notifyItemRemoved(index)
                                    adapter.updateMainBoxId(mainBoxId)

                                    // 빈 상태 업데이트
                                    updateEmptyState()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            progressDialog.dismiss()
                            Log.e("HomeFragment", "택배함 등록 해제 실패", e)
                            Toast.makeText(requireContext(), "등록 해제 실패: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "사용자 정보를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.e("HomeFragment", "사용자 정보 조회 실패", e)
                Toast.makeText(requireContext(), "등록 해제 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * 박스 문서에서 사용자 제거
     */
    private fun removeUserFromBox(uid: String, boxId: String, onComplete: () -> Unit) {
        val boxRef = db.collection("boxes").document(boxId)

        boxRef.get()
            .addOnSuccessListener { boxDoc ->
                if (boxDoc.exists()) {
                    val members = boxDoc.get("members") as? MutableMap<String, String>
                        ?: mutableMapOf()
                    val ownerId = boxDoc.getString("ownerId")

                    // 멤버에서 제거
                    members.remove(uid)

                    val updateData = mutableMapOf<String, Any>(
                        "members" to members
                    )

                    // 소유자였다면 소유자도 변경
                    if (ownerId == uid) {
                        if (members.isNotEmpty()) {
                            // 다른 멤버를 새 소유자로 설정
                            val newOwnerId = members.keys.first()
                            updateData["ownerId"] = newOwnerId
                            members[newOwnerId] = "owner"
                            updateData["members"] = members
                        } else {
                            // 멤버가 없으면 박스 상태를 AVAILABLE로 변경
                            updateData["status"] = "AVAILABLE"
                            updateData["ownerId"] = ""
                        }
                    }

                    boxRef.update(updateData)
                        .addOnSuccessListener { onComplete() }
                        .addOnFailureListener { e ->
                            Log.e("HomeFragment", "박스 문서 업데이트 실패", e)
                            onComplete() // 실패해도 계속 진행
                        }
                } else {
                    onComplete()
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "박스 문서 조회 실패", e)
                onComplete() // 실패해도 계속 진행
            }
    }

    private fun setupClickListeners() {
        binding.btnAddBox.setOnClickListener { showRegisterBoxDialog() }
        binding.btnEmptyAddBox.setOnClickListener { showRegisterBoxDialog() }
    }

    private fun showRegisterBoxDialog() {
        Log.d("HomeFragment", "등록 방법 다이얼로그 표시 시작")

        try {
            // 기존 다이얼로그가 있으면 먼저 닫기
            val existingDialog = parentFragmentManager.findFragmentByTag("RegisterBoxMethodDialog")
            if (existingDialog is RegisterBoxMethodDialogFragment) {
                Log.d("HomeFragment", "기존 다이얼로그 찾음, 닫는 중...")
                existingDialog.dismiss()
            }

            val dialog = RegisterBoxMethodDialogFragment()
            dialog.setOnRegisterBoxSelectedListener {
                Log.d("HomeFragment", "수동 등록 리스너 호출됨")

                // Fragment 상태 확인
                if (!isAdded || isDetached || isRemoving) {
                    Log.w("HomeFragment", "Fragment 상태가 유효하지 않음")
                    return@setOnRegisterBoxSelectedListener
                }

                try {
                    Log.d("HomeFragment", "RegisterBoxActivity 시작 시도")
                    val intent = Intent(requireContext(), RegisterBoxActivity::class.java)
                    registerBoxLauncher.launch(intent)
                    Log.d("HomeFragment", "RegisterBoxActivity 시작됨")
                } catch (e: Exception) {
                    Log.e("HomeFragment", "RegisterBoxActivity 시작 실패", e)
                    Toast.makeText(
                        requireContext(),
                        "등록 화면으로 이동할 수 없습니다: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            dialog.show(parentFragmentManager, "RegisterBoxMethodDialog")
            Log.d("HomeFragment", "다이얼로그 표시됨")

        } catch (e: Exception) {
            Log.e("HomeFragment", "다이얼로그 표시 실패", e)
            Toast.makeText(
                requireContext(),
                "다이얼로그를 표시할 수 없습니다: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 메인 박스 설정/해제 처리
     */
    private fun handleMainBoxToggle(boxInfo: DeliveryBox, setAsMain: Boolean) {
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
        if (!isAdded || _binding == null) {
            Log.w("HomeFragment", "❌ Fragment 상태 무효, 로딩 중단")
            return
        }

        val userUid = auth.currentUser?.uid
        if (userUid == null) {
            Log.w("HomeFragment", "❌ 사용자 로그인 안됨")
            updateUiState(UiState.EMPTY)
            return
        }

        Log.d("HomeFragment", "🚀 사용자 UID: $userUid 데이터 로딩 시작")

        // 로딩 상태로 시작 (단, 이미 데이터가 있으면 로딩 상태 표시 안함)
        if (boxList.isEmpty()) {
            updateUiState(UiState.LOADING)
        }

        // 기존 리스너 제거
        boxesListener?.remove()

        boxesListener = db.collection("users").document(userUid)
            .addSnapshotListener { snapshot, error ->
                Log.d("HomeFragment", "🎯 스냅샷 리스너 호출됨")

                if (!isAdded || _binding == null) {
                    Log.w("HomeFragment", "❌ Fragment 상태 무효, 리스너 무시")
                    return@addSnapshotListener
                }

                if (error != null) {
                    Log.e("HomeFragment", "❌ 데이터 로드 실패: ${error.message}")
                    updateUiState(UiState.EMPTY)
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w("HomeFragment", "⚠️ 사용자 문서 없음")
                    boxList.clear()
                    adapter.notifyDataSetChanged()
                    updateUiState(UiState.EMPTY)
                    return@addSnapshotListener
                }

                Log.d("HomeFragment", "📄 사용자 문서 존재함")

                // mainBoxId 업데이트
                val newMainBoxId = snapshot.getString("mainBoxId") ?: ""
                val oldMainBoxId = mainBoxId
                mainBoxId = newMainBoxId

                Log.d("HomeFragment", "🔍 mainBoxId: '$oldMainBoxId' -> '$newMainBoxId'")

                // boxAliases 확인
                val boxAliases = snapshot.get("boxAliases") as? Map<String, String> ?: emptyMap()
                Log.d("HomeFragment", "📦 boxAliases: $boxAliases (사이즈: ${boxAliases.size})")

                if (boxAliases.isEmpty()) {
                    Log.w("HomeFragment", "⚠️ 등록된 택배함 없음 - 빈 상태 표시")
                    boxList.clear()
                    adapter.notifyDataSetChanged()
                    adapter.updateMainBoxId(mainBoxId)
                    updateUiState(UiState.EMPTY)
                    return@addSnapshotListener
                }

                // 박스 리스트 구성
                val previousSize = boxList.size
                boxList.clear()

                boxAliases.forEach { (boxId, alias) ->
                    val boxInfo = DeliveryBox(
                        boxId = boxId,
                        alias = alias,
                        boxName = "로딩 중...",
                        packageCount = 0,
                        doorLocked = true
                    )
                    boxList.add(boxInfo)
                    Log.d("HomeFragment", "📦 박스 추가: $boxId -> $alias")
                }

                Log.d("HomeFragment", "📊 박스 리스트 업데이트: $previousSize -> ${boxList.size}")

                // 🔥 중요: 데이터가 있으므로 즉시 DATA 상태로 변경
                updateUiState(UiState.DATA)

                // UI 업데이트 - 강화된 방식
                adapter.updateMainBoxId(mainBoxId)

                // 🔥 추가: 데이터 변경을 확실히 알리기
                Log.d("HomeFragment", "🔄 notifyDataSetChanged 호출 전 - boxList.size: ${boxList.size}")
                adapter.notifyDataSetChanged()
                Log.d("HomeFragment", "🔄 notifyDataSetChanged 호출 완료")

                sortBoxList()

                // 박스 세부 정보 로드
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

    // 정렬 메서드 - UI 상태는 변경하지 않음
    private fun sortBoxList() {
        Log.d("HomeFragment", "박스 리스트 정렬 시작 - 총 ${boxList.size}개, 현재 상태: $currentUiState")

        // 리스트가 비어있으면 정렬만 하고 반환
        if (boxList.isEmpty()) {
            Log.d("HomeFragment", "리스트가 비어있어 정렬만 수행")
            adapter.notifyDataSetChanged()
            return
        }

        // 메인 박스를 최상단에 배치
        boxList.sortWith(compareBy { it.boxId != mainBoxId })

        // 어댑터 업데이트 (UI 상태는 변경하지 않음)
        adapter.updateMainBoxId(mainBoxId)
        adapter.notifyDataSetChanged()

        Log.d("HomeFragment", "박스 리스트 정렬 완료, UI 상태 유지: $currentUiState")
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
                                    DeliveryBox(
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

        // 🔥 수정: 새로고침도 Firestore 리스너만 사용
        // 기존 리스너를 제거하고 새로 시작하여 최신 데이터 확보
        boxesListener?.remove()
        loadBoxList()
    }

    // 새로운 메서드: 빈 상태 확인 및 적절한 로딩 방식 선택
    private fun smartLoadBoxList() {
        Log.d("HomeFragment", "🚀 smartLoadBoxList 시작 - 현재 박스 수: ${boxList.size}")

        // 초기에는 로딩 상태로 시작
        updateUiState(UiState.LOADING)

        // 🔥 수정: ValidationService 제거하고 Firestore 리스너만 사용
        // 이렇게 하면 단일 소스에서만 데이터를 받아 상태 충돌을 방지
        Log.d("HomeFragment", "🔄 Firestore 리스너만 시작 (ValidationService 제거)")
        loadBoxList()
    }

    override fun onDestroyView() {
        // 리스너 제거 및 바인딩 해제
        boxesListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}