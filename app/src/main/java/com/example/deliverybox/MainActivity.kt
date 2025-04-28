package com.example.deliverybox

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.deliverybox.ui.home.HomeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // BottomNavigationView를 findViewById로 연결
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // 첫 화면 기본 HomeFragment 띄우기
        replaceFragment(HomeFragment())

        // 탭 선택 리스너 설정
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> replaceFragment(HomeFragment())
                R.id.nav_package -> replaceFragment(PackageFragment())
                R.id.nav_notification -> replaceFragment(NotificationFragment())
                R.id.nav_doorlock -> replaceFragment(DoorlockFragment())
                R.id.nav_setting -> replaceFragment(SettingFragment())
            }
            true
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container_fragment, fragment)
            .commit()
    }
}
