package com.boosters.promise.ui.invite.adapter

import androidx.recyclerview.widget.DiffUtil
import com.boosters.promise.ui.invite.model.UserUiState

object UserDiffItemCallback : DiffUtil.ItemCallback<UserUiState>() {

    override fun areItemsTheSame(oldItem: UserUiState, newItem: UserUiState): Boolean {
        return oldItem.userCode == newItem.userCode
    }

    override fun areContentsTheSame(oldItem: UserUiState, newItem: UserUiState): Boolean {
        return oldItem == newItem
    }

}