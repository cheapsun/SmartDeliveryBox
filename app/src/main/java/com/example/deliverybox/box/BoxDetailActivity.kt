package com.example.deliverybox.box

import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import adapter.LogAdapter
import adapter.PackageAdapter
import com.example.deliverybox.delivery.PackageEditActivity
import com.example.deliverybox.delivery.PackageListActivity
import com.example.deliverybox.delivery.RegisterPackageActivity
import com.example.deliverybox.databinding.ActivityBoxDetailBinding
import com.example.deliverybox.delivery.Package
import adapter.LogItem
import com.example.deliverybox.delivery.PackageItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class BoxDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBoxDetailBinding
    private lateinit var boxId: String
    private lateinit var boxName: String
    private lateinit var boxAlias: String

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var packageAdapter: PackageAdapter
    private lateinit var logAdapter: LogAdapter

    private val packagesList = mutableListOf<PackageItem>()
    private val logsList = mutableListOf<LogItem>()

    private var doorStatusListener: ListenerRegistration? = null
    private var packagesListener: ListenerRegistration? = null
    private var logsListener: ListenerRegistration? = null

    private var isDoorLocked = true

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
    }

    private fun setupRecyclerViews() {
        // 택배 리스트
        packageAdapter = PackageAdapter(packagesList) { packageItem ->
            val intent = Intent(this, PackageEditActivity::class.java).apply {
                putExtra("boxId", boxId)
                putExtra("packageId", packageItem.id)
            }
            startActivity(intent)
        }

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
                    isDoorLocked = action == "CLOSE"

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
                    Toast.makeText(this, "택배 정보 로드 실패", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                packagesList.clear()

                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots) {
                        val pkg = doc.toObject(Package::class.java)
                        packagesList.add(PackageItem(doc.id, pkg))

                    }
                }

                packageAdapter.notifyDataSetChanged()

                // 통계 업데이트
                updateStatistics()
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
                            db.collection("users").document(byUid)
                                .get()
                                .addOnSuccessListener { userDoc ->
                                    val userName = userDoc.getString("displayName") ?: "사용자"

                                    val logItem = LogItem(
                                        id = doc.id,
                                        event = event,
                                        userName = userName,
                                        timestamp = timestamp
                                    )

                                    logsList.add(logItem)
                                    logAdapter.notifyDataSetChanged()
                                }
                        }
                    }
                }
            }
    }

    private fun updateDoorLockUI() {
        binding.tvDoorStatus.text = "현재 상태: ${if (isDoorLocked) "잠금" else "열림"}"

        // 버튼 활성화/비활성화
        binding.btnLock.isEnabled = !isDoorLocked
        binding.btnUnlock.isEnabled = isDoorLocked
    }

    private fun updateStatistics() {
        // 배송 중인 택배 수 계산 (Firestore에서 가져와야 함, 여기서는 예시로 처리)
        db.collection("boxes").document(boxId)
            .collection("packages")
            .whereEqualTo("isDelivered", false)
            .get()
            .addOnSuccessListener { packages ->
                val inDeliveryCount = packages.size()

                // 공유 사용자 수 계산
                db.collection("boxes").document(boxId)
                    .get()
                    .addOnSuccessListener { boxDoc ->
                        val members = boxDoc.get("members") as? Map<String, String> ?: emptyMap()
                        val memberCount = members.size

                        // 통계 UI 업데이트 로직
                        // 여기서는 예시로 처리
                        // 실제 구현에서는 데이터를 UI에 반영
                    }
            }
    }

    private fun controlDoorLock(lock: Boolean) {
        val uid = auth.currentUser?.uid ?: return

        // 도어락 제어 명령 전송
        db.collection("doorControl").document(boxId)
            .set(mapOf(
                "action" to if (lock) "CLOSE" else "OPEN",
                "byUid" to uid,
                "at" to com.google.firebase.Timestamp.now()
            ))
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "도어락 ${if (lock) "잠금" else "열기"} 명령 전송 완료",
                    Toast.LENGTH_SHORT
                ).show()

                // 로그 기록 (서버 측에서도 처리될 수 있음)
                db.collection("boxes").document(boxId)
                    .collection("logs")
                    .add(mapOf(
                        "event" to if (lock) "CLOSE" else "OPEN",
                        "byUid" to uid,
                        "at" to com.google.firebase.Timestamp.now()
                    ))
            }
            .addOnFailureListener {
                Toast.makeText(this, "도어락 제어 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        doorStatusListener?.remove()
        packagesListener?.remove()
        logsListener?.remove()
    }
}