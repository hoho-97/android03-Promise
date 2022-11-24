package com.boosters.promise.ui.promisesetting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boosters.promise.data.location.GeoLocation
import com.boosters.promise.R
import com.boosters.promise.data.notification.NotificationRepository
import com.boosters.promise.data.promise.Promise
import com.boosters.promise.data.promise.PromiseRepository
import com.boosters.promise.data.promise.ServerKeyRepository
import com.boosters.promise.data.user.User
import com.boosters.promise.data.user.UserRepository
import com.boosters.promise.ui.invite.model.UserUiModel
import com.boosters.promise.ui.invite.model.toUser
import com.boosters.promise.ui.promisesetting.model.PromiseSettingEvent
import com.boosters.promise.ui.promisesetting.model.PromiseSettingUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class PromiseSettingViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val promiseRepository: PromiseRepository,
    private val userRepository: UserRepository,
    private val serverKeyRepository: ServerKeyRepository
) : ViewModel() {

    private val _dialogEventFlow = MutableSharedFlow<PromiseSettingEvent>()
    val dialogEventFlow: SharedFlow<PromiseSettingEvent> = _dialogEventFlow.asSharedFlow()

    private val _promiseUiState = MutableStateFlow(Promise())
    val promiseUiState: StateFlow<Promise> = _promiseUiState.asStateFlow()

    private val _promiseSettingUiState = MutableSharedFlow<PromiseSettingUiState>()
    val promiseSettingUiState: SharedFlow<PromiseSettingUiState> =
        _promiseSettingUiState.asSharedFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT)
    private lateinit var myInfo: User

    init {
        viewModelScope.launch {
            userRepository.getMyInfo().first().onSuccess {
                myInfo = it
            }
        }
    }

    fun updateMember(newMemberList: List<UserUiModel>) {
        _promiseUiState.update {
            it.copy(
                members = newMemberList.map { userUiModel ->
                    userUiModel.toUser()
                }
            )
        }
    }

    fun removeMember(removeMember: UserUiModel) {
        _promiseUiState.update {
            it.copy(members = it.members.filter { member -> member.userCode != removeMember.userCode })
        }
    }

    fun onClickCompletionButton() {
        val promise = _promiseUiState.value
        if (promise.title.isEmpty() || promise.time.isEmpty() || promise.destinationName.isEmpty() || promise.date.isEmpty()) {
            changeUiState(PromiseSettingUiState.Fail(R.string.promiseSetting_empty))
            return
        }
        val promiseTime = LocalDateTime.parse("${promise.date} ${promise.time}", dateFormatter)
        if (promiseTime.isBefore(LocalDateTime.now())) {
            changeUiState(PromiseSettingUiState.Fail(R.string.promiseSetting_beforeTime))
            return
        }
        viewModelScope.launch {
            val members = promise.members.toMutableList()
            members.add(myInfo.copy(userToken = ""))
            promiseRepository.addPromise(promise.copy(members = members)).collect {
                when (it) {
                    true -> sendNotification()
                    false -> changeUiState(PromiseSettingUiState.Fail(R.string.promiseSetting_fail))
                }
            }
        }
    }

    fun onClickPickerEditText(event: PromiseSettingEvent) {
        viewModelScope.launch {
            _dialogEventFlow.emit(event)
        }
    }

    fun setPromiseDate(date: String) {
        _promiseUiState.update {
            it.copy(date = date)
        }
    }

    fun setPromiseTime(time: String) {
        _promiseUiState.update {
            it.copy(time = time)
        }
    }

    fun setPromiseDestination(destinationName: String, destinationGeoLocation: GeoLocation) {
        _promiseUiState.update {
            it.copy(
                destinationName = destinationName,
                destinationGeoLocation = destinationGeoLocation
            )
        }
    }

    fun setPromiseTitle(title: String) {
        _promiseUiState.update {
            it.copy(title = title)
        }
    }

    fun initPromise(promise: Promise) {
        _promiseUiState.update {
            val members = promise.members.filter { user -> user.userCode != myInfo.userCode }
            promise.copy(members = members)
        }
    }

    private fun changeUiState(state: PromiseSettingUiState) {
        viewModelScope.launch {
            _promiseSettingUiState.emit(state)
        }
    }

    private fun sendNotification() {
        viewModelScope.launch {
            val userCodeList =
                _promiseUiState.value.members.filter { it.userCode != myInfo.userCode }
                    .map { it.userCode }
            if (userCodeList.isEmpty()) {
                changeUiState(PromiseSettingUiState.Success)
                return@launch
            }
            val key = serverKeyRepository.getServerKey()

            val title = if (_promiseUiState.value.promiseId.isEmpty()) {
                NOTIFICATION_ADD
            } else {
                NOTIFICATION_EDIT
            }

            userRepository.getUserList(userCodeList).collectLatest {
                it.forEach { user ->
                    notificationRepository.sendNotification(
                        title,
                        _promiseUiState.value,
                        user.userToken,
                        key
                    )
                }
                changeUiState(PromiseSettingUiState.Success)
            }
        }
    }

    companion object {
        private const val DATE_FORMAT = "yyyy/MM/dd HH:mm"
        private const val NOTIFICATION_EDIT = "0"
        private const val NOTIFICATION_ADD = "1"
    }

}