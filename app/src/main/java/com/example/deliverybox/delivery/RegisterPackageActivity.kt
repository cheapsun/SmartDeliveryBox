package com.example.deliverybox.delivery

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class RegisterPackageActivity : AppCompatActivity() {

    private lateinit var etTrackingNumber: TextInputEditText
    private lateinit var etInfo: TextInputEditText
    private lateinit var etOrigin: TextInputEditText
    private lateinit var spinnerCourier: Spinner
    private lateinit var spinnerCategory: Spinner
    private lateinit var btnRegister: MaterialButton

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var boxId: String
    private var destination: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_package)

        // UI 초기화
        etTrackingNumber = findViewById(R.id.et_tracking_number)
        etInfo = findViewById(R.id.et_package_info)
        etOrigin = findViewById(R.id.et_origin)
        spinnerCourier = findViewById(R.id.spinner_courier)
        spinnerCategory = findViewById(R.id.spinner_category)
        btnRegister = findViewById(R.id.btn_register_package)

        // Firebase 초기화
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Spinner 설정
        val courierList = listOf("CJ대한통운", "롯데택배", "한진택배", "우체국", "기타")
        val categoryList = listOf("생활용품", "의류/잡화", "전자기기", "식품", "도서/문구", "기타")
        spinnerCourier.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courierList)
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryList)

        // boxId & destination 불러오기
        val currentUid = auth.currentUser?.uid ?: return
        db.collection("users").document(currentUid).get()
            .addOnSuccessListener { document ->
                boxId = document.getString("boxId") ?: return@addOnSuccessListener
                destination = document.getString("destination") ?: "도착지 정보 없음"
            }
            .addOnFailureListener {
                Toast.makeText(this, "사용자 정보 로딩 실패", Toast.LENGTH_SHORT).show()
            }

        // 등록 버튼 클릭 이벤트
        btnRegister.setOnClickListener {
            val trackingNumber = etTrackingNumber.text.toString().trim()
            val info = etInfo.text.toString().trim()
            val origin = etOrigin.text.toString().trim()
            val courier = spinnerCourier.selectedItem.toString()
            val category = spinnerCategory.selectedItem.toString()

            if (trackingNumber.isEmpty() || info.isEmpty() || origin.isEmpty()) {
                Toast.makeText(this, "모든 값을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val packagesRef = db.collection("boxes").document(boxId).collection("packages")

            // 중복 운송장 체크
            packagesRef.whereEqualTo("trackingNumber", trackingNumber).get()
                .addOnSuccessListener { result ->
                    if (!result.isEmpty) {
                        Toast.makeText(this, "이미 등록된 운송장번호입니다.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // 시간 정보
                    val timestamp = System.currentTimeMillis()
                    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    val fullFormatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
                    dateFormatter.timeZone = TimeZone.getTimeZone("Asia/Seoul")
                    timeFormatter.timeZone = TimeZone.getTimeZone("Asia/Seoul")
                    fullFormatter.timeZone = TimeZone.getTimeZone("Asia/Seoul")

                    val dateStr = dateFormatter.format(Date(timestamp))
                    val timeStr = timeFormatter.format(Date(timestamp))
                    val formattedDateTime = fullFormatter.format(Date(timestamp))

                    // 문서 ID는 운송장번호 제외 (수정 가능하게)
                    val docId = formattedDateTime

                    val packageData = hashMapOf(
                        "trackingNumber" to trackingNumber,
                        "info" to info,
                        "courierCompany" to courier,
                        "category" to category,
                        "origin" to origin,
                        "destination" to destination,
                        "valid" to true,
                        "createdAt" to timestamp,
                        "createdDate" to dateStr,
                        "createdTime" to timeStr
                    )

                    // 등록
                    packagesRef.document(docId).set(packageData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "택배가 등록되었습니다", Toast.LENGTH_SHORT).show()
                            etTrackingNumber.text?.clear()
                            etInfo.text?.clear()
                            etOrigin.text?.clear()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "등록 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "중복 확인 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
