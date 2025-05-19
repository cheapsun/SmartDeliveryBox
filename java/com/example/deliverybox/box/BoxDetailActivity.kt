package com.example.deliverybox.box

import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import adapter.LogAdapter
import com.example.deliverybox.delivery.adapter.PackageAdapter
import com.example.deliverybox.delivery.PackageEditActivity
import com.example.deliverybox.delivery.PackageListActivity
import com.example.deliverybox.delivery.RegisterPackageActivity
import com.example.deliverybox.databinding.ActivityBoxDetailBinding
import adapter.LogItem
import com.example.deliverybox.delivery.adapter.PackageItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.example.deliverybox.app.MainActivity
import androidx.appcompat.app.AlertDialog
import android.view.Menu
import android.view.MenuItem
import com.example.deliverybox.R
import com.example.deliverybox.delivery.DeliveryStatus
import com.example.deliverybox.delivery.PackageInfo

class BoxDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBoxDetailBinding
    private lateinit var boxId: String
    private lateinit var boxName: String
    private lateinit var boxAlias: String
    private var isMainBox: Boolean = false

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var packageAdapter: PackageAdapter
    private lateinit var logAdapter: LogAdapter

    private var currentPackagesList = emptyList<PackageItem>()
    private val logsList = mutableListOf<LogItem>()

    private var doorStatusListener: ListenerRegistration? = null
    private var packagesListener: ListenerRegistration? = null
    private var logsListener: ListenerRegistration? = null
    private var statisticsListener: ListenerRegistration? = null

    private var isDoorLocked = true

    // 통계 데이터
    private var inDeliveryCount = 0
    private var inStorageCount = 0
    private var memberCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBoxDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Intent에서 데이터 가져오기
        boxId = intent.getStringExtra("boxId") ?: ""
        boxName = intent.getStringExtra("boxName") ?: "택배함"
        boxAlias = intent.getStringExtra("boxAlias") ?: boxName

        if (boxId.isEmpty()) {
            Toast.makeText(this, "택배함 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupListeners()
        setupRecyclerViews()
        loadData()
        checkIfMainBox()
        loadStatistics()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_box_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_unregister_box -> {
                showUnregisterBoxDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // 택배함 등록 해제 확인 다이얼로그
    private fun showUnregisterBoxDialog() {
        AlertDialog.Builder(this)
            .setTitle("택배함 등록 해제")
            .setMessage("정말로 '${boxAlias}' 택배함 등록을 해제하시겠습니까?\n\n등록 해제 시:\n• 택배함 목록에서 제거됩니다\n• 등록된 택배 정보는 유지됩니다\n• 언제든지 다시 등록할 수 있습니다")
            .setPositiveButton("해제") { _, _ ->
                unregisterBox()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 택배함 등록 해제 실행
    private fun unregisterBox() {
        val uid = auth.currentUser?.uid ?: return

        // 진행 다이얼로그 표시
        val progressDialog = AlertDialog.Builder(this)
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
                    boxAliases.remove(boxId)

                    // 업데이트할 데이터 준비
                    val updateData = mutableMapOf<String, Any>(
                        "boxAliases" to boxAliases
                    )

                    // 메인 박스였다면 메인 박스 설정도 변경
                    if (currentMainBoxId == boxId) {
                        val newMainBoxId = if (boxAliases.isNotEmpty()) {
                            boxAliases.keys.first() // 첫 번째 박스를 새 메인으로
                        } else {
                            "" // 박스가 없으면 빈 문자열
                        }
                        updateData["mainBoxId"] = newMainBoxId
                    }

                    // Firestore 업데이트
                    db.collection("users").document(uid)
                        .update(updateData)
                        .addOnSuccessListener {
                            progressDialog.dismiss()

                            // boxes 컬렉션의 해당 박스 문서에서도 사용자 제거
                            removeUserFromBox(uid) {
                                Toast.makeText(this, "'${boxAlias}' 택배함 등록이 해제되었습니다", Toast.LENGTH_SHORT).show()

                                // MainActivity로 돌아가면서 목록 새로고침
                                val intent = Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("refresh_boxes", true)
                                }
                                startActivity(intent)
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            progressDialog.dismiss()
                            Log.e("BoxDetailActivity", "택배함 등록 해제 실패", e)
                            Toast.makeText(this, "등록 해제 실패: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(this, "사용자 정보를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.e("BoxDetailActivity", "사용자 정보 조회 실패", e)
                Toast.makeText(this, "등록 해제 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // 박스 문서에서 사용자 제거
    private fun removeUserFromBox(uid: String, onComplete: () -> Unit) {
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
                            Log.e("BoxDetailActivity", "박스 문서 업데이트 실패", e)
                            onComplete() // 실패해도 계속 진행
                        }
                } else {
                    onComplete()
                }
            }
            .addOnFailureListener { e ->
                Log.e("BoxDetailActivity", "박스 문서 조회 실패", e)
                onComplete() // 실패해도 계속 진행
            }
    }

    // 메인 박스 여부 확인 메서드 추가
    private fun checkIfMainBox() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { userDoc ->
                val mainBoxId = userDoc.getString("mainBoxId") ?: ""
                isMainBox = mainBoxId == boxId

                Log.d("BoxDetailActivity", "메인 박스 상태 확인: $boxId, 메인: $isMainBox")

                // 체크박스 상태 업데이트 (리스너 없이)
                binding.switchMainBox.setOnCheckedChangeListener(null)
                binding.switchMainBox.isChecked = isMainBox
                setupListeners() // 리스너 다시 설정
            }
            .addOnFailureListener { e ->
                Log.e("BoxDetailActivity", "메인 박스 상태 확인 실패: ${e.message}")
                // 실패 시 기본값으로 설정
                isMainBox = false
                binding.switchMainBox.isChecked = false
            }
    }

    private fun setupUI() {
        // 툴바 설정
        setSupportActionBar(binding.toolbarBoxDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // 박스 정보 표시
        binding.tvBoxNameDetail.text = boxAlias
        binding.tvBoxCode.text = boxId
    }

    private fun setupListeners() {
        // 뒤로가기 버튼
        binding.toolbarBoxDetail.setNavigationOnClickListener {
            onBackPressed()
        }

        // 도어락 제어 버튼
        binding.btnUnlock.setOnClickListener {
            controlDoorLock(false)
        }

        binding.btnLock.setOnClickListener {
            controlDoorLock(true)
        }

        // 택배 등록 버튼
        binding.fabAddPackage.setOnClickListener {
            val intent = Intent(this, RegisterPackageActivity::class.java).apply {
                putExtra("boxId", boxId)
            }
            startActivity(intent)
        }

        // 전체 보기 버튼
        binding.tvViewAll.setOnClickListener {
            val intent = Intent(this, PackageListActivity::class.java).apply {
                putExtra("boxId", boxId)
            }
            startActivity(intent)
        }

        setupMainBoxListener()
    }

    private fun setupMainBoxListener() {
        // 메인 박스 설정 스위치 리스너
        binding.switchMainBox.setOnCheckedChangeListener { _, isChecked ->
            Log.d("BoxDetailActivity", "스위치 상태 변경: $isChecked, 현재 메인: $isMainBox")

            // 상태가 실제로 변경된 경우에만 처리
            if (isChecked != isMainBox) {
                // 스위치 임시 비활성화로 중복 클릭 방지
                binding.switchMainBox.isEnabled = false
                setAsMainBox(isChecked)
            }
        }
    }

    private fun setAsMainBox(setAsMain: Boolean) {
        val uid = auth.currentUser?.uid ?: return

        Log.d("BoxDetailActivity", "메인 박스 설정 변경: $boxId -> $setAsMain")

        // 메인 박스로 설정 또는 해제
        val updateData = if (setAsMain) {
            mapOf("mainBoxId" to boxId)
        } else {
            mapOf("mainBoxId" to "")  // 메인 박스 해제시 빈 문자열로 설정
        }

        db.collection("users").document(uid)
            .update(updateData)
            .addOnSuccessListener {
                // 성공 시 상태 업데이트
                isMainBox = setAsMain

                // 스위치 다시 활성화
                binding.switchMainBox.isEnabled = true

                Log.d("BoxDetailActivity", "메인 박스 설정 성공: $setAsMain")

                Toast.makeText(
                    this,
                    if (setAsMain) "메인 택배함으로 설정되었습니다" else "메인 택배함 설정이 해제되었습니다",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Log.e("BoxDetailActivity", "메인 박스 설정 실패: ${e.message}")

                // 실패시 스위치 상태 되돌리기
                binding.switchMainBox.isChecked = isMainBox
                binding.switchMainBox.isEnabled = true

                Toast.makeText(this, "설정 변경 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerViews() {
        // 택배 리스트
        packageAdapter = PackageAdapter(
            onItemClick = { packageItem ->
                val intent = Intent(this, PackageEditActivity::class.java).apply {
                    putExtra("boxId", boxId)
                    putExtra("packageId", packageItem.id)
                }
                startActivity(intent)
            },
            onStatusChange = { packageItem, newStatus ->
                // 상태 변경 로직 구현
                updatePackageStatus(packageItem, newStatus)
            },
            onDeleteClick = { packageItem ->
                // 삭제 로직 구현
                deletePackage(packageItem)
            }
        )

        binding.rvRecentPackages.apply {
            layoutManager = LinearLayoutManager(this@BoxDetailActivity)
            adapter = packageAdapter
        }

        // 활동 로그 리스트
        logAdapter = LogAdapter(logsList)

        binding.rvActivityLogs.apply {
            layoutManager = LinearLayoutManager(this@BoxDetailActivity)
            adapter = logAdapter
        }
    }

    private fun loadData() {
        val uid = auth.currentUser?.uid ?: return

        // 도어락 상태 리스너
        doorStatusListener = db.collection("doorControl").document(boxId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "도어락 상태 로드 실패", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val action = snapshot.getString("action")
                    isDoorLocked = action == "CLOSE" || action == "LOCK"

                    updateDoorLockUI()
                } else {
                    // 기본값으로 잠금 상태 설정
                    isDoorLocked = true
                    updateDoorLockUI()
                }
            }

        // 최근 택배 리스너 (최근 3개)
        packagesListener = db.collection("boxes").document(boxId)
            .collection("packages")
            .whereEqualTo("isDelivered", false)
            .orderBy("registeredAt", Query.Direction.DESCENDING)
            .limit(3)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("BoxDetailActivity", "택배 정보 로드 실패", error)
                    return@addSnapshotListener
                }

                val newPackagesList = mutableListOf<PackageItem>()

                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots) {
                        val pkg = doc.toObject(PackageInfo::class.java)
                        newPackagesList.add(PackageItem(doc.id, pkg))

                    }
                }

                packageAdapter.submitList(newPackagesList)

                // 빈 상태 표시
                updateEmptyPackageState(newPackagesList)
            }

        // 활동 로그 리스너 (최근 5개)
        logsListener = db.collection("boxes").document(boxId)
            .collection("logs")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "로그 정보 로드 실패", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                logsList.clear()

                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots) {
                        val event = doc.getString("event") ?: ""
                        val byUid = doc.getString("byUid") ?: ""
                        val timestamp = doc.getTimestamp("at")?.toDate()?.time ?: 0L

                        // 사용자 이름 가져오기
                        if (byUid.isNotEmpty()) {
                            loadUserNameAndAddLog(doc.id, event, byUid, timestamp)
                        } else {
                            val logItem = LogItem(
                                id = doc.id,
                                event = event,
                                userName = "시스템",
                                timestamp = timestamp
                            )
                            logsList.add(logItem)
                        }
                    }

                    logAdapter.notifyDataSetChanged()
                    updateEmptyLogState()
                }
            }
    }

    // 사용자 이름을 로드하고 로그 아이템 추가
    private fun loadUserNameAndAddLog(logId: String, event: String, byUid: String, timestamp: Long) {
        db.collection("users").document(byUid)
            .get()
            .addOnSuccessListener { userDoc ->
                val userName = if (userDoc.exists()) {
                    userDoc.getString("displayName") ?: userDoc.getString("name") ?: "사용자"
                } else {
                    "알 수 없는 사용자"
                }

                val logItem = LogItem(
                    id = logId,
                    event = event,
                    userName = userName,
                    timestamp = timestamp
                )

                // 중복 방지
                if (logsList.none { it.id == logId }) {
                    logsList.add(logItem)
                    logAdapter.notifyDataSetChanged()
                    updateEmptyLogState()
                }
            }
            .addOnFailureListener { e ->
                Log.e("BoxDetailActivity", "사용자 정보 로드 실패: $byUid", e)

                val logItem = LogItem(
                    id = logId,
                    event = event,
                    userName = "알 수 없는 사용자",
                    timestamp = timestamp
                )

                if (logsList.none { it.id == logId }) {
                    logsList.add(logItem)
                    logAdapter.notifyDataSetChanged()
                    updateEmptyLogState()
                }
            }
    }

    // 통계 정보 로드
    private fun loadStatistics() {
        statisticsListener = db.collection("boxes").document(boxId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("BoxDetailActivity", "통계 정보 로드 실패", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val members = snapshot.get("members") as? Map<String, String> ?: emptyMap()
                    memberCount = members.size

                    // 배송 중인 택배 수 계산
                    loadPackageStatistics()

                    // UI 업데이트
                    updateStatisticsUI()
                }
            }
    }

    // 택배 통계 정보 로드
    private fun loadPackageStatistics() {
        // 배송 중인 택배 수
        db.collection("boxes").document(boxId)
            .collection("packages")
            .whereEqualTo("isDelivered", false)
            .get()
            .addOnSuccessListener { packages ->
                inDeliveryCount = packages.size()
                updateStatisticsUI()
            }

        // 보관 중인 택배 수 (배송 완료되었지만 수령하지 않은 택배)
        db.collection("boxes").document(boxId)
            .collection("packages")
            .whereEqualTo("isDelivered", true)
            .whereEqualTo("isReceived", false)
            .get()
            .addOnSuccessListener { packages ->
                inStorageCount = packages.size()
                updateStatisticsUI()
            }
    }

    private fun updateDoorLockUI() {
        binding.tvDoorStatus.text = "현재 상태: ${if (isDoorLocked) "잠금" else "열림"}"

        // 버튼 활성화/비활성화
        binding.btnLock.isEnabled = !isDoorLocked
        binding.btnUnlock.isEnabled = isDoorLocked

        // 버튼 텍스트 동적 변경
        binding.btnUnlock.text = if (isDoorLocked) "열기" else "이미 열림"
        binding.btnLock.text = if (!isDoorLocked) "잠금" else "이미 잠김"
    }

    // 통계 UI 업데이트
    private fun updateStatisticsUI() {
        runOnUiThread {
            // 여기에 통계 UI 업데이트 로직 구현
            // 현재 XML에 통계 표시 텍스트뷰들이 있다면 업데이트
            // 예시: binding.tvInDeliveryCount.text = inDeliveryCount.toString()

            Log.d("BoxDetailActivity", "통계 업데이트: 배송중=$inDeliveryCount, 보관중=$inStorageCount, 멤버=$memberCount")
        }
    }

    // 빈 택배 상태 업데이트
    private fun updateEmptyPackageState(packages: List<PackageItem>) {
        if (packages.isEmpty()) {
            binding.tvEmptyPackages.visibility = android.view.View.VISIBLE
            binding.rvRecentPackages.visibility = android.view.View.GONE
        } else {
            binding.tvEmptyPackages.visibility = android.view.View.GONE
            binding.rvRecentPackages.visibility = android.view.View.VISIBLE
        }
    }

    // 빈 로그 상태 업데이트
    private fun updateEmptyLogState() {
        if (logsList.isEmpty()) {
            binding.tvEmptyLogs.visibility = android.view.View.VISIBLE
            binding.rvActivityLogs.visibility = android.view.View.GONE
        } else {
            binding.tvEmptyLogs.visibility = android.view.View.GONE
            binding.rvActivityLogs.visibility = android.view.View.VISIBLE
        }
    }

    private fun controlDoorLock(lock: Boolean) {
        val uid = auth.currentUser?.uid ?: return

        // 버튼 비활성화로 중복 클릭 방지
        binding.btnLock.isEnabled = false
        binding.btnUnlock.isEnabled = false

        val action = if (lock) "CLOSE" else "OPEN"

        // 도어락 제어 명령 전송
        db.collection("doorControl").document(boxId)
            .set(mapOf(
                "action" to action,
                "byUid" to uid,
                "at" to com.google.firebase.Timestamp.now()
            ))
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "도어락 ${if (lock) "잠금" else "열기"} 명령을 전송했습니다",
                    Toast.LENGTH_SHORT
                ).show()

                // 로그 기록
                addActivityLog(action, uid)

                // 상태 미리 업데이트 (실제 상태는 리스너로 확인)
                isDoorLocked = lock
                updateDoorLockUI()
            }
            .addOnFailureListener { e ->
                Log.e("BoxDetailActivity", "도어락 제어 실패", e)
                Toast.makeText(this, "도어락 제어 실패: ${e.message}", Toast.LENGTH_SHORT).show()

                // 버튼 다시 활성화
                updateDoorLockUI()
            }
    }

    // 활동 로그 추가
    private fun addActivityLog(action: String, uid: String) {
        db.collection("boxes").document(boxId)
            .collection("logs")
            .add(mapOf(
                "event" to action,
                "byUid" to uid,
                "at" to com.google.firebase.Timestamp.now()
            ))
            .addOnFailureListener { e ->
                Log.e("BoxDetailActivity", "로그 기록 실패", e)
            }
    }

    // 택배 상태 변경
    private fun updatePackageStatus(packageItem: PackageItem, newStatus: DeliveryStatus) {
        val packageRef = db.collection("boxes").document(boxId)
            .collection("packages").document(packageItem.id)

        val updateData = mutableMapOf<String, Any>()

        when (newStatus) {
            DeliveryStatus.DELIVERED -> {
                updateData["isDelivered"] = true
                updateData["deliveredAt"] = com.google.firebase.Timestamp.now()
                updateData["status"] = newStatus.name
            }
            DeliveryStatus.IN_BOX -> {
                updateData["isInBox"] = true
                updateData["inBoxAt"] = com.google.firebase.Timestamp.now()
                updateData["status"] = newStatus.name
            }
            else -> {
                updateData["status"] = newStatus.name
            }
        }

        packageRef.update(updateData)
            .addOnSuccessListener {
                Toast.makeText(this, "상태가 변경되었습니다", Toast.LENGTH_SHORT).show()

                // 상태 변경 로그 기록
                val uid = auth.currentUser?.uid ?: ""
                addActivityLog("상태 변경: ${newStatus.name}", uid)
            }
            .addOnFailureListener { e ->
                Log.e("BoxDetailActivity", "상태 변경 실패", e)
                Toast.makeText(this, "상태 변경 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // 택배 삭제
    private fun deletePackage(packageItem: PackageItem) {
        AlertDialog.Builder(this)
            .setTitle("택배 삭제")
            .setMessage("정말로 이 택배를 삭제하시겠습니까?\n\n삭제된 택배는 복구할 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                // 삭제 로직
                val packageRef = db.collection("boxes").document(boxId)
                    .collection("packages").document(packageItem.id)

                packageRef.delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "택배가 삭제되었습니다", Toast.LENGTH_SHORT).show()

                        // 삭제 로그 기록
                        val uid = auth.currentUser?.uid ?: ""
                        addActivityLog("택배 삭제: ${packageItem.data.itemName ?: "상품명 없음"}", uid)
                    }
                    .addOnFailureListener { e ->
                        Log.e("BoxDetailActivity", "택배 삭제 실패", e)
                        Toast.makeText(this, "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        doorStatusListener?.remove()
        packagesListener?.remove()
        logsListener?.remove()
        statisticsListener?.remove()
    }
}