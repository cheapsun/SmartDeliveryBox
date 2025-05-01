package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.deliverybox.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Firebase
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

        // 2️⃣ 내 박스(mainBoxId) 존재 여부 확인
        // ✅ 박스가 없더라도 강제 등록하지 않고 홈으로 진입
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                // 그냥 홈 프래그먼트로 이동 (box 연결 여부와 무관)
                replaceFragment(HomeFragment())
            }
            .addOnFailureListener {
                // Firestore 읽기 실패 시에도 최소한 홈 화면은 띄우기
                replaceFragment(HomeFragment())
            }

        // 3️⃣ 바텀 네비게이션
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_home ->  { replaceFragment(HomeFragment());            true }
                R.id.menu_package -> { replaceFragment(PackageFragment());        true }
                R.id.menu_notification -> { replaceFragment(NotificationFragment()); true }
                R.id.menu_doorlock -> { replaceFragment(DoorlockFragment());      true }
                R.id.menu_setting -> { replaceFragment(SettingFragment());        true }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.frameLayout, fragment)
            .commit()
    }
}
