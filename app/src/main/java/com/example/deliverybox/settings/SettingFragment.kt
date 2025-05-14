package com.example.deliverybox.settings

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
import com.example.deliverybox.shareduser.AddSharedUserActivity
import com.example.deliverybox.shareduser.SharedUserManageActivity
import com.example.deliverybox.SplashActivity
import com.example.deliverybox.databinding.FragmentSettingBinding
import com.example.deliverybox.profile.ProfileActivity
import com.example.deliverybox.utils.SharedPrefsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var layoutProfile: CardView
    private lateinit var layoutBoxInfo: CardView
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
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // View 연결
        layoutProfile = binding.layoutProfile
        layoutBoxInfo = binding.layoutBoxInfo
        tvUserEmail = binding.tvUserEmail
        tvBoxInfo = binding.tvBoxInfo
        btnAddSharedUser = binding.btnAddSharedUser
        layoutNotificationSettings = binding.layoutNotificationSettings
        layoutLogout = binding.layoutLogout
        layoutDeleteAccount = binding.layoutDeleteAccount

        // 사용자 정보 로드
        loadUserData()

        // 리스너 설정
        setupListeners()
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            tvUserEmail.text = currentUser.email
            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    val boxIds = document.get("boxIds") as? List<String> ?: emptyList()
                    val boxCount = boxIds.size
                    val sharedUserUids = document.get("sharedUserUids") as? List<String> ?: emptyList()
                    val memberCount = sharedUserUids.size + 1
                    tvBoxInfo.text = "택배함 ${boxCount}개 | ${memberCount}명"
                }
                .addOnFailureListener {
                    tvBoxInfo.text = "택배함 0개 | 0명"
                }
        }
    }

    private fun setupListeners() {
        // 공유 사용자 추가
        btnAddSharedUser.setOnClickListener {
            startActivity(Intent(requireContext(), AddSharedUserActivity::class.java))
        }

        // 프로필 이동
        layoutProfile.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        // 택배함 정보 이동
        layoutBoxInfo.setOnClickListener {
            startActivity(Intent(requireContext(), SharedUserManageActivity::class.java))
        }

        // 알림 설정
        layoutNotificationSettings.setOnClickListener {
            Toast.makeText(requireContext(), "알림 설정은 추후 지원 예정입니다.", Toast.LENGTH_SHORT).show()
        }

        // 로그아웃 클릭: 자동 로그인 해제 및 세션 정리
        layoutLogout.setOnClickListener {
            auth.signOut()
            SharedPrefsHelper.setAutoLogin(requireContext(), false)
            SharedPrefsHelper.clearLoginSession(requireContext())

            Toast.makeText(requireContext(), "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(requireContext(), SplashActivity::class.java))
            requireActivity().finishAffinity()
        }

        // 회원탈퇴
        layoutDeleteAccount.setOnClickListener {
            deleteAccount()
        }
    }

    private fun deleteAccount() {
        val user = auth.currentUser ?: return
        val uid = user.uid

        user.delete()
            .addOnSuccessListener {
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
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "회원탈퇴 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}