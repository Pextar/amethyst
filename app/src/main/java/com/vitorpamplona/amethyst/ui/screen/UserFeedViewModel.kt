package com.vitorpamplona.amethyst.ui.screen

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowersDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowsDataSource
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NostrUserProfileFollowsUserFeedViewModel(): UserFeedViewModel(
    NostrUserProfileFollowsDataSource
)

class NostrUserProfileFollowersUserFeedViewModel(): UserFeedViewModel(
    NostrUserProfileFollowersDataSource
)

open class UserFeedViewModel(val dataSource: NostrDataSource<User>): ViewModel() {
    private val _feedContent = MutableStateFlow<UserFeedState>(UserFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    fun refresh() {
        // For some reason, view Model Scope doesn't call
        viewModelScope.launch {
            refreshSuspend()
        }
    }

    fun refreshSuspend() {
        val notes = dataSource.loadTop()

        val oldNotesState = feedContent.value
        if (oldNotesState is UserFeedState.Loaded) {
            if (notes != oldNotesState.feed) {
                updateFeed(notes)
            }
        } else {
            updateFeed(notes)
        }
    }

    fun updateFeed(notes: List<User>) {
        if (notes.isEmpty()) {
            _feedContent.update { UserFeedState.Empty }
        } else {
            _feedContent.update { UserFeedState.Loaded(notes) }
        }
    }

    fun refreshCurrentList() {
        val state = feedContent.value
        if (state is UserFeedState.Loaded) {
            _feedContent.update { UserFeedState.Loaded(state.feed) }
        }
    }

    val filterHandler = Handler(Looper.getMainLooper())
    var handlerWaiting = false
    @Synchronized
    fun invalidateData() {
        if (handlerWaiting) return

        handlerWaiting = true
        filterHandler.postDelayed({
            refresh()
            handlerWaiting = false
        }, 100)
    }

    private val cacheListener: (LocalCacheState) -> Unit = {
        invalidateData()
    }

    init {
        LocalCache.live.observeForever(cacheListener)
    }

    override fun onCleared() {
        LocalCache.live.removeObserver(cacheListener)

        dataSource.stop()
        viewModelScope.cancel()
        super.onCleared()
    }
}