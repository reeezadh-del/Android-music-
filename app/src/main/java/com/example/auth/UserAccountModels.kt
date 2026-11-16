package com.example.auth

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val usernameKey: String = "",
    val driveFileId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UsernameClaim(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val usernameKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
