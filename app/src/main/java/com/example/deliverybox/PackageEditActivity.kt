// PackageEditActivity.kt
package com.example.deliverybox

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

class PackageEditActivity : AppCompatActivity() {

    private lateinit var etTrackingNumber: TextInputEditText
    private lateinit var etInfo: TextInputEditText
    private lateinit var etOrigin: TextInputEditText
    private lateinit var spinnerCourier: Spinner
    private lateinit var spinnerCategory: Spinner
    private lateinit var btnUpdate: MaterialButton
    private lateinit var btnDelete: MaterialButton

    private lateinit var db: FirebaseFirestore
    private lateinit var boxId: String
    private lateinit var docId: String
    private var createdAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_edit)

        // UI 연결
        etTrackingNumber = findViewById(R.id.et_edit_tracking_number)
        etInfo = findViewById(R.id.et_edit_info)
        etOrigin = findViewById(R.id.et_edit_origin)
        spinnerCourier = findViewById(R.id.spinner_edit_courier)
        spinnerCategory = findViewById(R.id.spinner_edit_category)
        btnUpdate = findViewById(R.id.btn_update_package)
        btnDelete = findViewById(R.id.btn_delete_package)

        // Firestore
        db = FirebaseFirestore.getInstance()

        // Spinner 설정
        val courierList = listOf("CJ대한통운", "롯데택배", "한진택배", "우체국", "기타")
        val categoryList = listOf("생활용품", "의류/잡화", "전자기기", "식품", "도서/문구", "기타")
        spinnerCourier.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, courierList)
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryList)

        // 인텐트로 전달받은 값들
        boxId = intent.getStringExtra("boxId") ?: return
        docId = intent.getStringExtra("docId") ?: return
        createdAt = intent.getLongExtra("createdAt", 0L)

        etTrackingNumber.setText(intent.getStringExtra("trackingNumber"))
        etInfo.setText(intent.getStringExtra("info"))
        etOrigin.setText(intent.getStringExtra("origin"))

        val courier = intent.getStringExtra("courierCompany") ?: "기타"
        val category = intent.getStringExtra("category") ?: "기타"
        spinnerCourier.setSelection(courierList.indexOf(courier))
        spinnerCategory.setSelection(categoryList.indexOf(category))

        // 삭제 제한 (10분 이상 지난 경우 삭제 버튼 비활성화)
        val now = System.currentTimeMillis()
        val canDelete = now - createdAt <= 10 * 60 * 1000
        btnDelete.isEnabled = canDelete

        // 수정 버튼 클릭
        btnUpdate.setOnClickListener {
            val newTrackingNumber = etTrackingNumber.text.toString().trim()
            val newInfo = etInfo.text.toString().trim()
            val newOrigin = etOrigin.text.toString().trim()
            val newCourier = spinnerCourier.selectedItem.toString()
            val newCategory = spinnerCategory.selectedItem.toString()

            db.collection("boxes").document(boxId)
                .collection("packages").document(docId)
                .update(
                    mapOf(
                        "trackingNumber" to newTrackingNumber,
                        "info" to newInfo,
                        "origin" to newOrigin,
                        "courierCompany" to newCourier,
                        "category" to newCategory
                    )
                )
                .addOnSuccessListener {
                    Toast.makeText(this, "수정 완료", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "수정 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // 삭제 버튼 클릭
        btnDelete.setOnClickListener {
            db.collection("boxes").document(boxId)
                .collection("packages").document(docId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "삭제 완료", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "삭제 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
