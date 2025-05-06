package com.example.deliverybox

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class ProfileActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var layoutProfilePicture: LinearLayout
    private lateinit var layoutNickname: LinearLayout
    private lateinit var tvNickname: TextView
    private lateinit var tvEmail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // 🔹 View 연결
        toolbar = findViewById(R.id.toolbar_profile)
        layoutProfilePicture = findViewById(R.id.layout_profile_picture)
        layoutNickname = findViewById(R.id.layout_nickname)
        tvNickname = findViewById(R.id.tv_nickname)
        tvEmail = findViewById(R.id.tv_email)

        // 🔹 Toolbar 설정
        setupToolbar()

        // 🔹 데이터 세팅 (임시로 기본 텍스트 세팅, 나중에 Firestore 등 연결 가능)
        tvNickname.text = "테스트닉네임"
        tvEmail.text = "tes****@gmail.com"  // 이메일 마스킹된 형태로 표시

        // 🔹 클릭 리스너 설정
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // 기본 타이틀 숨김

        // 네비게이션 아이콘 직접 설정
        toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)

        // 네비게이션 클릭 시 뒤로가기 처리
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupListeners() {
        // 프로필 사진 변경 클릭
        layoutProfilePicture.setOnClickListener {
            // TODO: 프로필 사진 변경 화면 연결 예정
        }

        // 닉네임 변경 클릭
        layoutNickname.setOnClickListener {
            // TODO: 닉네임 수정 화면 연결 예정
        }

        // 이메일은 클릭 리스너 필요 없음 (수정 불가)
    }
}
