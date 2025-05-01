package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.databinding.ActivitySharedUserManageBinding

class SharedUserManageActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySharedUserManageBinding
    private lateinit var adapter: SharedUserAdapter
    private val sharedUsers = mutableListOf<Pair<String, String>>()

    companion object {
        private const val REQUEST_CODE_ADD_USER = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySharedUserManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 툴바 설정
        binding.toolbarSharedUser.setNavigationOnClickListener {
            finish()
        }

        setupRecyclerView()
        setupListeners()
        updateMemberTitle()
        updateEmptyMessage()
    }

    private fun setupRecyclerView() {
        adapter = SharedUserAdapter(sharedUsers) { uidOrKey ->
            deleteUser(uidOrKey)
        }
        binding.recyclerViewMembers.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMembers.adapter = adapter
    }

    private fun setupListeners() {
        binding.layoutInviteMember.setOnClickListener {
            val intent = Intent(this, AddSharedUserActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_ADD_USER)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_ADD_USER && resultCode == RESULT_OK) {
            val invitedEmail = data?.getStringExtra("invite_email")
            if (!invitedEmail.isNullOrEmpty()) {
                sharedUsers.add(Pair("new_uid_${System.currentTimeMillis()}", invitedEmail))
                adapter.notifyDataSetChanged()
                updateMemberTitle()
                updateEmptyMessage()
            }
        }
    }

    private fun deleteUser(uidOrKey: String) {
        sharedUsers.removeAll { it.first == uidOrKey }
        adapter.notifyDataSetChanged()
        updateMemberTitle()
        updateEmptyMessage()
    }

    private fun updateMemberTitle() {
        val memberCount = sharedUsers.size
        binding.tvMemberTitle.text = "사용자 (${memberCount}명)"
    }

    private fun updateEmptyMessage() {
        binding.tvEmptyMessage.visibility =
            if (sharedUsers.isEmpty()) View.VISIBLE else View.GONE
    }
}
