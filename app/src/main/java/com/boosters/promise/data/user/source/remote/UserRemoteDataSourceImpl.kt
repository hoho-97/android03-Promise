package com.boosters.promise.data.user.source.remote

import com.boosters.promise.data.location.GeoLocation
import com.boosters.promise.data.network.NetworkConnectionUtil
import com.boosters.promise.data.user.di.UserModule
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRemoteDataSourceImpl @Inject constructor(
    @UserModule.UserCollectionReference private val userCollectionReference: CollectionReference,
    private val networkConnectionUtil: NetworkConnectionUtil
) : UserRemoteDataSource {

    override suspend fun requestSignUp(userName: String): Result<UserBody> = runCatching {
        networkConnectionUtil.checkNetworkOnline()

        val userCode = userCollectionReference.document().id.take(USER_CODE_LENGTH)
        val token = FirebaseMessaging.getInstance().token.await()
        val userBody = UserBody(
            userCode = userCode,
            userName = userName,
            geoLocation = null,
            token = token
        )
        userCollectionReference.document(userCode).set(
            userBody
        ).await()

        userBody
    }

    override fun getUser(userCode: String): Flow<UserBody> =
        userCollectionReference.document(userCode).snapshots().mapNotNull {
            it.toObject(UserBody::class.java)
        }

    override suspend fun uploadMyGeoLocation(userCode: String, geoLocation: GeoLocation?): Result<Unit> = runCatching {
        networkConnectionUtil.checkNetworkOnline()

        userCollectionReference.document(userCode)
            .update(GEO_LOCATION_FIELD, geoLocation)
            .addOnSuccessListener { Result.success(Unit) }
            .addOnFailureListener { Result.failure<Unit>(it) }
    }

    override suspend fun resetMyGeoLocation(userCode: String) {
        uploadMyGeoLocation(userCode, null)
    }

    override suspend fun getUserList(userCode: List<String>): List<UserBody> {
        val task = userCollectionReference
            .whereIn(USER_CODE_KEY, userCode)
            .get()
        task.await()
        return task.result.documents.mapNotNull {
            it.toObject(UserBody::class.java)
        }
    }
            
    override fun getUserByName(userName: String): Flow<List<UserBody>> =
        userCollectionReference.whereEqualTo(USER_NAME_KEY, userName).snapshots().mapNotNull {
            it.toObjects(UserBody::class.java)
        }

    companion object {
        private const val USER_CODE_LENGTH = 6
        private const val USER_CODE_KEY = "userCode"
        private const val USER_NAME_KEY = "userName"
    }

}