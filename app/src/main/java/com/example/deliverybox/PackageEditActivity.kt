package com.example.deliverybox

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.deliverybox.model.Package
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

class PackageEditActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var boxId: String
    private lateinit var packageId: String
    private var createdAt: Long = 0L

    private val courierOptions = listOf("CJ대한통운", "한진택배", "우체국택배", "로젠택배", "쿠팡로지스틱스")
    private val categoryOptions = listOf("의류", "전자기기", "서적", "식품", "생활용품", "기타")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_edit)

        db = FirebaseFirestore.getInstance()
        boxId = intent.getStringExtra("boxId") ?: return
        packageId = intent.getStringExtra("packageId") ?: return

        val etTracking = findViewById<TextInputEditText>(R.id.et_edit_tracking)
        val etInfo = findViewById<TextInputEditText>(R.id.et_edit_info)
        val spinnerCourier = findViewById<Spinner>(R.id.spinner_courier)
        val spinnerCategory = findViewById<Spinner>(R.id.spinner_category)
        val etOrigin = findViewById<TextInputEditText>(R.id.et_edit_origin)
        val btnUpdate = findViewById<Button>(R.id.btn_update)
        val btnDelete = findViewById<Button>(R.id.btn_delete)

        // 스피너 연결
        spinnerCourier.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courierOptions)
        spinnerCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryOptions)

        // 기존 데이터 로드
        db.collection("boxes").document(boxId).collection("packages").document(packageId)
            .get()
            .addOnSuccessListener { doc ->
                val pkg = doc.toObject(Package::class.java)
                if (pkg != null) {
                    etTracking.setText(pkg.trackingNumber)
                    etInfo.setText(pkg.info)
                    etOrigin.setText(pkg.origin)
                    createdAt = pkg.createdAt

                    spinnerCourier.setSelection(courierOptions.indexOf(pkg.courierCompany))
                    spinnerCategory.setSelection(categoryOptions.indexOf(pkg.category))

                    // 삭제 제한 처리
                    val canDelete = System.currentTimeMillis() - createdAt <= 10 * 60 * 1000
                    btnDelete.isEnabled = canDelete
                    btnDelete.alpha = if (canDelete) 1.0f else 0.5f
                }
            }

        // 수정
        btnUpdate.setOnClickListener {
            val updatedTracking = etTracking.text.toString()
            val updatedInfo = etInfo.text.toString()
            val updatedCourier = spinnerCourier.selectedItem.toString()
            val updatedCategory = spinnerCategory.selectedItem.toString()
            val updatedOrigin = etOrigin.text.toString()

            db.collection("boxes").document(boxId).collection("packages").document(packageId)
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
                    setResult(RESULT_OK)
                    finish()
                }
        }

        // 삭제
        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("삭제 확인")
                .setMessage("정말 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    db.collection("boxes").document(boxId)
                        .collection("packages").document(packageId)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "삭제 완료", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }
}
