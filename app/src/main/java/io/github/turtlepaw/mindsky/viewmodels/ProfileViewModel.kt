package io.github.turtlepaw.mindsky.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.bsky.actor.ProfileViewDetailed
import com.atproto.label.LabelValueDefinition
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.repositories.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Did

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val profile: ProfileViewDetailed) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

class ProfileViewModel(
    private val repository: ProfileRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.sessionFlow.collect { session ->
                if (session == null) {
                    _uiState.value = ProfileUiState.Error("Not logged in")
                    Log.d("ProfileViewModel", "Not logged in")
                } else {
                    fetchProfile(session)
                }
            }
        }
    }

    private suspend fun fetchProfile(session: UserSession) {
        _uiState.value = ProfileUiState.Loading
        try {
            val profile = repository.getProfile(Did(session.did))
            if (profile == null) {
                // If it's null, it might be a cancellation that didn't throw
                // but we'll only set error if the scope is still active
                _uiState.value = ProfileUiState.Error("Profile not found")
                return
            }
            _uiState.value = ProfileUiState.Success(profile)
        } catch (e: Exception) {
            val isCancellation = e is CancellationException || 
                                 e.message?.contains("cancel", ignoreCase = true) == true
            
            if (!isCancellation) {
                _uiState.value = ProfileUiState.Error("Failed to load profile: ${e.message}")
                e.printStackTrace()
            }
            // Rethrow real cancellations for coroutine scope management
            if (e is CancellationException) throw e
        }
    }

    private val _resolvedLabels = MutableStateFlow<List<LabelValueDefinition>>(emptyList())
    val resolvedLabels = _resolvedLabels.asStateFlow()
}

class ProfileViewModelFactory(
    private val repository: ProfileRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}