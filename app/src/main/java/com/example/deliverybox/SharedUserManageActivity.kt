package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SharedUserManageActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SharedUserAdapter
    private lateinit var tvMemberTitle: TextView
    private lateinit var inviteMemberCard: CardView
    private lateinit var tvEmptyMessage: TextView

    private val sharedUsers = mutableListOf<Pair<String, String>>()  // 🔥 빈 리스트로 안전 초기화

    companion object {
        private const val REQUEST_CODE_ADD_USER = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_user_manage)

        toolbar = findViewById(R.id.toolbar_shared_user)
        tvMemberTitle = findViewById(R.id.tv_member_title)
        recyclerView = findViewById(R.id.recycler_view_members)
        inviteMemberCard = findViewById(R.id.layout_invite_member)
        tvEmptyMessage = findViewById(R.id.tv_empty_message)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
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
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        inviteMemberCard.setOnClickListener {
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
        tvMemberTitle.text = "사용자 (${memberCount}명)"
    }

    private fun updateEmptyMessage() {
        if (sharedUsers.isEmpty()) {
            tvEmptyMessage.visibility = View.VISIBLE
        } else {
            tvEmptyMessage.visibility = View.GONE
        }
    }
}
