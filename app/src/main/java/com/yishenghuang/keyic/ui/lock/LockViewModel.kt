package com.yishenghuang.keyic.ui.lock

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yishenghuang.keyic.R
import com.yishenghuang.keyic.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LockUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val vaultName: String = "",
    @StringRes val errorRes: Int? = null,
    val errorDetail: String? = null,
    val busy: Boolean = false,
    val biometricAvailable: Boolean = false,
)

class LockViewModel(
    application: Application,
    private val container: AppContainer,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(
        LockUiState(vaultName = application.getString(R.string.vault_default_numbered)),
    )
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, errorRes = null, errorDetail = null) }

    fun onConfirmChange(value: String) =
        _state.update { it.copy(confirmPassword = value, errorRes = null, errorDetail = null) }

    fun onVaultNameChange(value: String) =
        _state.update { it.copy(vaultName = value, errorRes = null, errorDetail = null) }

    fun setup() {
        if (_state.value.busy) return
        val password = _state.value.password
        val confirm = _state.value.confirmPassword
        val fallback = getApplication<Application>().getString(R.string.vault_default_numbered)
        val vaultName = _state.value.vaultName.trim().ifEmpty { fallback }
        if (password.length < 8) {
            _state.update { it.copy(errorRes = R.string.error_password_too_short) }
            return
        }
        if (password != confirm) {
            _state.update { it.copy(errorRes = R.string.error_passwords_mismatch) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errorRes = null, errorDetail = null) }
            try {
                withContext(Dispatchers.Default) {
                    container.vaultSession.setup(password.toCharArray(), vaultName)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        errorRes = R.string.error_setup_failed,
                        errorDetail = null,
                        busy = false,
                    )
                }
                return@launch
            }
            _state.update { it.copy(busy = false, password = "", confirmPassword = "") }
        }
    }

    fun unlock() {
        if (_state.value.busy) return
        val password = _state.value.password
        if (password.isEmpty()) {
            _state.update { it.copy(errorRes = R.string.error_enter_master_password) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errorRes = null, errorDetail = null) }
            val ok = try {
                withContext(Dispatchers.Default) {
                    container.vaultSession.unlock(password.toCharArray())
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.error_vault_open, password = "") }
                return@launch
            }
            if (!ok) {
                _state.update { it.copy(busy = false, errorRes = R.string.error_wrong_password) }
            } else {
                _state.update { it.copy(busy = false, password = "") }
            }
        }
    }

    fun biometricFailed() {
        _state.update { it.copy(errorRes = R.string.error_biometric_failed, busy = false) }
    }

    fun unlockWithDbKey(dbKey: ByteArray) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, errorRes = null, errorDetail = null) }
            try {
                withContext(Dispatchers.Default) {
                    container.vaultSession.unlockWithBiometricKey(dbKey)
                }
                _state.update { it.copy(busy = false, password = "") }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(busy = false, errorRes = R.string.error_vault_open) }
            } finally {
                dbKey.fill(0)
            }
        }
    }
}

class LockViewModelFactory(
    private val application: Application,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LockViewModel(application, container) as T
    }
}
