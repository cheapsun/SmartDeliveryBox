// app/src/main/java/com/example/deliverybox/NotificationFragment.kt
package com.example.deliverybox

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import adapter.NotificationAdapter
import com.example.deliverybox.model.Notification
import com.example.deliverybox.model.NotificationType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class NotificationFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var layoutEmptyNotifications: LinearLayout
    private val notificationsList = mutableListOf<Notification>()
    private lateinit var adapter: NotificationAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notification, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvNotifications = view.findViewById(R.id.rv_notifications)
        layoutEmptyNotifications = view.findViewById(R.id.layout_empty_notifications)

        setupRecyclerView()
        loadNotifications()
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(notificationsList) { notification ->
            // 알림 클릭 처리
            markNotificationAsRead(notification.id)
        }

        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter
    }

    private fun loadNotifications() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50) // 최대 50개까지만 로드
            .get()
            .addOnSuccessListener { result ->
                notificationsList.clear()

                if (!result.isEmpty) {
                    for (document in result) {
                        val title = document.getString("title") ?: ""
                        val message = document.getString("message") ?: ""
                        val timestamp = document.getLong("timestamp") ?: 0L
                        val typeString = document.getString("type") ?: "GENERAL"
                        val type = try {
                            NotificationType.valueOf(typeString)
                        } catch (e: IllegalArgumentException) {
                            NotificationType.GENERAL
                        }
                        val boxId = document.getString("boxId") ?: ""
                        val read = document.getBoolean("read") ?: false
                        val additionalData = document.get("additionalData") as? Map<String, String> ?: mapOf()

                        val notification = Notification(
                            id = document.id,
                            title = title,
                            message = message,
                            timestamp = timestamp,
                            type = type,
                            boxId = boxId,
                            read = read,
                            additionalData = additionalData
                        )

                        notificationsList.add(notification)
                    }

                    updateUI(false)
                } else {
                    updateUI(true)
                }
            }
            .addOnFailureListener { e ->
                Log.e("NotificationFragment", "알림 로드 실패: ${e.message}")
                updateUI(true)
            }
    }

    private fun updateUI(isEmpty: Boolean) {
        if (isEmpty) {
            rvNotifications.visibility = View.GONE
            layoutEmptyNotifications.visibility = View.VISIBLE
        } else {
            rvNotifications.visibility = View.VISIBLE
            layoutEmptyNotifications.visibility = View.GONE
            adapter.notifyDataSetChanged()
        }
    }

    private fun markNotificationAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid)
            .collection("notifications").document(notificationId)
            .update("read", true)
            .addOnSuccessListener {
                // UI에서 해당 알림의 읽음 상태 업데이트
                val position = notificationsList.indexOfFirst { it.id == notificationId }
                if (position != -1) {
                    notificationsList[position] = notificationsList[position].copy(read = true)
                    adapter.notifyItemChanged(position)
                }
            }
            .addOnFailureListener { e ->
                Log.e("NotificationFragment", "알림 읽음 상태 업데이트 실패: ${e.message}")
            }
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 알림 목록 새로고침
        loadNotifications()
    }
}