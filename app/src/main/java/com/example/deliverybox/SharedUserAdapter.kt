package com.example.deliverybox

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class   SharedUserAdapter(
    private val userList: List<Pair<String, String>>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<SharedUserAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emailText: TextView = view.findViewById(R.id.tv_user_email)
        val deleteButton: ImageButton = view.findViewById(R.id.btn_delete_user)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shared_user, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (uidOrKey, emailDisplay) = userList[position]
        val context = holder.itemView.context

        holder.emailText.text = emailDisplay

        when {
            uidOrKey == "info" -> {
                // 안내 텍스트용
                holder.emailText.setTypeface(null, Typeface.NORMAL)
                holder.emailText.setTextColor(ContextCompat.getColor(context, R.color.gray))
                holder.emailText.textAlignment = View.TEXT_ALIGNMENT_CENTER
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                holder.deleteButton.visibility = View.GONE
            }

            uidOrKey.startsWith("pending_") -> {
                // 가입 대기 사용자
                holder.emailText.setTypeface(null, Typeface.ITALIC)
                holder.emailText.setTextColor(ContextCompat.getColor(context, R.color.gray))
                holder.emailText.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.gray_background))
                holder.deleteButton.visibility = View.VISIBLE
            }

            else -> {
                // 정상 사용자
                holder.emailText.setTypeface(null, Typeface.NORMAL)
                holder.emailText.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                holder.emailText.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
                holder.deleteButton.visibility = View.VISIBLE
            }
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(uidOrKey)
        }
    }

    override fun getItemCount(): Int = userList.size
}
