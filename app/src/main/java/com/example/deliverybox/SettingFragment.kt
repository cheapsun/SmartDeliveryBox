package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingFragment : Fragment() {

    private lateinit var layoutProfile: CardView   // ✅ CardView로 수정
    private lateinit var layoutBoxInfo: CardView   // ✅ CardView로 수정
    private lateinit var tvUserEmail: TextView
    private lateinit var tvBoxInfo: TextView
    private lateinit var btnAddSharedUser: ImageButton
    private lateinit var layoutNotificationSettings: LinearLayout
    private lateinit var layoutLogout: LinearLayout
    private lateinit var layoutDeleteAccount: LinearLayout

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // View 연결
        layoutProfile = view.findViewById(R.id.layout_profile)
        layoutBoxInfo = view.findViewById(R.id.layout_box_info)
        tvUserEmail = view.findViewById(R.id.tv_user_email)
        tvBoxInfo = view.findViewById(R.id.tv_box_info)
        btnAddSharedUser = view.findViewById(R.id.btn_add_shared_user)
        layoutNotificationSettings = view.findViewById(R.id.layout_notification_settings)
        layoutLogout = view.findViewById(R.id.layout_logout)
        layoutDeleteAccount = view.findViewById(R.id.layout_delete_account)

        // 🔹 Firestore 사용자 정보 로드
        loadUserData()

        // 🔹 버튼 클릭 리스너 설정
        setupListeners()

        return view
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val uid = currentUser.uid

            // 이메일 표시
            tvUserEmail.text = currentUser.email

            // 박스 정보 가져오기
            db.collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val boxIds = document.get("boxIds") as? List<String> ?: emptyList()
                        val boxCount = boxIds.size
                        val sharedUserUids = document.get("sharedUserUids") as? List<String> ?: emptyList()
                        val memberCount = sharedUserUids.size + 1  // 공유 사용자 수 + 본인

                        tvBoxInfo.text = "택배함 ${boxCount}개 | ${memberCount}명"
                    } else {
                        tvBoxInfo.text = "택배함 0개 | 0명"
                    }
                }
                .addOnFailureListener {
                    tvBoxInfo.text = "택배함 0개 | 0명"
                }
        }
    }

    private fun setupListeners() {
        // ➕ 공유 사용자 추가
        btnAddSharedUser.setOnClickListener {
            startActivity(Intent(requireContext(), AddSharedUserActivity::class.java))
        }

        // 🔵 프로필 클릭 → ProfileActivity 이동
        layoutProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        // 🔵 택배함 클릭 → SharedUserManageActivity 이동
        layoutBoxInfo.setOnClickListener {
            startActivity(Intent(requireContext(), SharedUserManageActivity::class.java))
        }

        // 알림 설정 클릭
        layoutNotificationSettings.setOnClickListener {
            Toast.makeText(requireContext(), "알림 설정은 추후 지원 예정입니다.", Toast.LENGTH_SHORT).show()
        }

        // 로그아웃 클릭
        layoutLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(requireContext(), "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), SplashActivity::class.java))
            requireActivity().finishAffinity()
        }

        // 회원탈퇴 클릭
        layoutDeleteAccount.setOnClickListener {
            deleteAccount()
        }
    }

    private fun deleteAccount() {
        val user = auth.currentUser
        val uid = user?.uid ?: return

        // Authentication 계정 삭제
        user.delete()
            .addOnSuccessListener {
                // Firestore 사용자 문서 삭제
                db.collection("users").document(uid)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "회원탈퇴 완료", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(requireContext(), SplashActivity::class.java))
                        requireActivity().finishAffinity()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Firestore 삭제 실패", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "회원탈퇴 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
