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
                // 따로 처리할 로직이 없으면 홈으로 이동
                replaceFragment(HomeFragment())
            }
            .addOnFailureListener {
                replaceFragment(HomeFragment())
            }

        // 3️⃣ 앱 시작 시 기본으로 HomeFragment 표시
        replaceFragment(HomeFragment())

        // 4️⃣ 바텀 네비게이션 클릭 리스너
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

    // Fragment 교체 유틸 함수
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, fragment)
            .commit()
    }
}
