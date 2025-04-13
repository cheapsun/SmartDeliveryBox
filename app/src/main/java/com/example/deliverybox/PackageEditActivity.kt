package com.example.deliverybox

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.model.Package
import com.google.firebase.firestore.FirebaseFirestore

class PackageEditActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var boxId: String
    private lateinit var packageId: String
    private var createdAt: Long = 0L  // 삭제 제한용

    // 스피너 선택지
    private val courierOptions = listOf("CJ대한통운", "한진택배", "우체국택배", "로젠택배", "쿠팡로지스틱스")
    private val categoryOptions = listOf("의류", "전자기기", "서적", "식품", "생활용품", "기타")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_edit)

        db = FirebaseFirestore.getInstance()

        // 인텐트로부터 boxId, packageId 받기
        boxId = intent.getStringExtra("boxId") ?: return
        packageId = intent.getStringExtra("packageId") ?: return

        // UI 요소들 참조
        val etTracking = findViewById<EditText>(R.id.et_edit_tracking)
        val etInfo = findViewById<EditText>(R.id.et_edit_info)
        val spinnerCourier = findViewById<Spinner>(R.id.spinner_courier)
        val spinnerCategory = findViewById<Spinner>(R.id.spinner_category)
        val etOrigin = findViewById<EditText>(R.id.et_edit_origin)
        val btnUpdate = findViewById<Button>(R.id.btn_update)
        val btnDelete = findViewById<Button>(R.id.btn_delete)

        // 스피너 어댑터 설정
        spinnerCourier.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courierOptions)
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryOptions)

        // 기존 데이터 Firestore에서 불러오기
        db.collection("boxes").document(boxId)
            .collection("packages").document(packageId)
            .get()
            .addOnSuccessListener { doc ->
                val pkg = doc.toObject(Package::class.java)
                if (pkg != null) {
                    etTracking.setText(pkg.trackingNumber)
                    etInfo.setText(pkg.info)
                    etOrigin.setText(pkg.origin)
                    createdAt = pkg.createdAt

                    // 기존 선택값 반영
                    spinnerCourier.setSelection(courierOptions.indexOf(pkg.courierCompany))
                    spinnerCategory.setSelection(categoryOptions.indexOf(pkg.category))
                }
            }

        // 수정 버튼 클릭
        btnUpdate.setOnClickListener {
            val updatedTracking = etTracking.text.toString()
            val updatedInfo = etInfo.text.toString()
            val updatedCourier = spinnerCourier.selectedItem.toString()
            val updatedCategory = spinnerCategory.selectedItem.toString()
            val updatedOrigin = etOrigin.text.toString()

            db.collection("boxes").document(boxId)
                .collection("packages").document(packageId)
                .update(
                    mapOf(
                        "trackingNumber" to updatedTracking,
                        "info" to updatedInfo,
                        "courierCompany" to updatedCourier,
                        "category" to updatedCategory,
                        "origin" to updatedOrigin
                    )
                )
                .addOnSuccessListener {
                    Toast.makeText(this, "수정 완료", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }

        // 삭제 버튼 클릭 (10분 이내만 허용)
        btnDelete.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - createdAt <= 10 * 60 * 1000) {
                db.collection("boxes").document(boxId)
                    .collection("packages").document(packageId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "삭제 완료", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            } else {
                Toast.makeText(this, "10분이 지나 삭제할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
