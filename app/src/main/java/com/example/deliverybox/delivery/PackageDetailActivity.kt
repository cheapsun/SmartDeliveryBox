package com.example.deliverybox.delivery

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.deliverybox.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PackageDetailActivity : AppCompatActivity() {

    private val viewModel: PackageDetailViewModel by viewModels()

    private var boxId: String = ""
    private var packageId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_detail)

        // Intent에서 데이터 추출
        boxId = intent.getStringExtra("boxId") ?: ""
        packageId = intent.getStringExtra("packageId") ?: ""

        if (boxId.isEmpty() || packageId.isEmpty()) {
            Toast.makeText(this, "잘못된 접근입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupActionBar()
        setupViews()
        setupObservers()

        // 패키지 상세 정보 로드
        viewModel.loadPackageDetail(boxId, packageId)
    }

    private fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "택배 상세"
        }
    }

    private fun setupViews() {
        // 뒤로가기 버튼
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        // 수령 완료 버튼
        findViewById<MaterialButton>(R.id.btn_mark_received).setOnClickListener {
            showReceiveConfirmDialog()
        }

        // 택배사 추적 버튼
        findViewById<MaterialButton>(R.id.btn_track_courier).setOnClickListener {
            // 택배사 홈페이지 열기 (나중에 구현)
            Toast.makeText(this, "택배사 홈페이지 연결 예정", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupObservers() {
        // 패키지 정보 관찰
        viewModel.packageInfo.observe(this) { packageInfo ->
            packageInfo?.let {
                updatePackageInfo(it)
                supportActionBar?.subtitle = it.trackingNumber
            }
        }

        // UI 상태 관찰
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is PackageDetailUiState.Loading -> {
                    findViewById<ProgressBar>(R.id.progress_loading).visibility = View.VISIBLE
                }
                is PackageDetailUiState.Success -> {
                    findViewById<ProgressBar>(R.id.progress_loading).visibility = View.GONE
                }
                is PackageDetailUiState.Error -> {
                    findViewById<ProgressBar>(R.id.progress_loading).visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is PackageDetailUiState.Idle -> {
                    findViewById<ProgressBar>(R.id.progress_loading).visibility = View.GONE
                }
            }
        }

        // 작업 결과 관찰
        viewModel.operationResult.observe(this) { result ->
            result?.let { (success, message) ->
                Toast.makeText(this, message, if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()

                if (success) {
                    when {
                        message.contains("삭제") -> finish()
                        message.contains("수령") -> {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                }

                viewModel.clearOperationResult()
            }
        }
    }

    private fun updatePackageInfo(packageInfo: PackageInfo) {
        findViewById<TextView>(R.id.tv_tracking_number).text = packageInfo.getFormattedTrackingNumber()
        findViewById<TextView>(R.id.tv_courier_company).text = packageInfo.courierCompany
        findViewById<TextView>(R.id.tv_item_name).text = packageInfo.itemName ?: "상품명 없음"
        findViewById<TextView>(R.id.tv_status).text = "${packageInfo.status.getEmoji()} ${packageInfo.status.toKorean()}"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_package_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_edit -> {
                // 편집 화면으로 이동
                val intent = Intent(this, PackageEditActivity::class.java).apply {
                    putExtra("boxId", boxId)
                    putExtra("packageId", packageId)
                }
                startActivityForResult(intent, REQUEST_CODE_EDIT)
                true
            }
            R.id.action_delete -> {
                showDeleteConfirmDialog()
                true
            }
            R.id.action_mark_received -> {
                showReceiveConfirmDialog()
                true
            }
            R.id.action_refresh -> {
                viewModel.refreshTrackingInfo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("택배 삭제")
            .setMessage("택배 정보를 삭제하시겠습니까?\n삭제된 정보는 복구할 수 없습니다.")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deletePackage()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showReceiveConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("수령 확인")
            .setMessage("택배를 수령하셨습니까?\n수령 확인 후에는 취소할 수 없습니다.")
            .setPositiveButton("수령 완료") { _, _ ->
                viewModel.markAsReceived()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_EDIT && resultCode == RESULT_OK) {
            // 편집 후 돌아왔을 때 정보 새로고침
            viewModel.loadPackageDetail(boxId, packageId)
        }
    }

    companion object {
        private const val REQUEST_CODE_EDIT = 1001
    }
}