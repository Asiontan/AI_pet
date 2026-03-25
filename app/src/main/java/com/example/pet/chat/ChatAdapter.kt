package com.example.pet.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pet.R
import com.pet.core.data.chat.ChatMessage

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MsgViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    fun submitList(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** 追加或更新最后一条宠物消息（流式更新） */
    fun appendOrUpdateLast(msg: ChatMessage) {
        val lastIdx = items.indexOfLast { it.id == msg.id }
        if (lastIdx >= 0) {
            items[lastIdx] = msg
            notifyItemChanged(lastIdx)
        } else {
            items.add(msg)
            notifyItemInserted(items.size - 1)
        }
    }

    fun addMessage(msg: ChatMessage) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MsgViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MsgViewHolder(view)
    }

    override fun onBindViewHolder(holder: MsgViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class MsgViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val llRoot: LinearLayout = itemView.findViewById(R.id.llRoot)
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)

        fun bind(msg: ChatMessage) {
            if (msg.isUser) {
                // 用户消息：右对齐，紫色气泡
                llRoot.gravity = Gravity.END
                tvAvatar.visibility = View.GONE
                tvMessage.setBackgroundResource(R.drawable.bg_bubble_user)
                tvMessage.text = msg.content
            } else {
                // 宠物消息：左对齐，深蓝气泡
                llRoot.gravity = Gravity.START
                tvAvatar.visibility = View.VISIBLE
                tvMessage.setBackgroundResource(R.drawable.bg_bubble_pet)
                tvMessage.text = if (msg.isLoading && msg.content.isEmpty()) "..." else msg.content
            }
        }
    }
}

