/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.nowinandroid.feature.bookmarks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState.Loading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
    userNewsResourceRepository: UserNewsResourceRepository,
) : ViewModel() {

    var shouldDisplayUndoBookmark by mutableStateOf(false)
    private var lastRemovedBookmarkId: String? = null

    // 북마크된 피드의 상태
    val feedUiState: StateFlow<NewsFeedUiState> =
        // 유저 정보에서 북마크된 것들을 모두 가져옴
        userNewsResourceRepository.observeAllBookmarked()
            // NewsFeedUiState 형태로 변황
            .map<List<UserNewsResource>, NewsFeedUiState>(NewsFeedUiState::Success)
            // flow가 수집 되기 전에는 State.Loading을 방출
            .onStart { emit(Loading) }
            // StateFlow로 변환
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Loading,
            )

    // 북마크 삭제
    fun removeFromSavedResources(newsResourceId: String) {
        viewModelScope.launch {
            // 북마크 삭제 할때 UI를 보여주기 위해 (삭제할거냐는)
            shouldDisplayUndoBookmark = true
            lastRemovedBookmarkId = newsResourceId
            // 북마크 삭제 -> 데이터 레이어로 전송
            userDataRepository.setNewsResourceBookmarked(newsResourceId, false)
        }
    }

    fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean) {
        viewModelScope.launch {
            userDataRepository.setNewsResourceViewed(newsResourceId, viewed)
        }
    }

    // 북마크 취소를 취소
    fun undoBookmarkRemoval() {
        viewModelScope.launch {
            // 마지막으로 제거된 북마크의 고유값에 대해서
            lastRemovedBookmarkId?.let {
                // 북마크 상태를 true로 업데이트함
                userDataRepository.setNewsResourceBookmarked(it, true)
            }
        }
        clearUndoState()
    }

    // 북마크 모든 상탤,ㄹ 초기화
    fun clearUndoState() {
        shouldDisplayUndoBookmark = false
        lastRemovedBookmarkId = null
    }
}
