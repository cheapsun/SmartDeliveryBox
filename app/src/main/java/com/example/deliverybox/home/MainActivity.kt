package com.example.deliverybox.home

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.deliverybox.lock.DoorlockFragment
import com.example.deliverybox.notification.NotificationFragment
import com.example.deliverybox.R
import com.example.deliverybox.settings.SettingFragment
import com.example.deliverybox.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.deliverybox.auth.LoginActivity
import com.example.deliverybox.delivery.PackageFragment
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Firebase 인스턴스
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db   by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1️⃣ 로그인 체크
        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 2️⃣ Firestore에서 사용자 문서 읽기 (실패해도 홈 화면 진입)
        db.collection("users").document(uid).get()
            .addOnSuccessListener {
                // 성공 시에도 홈 화면 표시
                if (supportFragmentManager.findFragmentById(R.id.frameLayout) == null) {
                    replaceFragment(HomeFragment())
                }
            }
            .addOnFailureListener {
                // 실패 시에도 홈 화면 표시
                if (supportFragmentManager.findFragmentById(R.id.frameLayout) == null) {
                    replaceFragment(HomeFragment())
                }
            }

        // 3️⃣ 초기 Fragment 설정 (Firestore 로드와 별개로)
        if (supportFragmentManager.findFragmentById(R.id.frameLayout) == null) {
            replaceFragment(HomeFragment())
        }

        // 4️⃣ 바텀 네비게이션 클릭 리스너 (기존 코드 유지)
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.menu_package -> {
                    replaceFragment(PackageFragment())
                    true
                }
                R.id.menu_notification -> {
                    replaceFragment(NotificationFragment())
                    true
                }
                R.id.menu_doorlock -> {
                    replaceFragment(DoorlockFragment())
                    true
                }
                R.id.menu_setting -> {
                    replaceFragment(SettingFragment())
                    true
                }
                else -> false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 새로운 Intent로 업데이트

        // 택배함 등록 후 목록 새로고침 처리
        if (intent?.getBooleanExtra("refresh_boxes", false) == true) {
            refreshBoxList()

            // 성공 메시지 표시
            if (intent.getBooleanExtra("show_success_message", false)) {
                Toast.makeText(this, "택배함이 성공적으로 등록되었습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fragment 교체 유틸 함수
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, fragment)
            .commit()
    }

    /**
     * HomeFragment의 박스 목록을 새로고침
     * RegisterBoxActivity에서 택배함 등록 후 호출됨
     */
    private fun refreshBoxList() {
        try {
            // Fragment 컨테이너에서 현재 표시된 Fragment 찾기
            val currentFragment = supportFragmentManager.findFragmentById(R.id.frameLayout)

            when (currentFragment) {
                is HomeFragment -> {
                    // HomeFragment의 새로고침 메서드 호출
                    currentFragment.refreshBoxList()

                    // 홈 탭으로 이동 (다른 탭에 있을 경우)
                    binding.bottomNavigation.selectedItemId = R.id.menu_home
                }
                else -> {
                    // 현재 HomeFragment가 아니면 홈으로 이동
                    binding.bottomNavigation.selectedItemId = R.id.menu_home
                    replaceFragment(HomeFragment())
                }
            }
        } catch (e: Exception) {
            // 오류 발생 시 로그 출력 및 홈으로 이동
            android.util.Log.e("MainActivity", "박스 목록 새로고침 중 오류", e)
            binding.bottomNavigation.selectedItemId = R.id.menu_home
            replaceFragment(HomeFragment())
        }
    }

    override fun onResume() {
        super.onResume()

        // Intent로부터 새로고침 플래그 확인
        if (intent?.getBooleanExtra("refresh_boxes", false) == true) {
            // 플래그를 제거하여 중복 실행 방지
            intent.removeExtra("refresh_boxes")
            intent.removeExtra("show_success_message")

            refreshBoxList()
        }
    }
}
