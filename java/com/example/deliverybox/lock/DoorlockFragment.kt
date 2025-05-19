package com.example.deliverybox.lock

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import adapter.LogAdapter
import com.example.deliverybox.R
import com.example.deliverybox.box.DeliveryBox
import adapter.LogItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import java.util.Date

class DoorlockFragment : Fragment() {

    private lateinit var spinnerBox: Spinner
    private lateinit var tvDoorlockName: TextView
    private lateinit var tvDoorlockStatus: TextView
    private lateinit var ivDoorlockIcon: ImageView
    private lateinit var btnUnlock: Button
    private lateinit var btnLock: Button
    private lateinit var btnGenerateQr: Button
    private lateinit var rvLogs: RecyclerView
    private lateinit var tvEmptyLogs: TextView
    private lateinit var cardDoorlockStatus: CardView

    private lateinit var logAdapter: LogAdapter
    private val logsList = mutableListOf<LogItem>()
    private val boxesList = mutableListOf<DeliveryBox>()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var selectedBoxId: String = ""
    private var selectedBoxName: String = ""
    private var isDoorLocked: Boolean = true

    private var doorStatusListener: ListenerRegistration? = null
    private var logsListener: ListenerRegistration? = null

    private var qrCodeDialog: Dialog? = null
    private var qrCountDownTimer: CountDownTimer? = null
    private val QR_EXPIRATION_SECONDS = 45L // QR 코드 유효 시간(초)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_doorlock, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뷰 초기화
        spinnerBox = view.findViewById(R.id.spinner_box)
        tvDoorlockName = view.findViewById(R.id.tv_doorlock_name)
        tvDoorlockStatus = view.findViewById(R.id.tv_doorlock_status)
        ivDoorlockIcon = view.findViewById(R.id.iv_doorlock_icon)
        btnUnlock = view.findViewById(R.id.btn_unlock)
        btnLock = view.findViewById(R.id.btn_lock)
        btnGenerateQr = view.findViewById(R.id.btn_generate_qr)
        rvLogs = view.findViewById(R.id.rv_logs)
        tvEmptyLogs = view.findViewById(R.id.tv_empty_logs)
        cardDoorlockStatus = view.findViewById(R.id.card_doorlock_status)

        // 리사이클러뷰 초기화
        rvLogs.layoutManager = LinearLayoutManager(requireContext())
        logAdapter = LogAdapter(logsList)
        rvLogs.adapter = logAdapter

        // 버튼 리스너 설정
        btnUnlock.setOnClickListener { controlDoorLock(false) }
        btnLock.setOnClickListener { controlDoorLock(true) }

        // QR 코드 생성 버튼 리스너
        btnGenerateQr.setOnClickListener { showQrCodeDialog() }

        // 박스 목록 로드 및 스피너 설정
        loadBoxList()
    }

    // QR 코드 생성 및 다이얼로그 표시
    private fun showQrCodeDialog() {
        if (selectedBoxId.isEmpty()) {
            Toast.makeText(requireContext(), "택배함을 선택해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid ?: return

        // 이전 다이얼로그 및 타이머 정리
        qrCodeDialog?.dismiss()
        qrCountDownTimer?.cancel()

        // 새로운 QR 코드 생성
        val qrBitmap = QrCodeGenerator.generateDoorlockQrCode(
            boxId = selectedBoxId,
            userId = userId,
            action = "OPEN",  // 기본적으로 열기 액션
            expirationSeconds = QR_EXPIRATION_SECONDS.toInt()  // 유효 시간(초)
        )

        if (qrBitmap == null) {
            Toast.makeText(requireContext(), "QR 코드 생성에 실패했습니다", Toast.LENGTH_SHORT).show()
            return
        }

        // QR 코드 사용 로그 기록
        db.collection("boxes").document(selectedBoxId)
            .collection("logs")
            .add(mapOf(
                "event" to "QR_GENERATED",
                "byUid" to userId,
                "at" to com.google.firebase.Timestamp.now(),
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + QR_EXPIRATION_SECONDS * 1000))
            ))

        // 다이얼로그 생성 및 표시
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_qr_code)
        qrCodeDialog = dialog

        // 다이얼로그 뷰 설정
        val tvQrTitle = dialog.findViewById<TextView>(R.id.tv_qr_title)
        val ivQrCode = dialog.findViewById<ImageView>(R.id.iv_qr_code)
        val tvQrInfo = dialog.findViewById<TextView>(R.id.tv_qr_info)
        val tvTimer = dialog.findViewById<TextView>(R.id.tv_timer)
        val btnRefresh = dialog.findViewById<Button>(R.id.btn_refresh)
        val btnClose = dialog.findViewById<Button>(R.id.btn_close)

        tvQrTitle.text = "$selectedBoxName QR 코드"
        ivQrCode.setImageBitmap(qrBitmap)
        tvQrInfo.text = "이 QR 코드는 ${QR_EXPIRATION_SECONDS}초간 유효합니다"

        // 카운트다운 타이머 설정
        qrCountDownTimer = object : CountDownTimer(QR_EXPIRATION_SECONDS * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvTimer.text = "남은 시간: 00:${String.format("%02d", seconds)}"

                // 15초 이하로 떨어지면 빨간색으로 표시
                if (seconds <= 15) {
                    tvTimer.setTextColor(Color.RED)
                }
            }

            override fun onFinish() {
                tvTimer.text = "만료됨"
                tvTimer.setTextColor(Color.RED)
                ivQrCode.alpha = 0.3f // 흐리게 처리

                // 만료 후 자동으로 새로고침 버튼 강조
                btnRefresh.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#6A8DFF"))
                )
                btnRefresh.setTextColor(Color.WHITE)
            }
        }.start()

        // 새로고침 버튼 클릭 리스너
        btnRefresh.setOnClickListener {
            dialog.dismiss()
            showQrCodeDialog() // 새로운 QR 코드 생성
        }

        // 닫기 버튼 클릭 리스너
        btnClose.setOnClickListener {
            qrCountDownTimer?.cancel()
            dialog.dismiss()
        }

        // 다이얼로그가 취소될 때 타이머도 취소
        dialog.setOnCancelListener {
            qrCountDownTimer?.cancel()
        }

        dialog.show()
    }

    private fun loadBoxList() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                boxesList.clear()

                val boxAliases = snapshot.get("boxAliases") as? Map<String, String> ?: emptyMap()
                val mainBoxId = snapshot.getString("mainBoxId")

                if (boxAliases.isEmpty() && mainBoxId.isNullOrEmpty()) {
                    showNoBoxesUI()
                    return@addOnSuccessListener
                }

                val boxIds = boxAliases.keys.toMutableList()
                if (!mainBoxId.isNullOrEmpty() && !boxIds.contains(mainBoxId)) {
                    boxIds.add(mainBoxId)
                }

                if (boxIds.isEmpty()) {
                    showNoBoxesUI()
                    return@addOnSuccessListener
                }

                var loadedCount = 0
                for (boxId in boxIds) {
                    db.collection("boxes").document(boxId)
                        .get()
                        .addOnSuccessListener { boxDoc ->
                            loadedCount++

                            if (boxDoc.exists()) {
                                val alias = boxAliases[boxId] ?: "내 택배함"
                                val boxName = boxDoc.getString("boxName") ?: "택배함"

                                boxesList.add(
                                    DeliveryBox(
                                    boxId = boxId,
                                    alias = alias,
                                    boxName = boxName
                                )
                                )
                            }

                            if (loadedCount == boxIds.size) {
                                setupBoxSpinner()
                            }
                        }
                        .addOnFailureListener {
                            loadedCount++
                            if (loadedCount == boxIds.size && boxesList.isEmpty()) {
                                showNoBoxesUI()
                            }
                        }
                }
            }
            .addOnFailureListener {
                showNoBoxesUI()
            }
    }

    private fun setupBoxSpinner() {
        if (boxesList.isEmpty()) {
            showNoBoxesUI()
            return
        }

        val boxNames = boxesList.map { it.alias }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, boxNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBox.adapter = adapter

        spinnerBox.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < boxesList.size) {
                    val box = boxesList[position]
                    selectedBoxId = box.boxId
                    selectedBoxName = box.alias

                    cardDoorlockStatus.visibility = View.VISIBLE
                    tvDoorlockName.text = selectedBoxName

                    // 리스너 제거 후 새로 설정
                    removeDoorStatusListener()
                    removeLogsListener()
                    setupDoorStatusListener()
                    setupLogsListener()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedBoxId = ""
                selectedBoxName = ""
                cardDoorlockStatus.visibility = View.GONE
            }
        }

        // 첫 번째 박스 선택
        if (boxesList.isNotEmpty()) {
            spinnerBox.setSelection(0)
        }
    }

    private fun showNoBoxesUI() {
        Toast.makeText(requireContext(), "등록된 택배함이 없습니다.", Toast.LENGTH_SHORT).show()
        cardDoorlockStatus.visibility = View.GONE
    }

    private fun setupDoorStatusListener() {
        if (selectedBoxId.isEmpty()) return

        doorStatusListener = db.collection("doorControl").document(selectedBoxId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DoorlockFragment", "도어락 상태 리스너 오류: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val action = snapshot.getString("action")
                    isDoorLocked = action == "CLOSE"
                    updateDoorLockUI()
                }
            }
    }

    private fun setupLogsListener() {
        if (selectedBoxId.isEmpty()) return

        logsListener = db.collection("boxes").document(selectedBoxId)
            .collection("logs")
            .orderBy("at", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("DoorlockFragment", "로그 리스너 오류: ${error.message}")
                    return@addSnapshotListener
                }

                logsList.clear()

                if (snapshots != null && !snapshots.isEmpty) {
                    tvEmptyLogs.visibility = View.GONE
                    rvLogs.visibility = View.VISIBLE

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

                                    // 중복 추가 방지
                                    if (!logsList.any { it.id == doc.id }) {
                                        logsList.add(logItem)
                                        logAdapter.notifyDataSetChanged()
                                    }
                                }
                        }
                    }
                } else {
                    tvEmptyLogs.visibility = View.VISIBLE
                    rvLogs.visibility = View.GONE
                }
            }
    }

    private fun updateDoorLockUI() {
        tvDoorlockStatus.text = "현재 상태: ${if (isDoorLocked) "잠김" else "열림"}"

        // 아이콘 색상 변경
        ivDoorlockIcon.setColorFilter(
            requireContext().getColor(
                if (isDoorLocked) R.color.red else R.color.green_success
            )
        )

        // 버튼 활성화/비활성화
        btnLock.isEnabled = !isDoorLocked
        btnUnlock.isEnabled = isDoorLocked
    }

    private fun controlDoorLock(lock: Boolean) {
        if (selectedBoxId.isEmpty()) return

        val uid = auth.currentUser?.uid ?: return

        db.collection("doorControl").document(selectedBoxId)
            .set(mapOf(
                "action" to if (lock) "CLOSE" else "OPEN",
                "byUid" to uid,
                "at" to com.google.firebase.Timestamp.now()
            ))
            .addOnSuccessListener {
                Toast.makeText(
                    requireContext(),
                    "도어락 ${if (lock) "잠금" else "열기"} 명령 전송 완료",
                    Toast.LENGTH_SHORT
                ).show()

                // 로그 기록
                db.collection("boxes").document(selectedBoxId)
                    .collection("logs")
                    .add(mapOf(
                        "event" to if (lock) "CLOSE" else "OPEN",
                        "byUid" to uid,
                        "at" to com.google.firebase.Timestamp.now()
                    ))
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "도어락 제어 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeDoorStatusListener() {
        doorStatusListener?.remove()
        doorStatusListener = null
    }

    private fun removeLogsListener() {
        logsListener?.remove()
        logsListener = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeDoorStatusListener()
        removeLogsListener()
        qrCountDownTimer?.cancel()
        qrCodeDialog?.dismiss()
    }
}