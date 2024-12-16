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

package com.google.samples.apps.nowinandroid.core.data.repository

import androidx.annotation.VisibleForTesting
import com.google.samples.apps.nowinandroid.core.analytics.AnalyticsHelper
import com.google.samples.apps.nowinandroid.core.datastore.NiaPreferencesDataSource
import com.google.samples.apps.nowinandroid.core.model.data.DarkThemeConfig
import com.google.samples.apps.nowinandroid.core.model.data.ThemeBrand
import com.google.samples.apps.nowinandroid.core.model.data.UserData
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class OfflineFirstUserDataRepository @Inject constructor(
    private val niaPreferencesDataSource: NiaPreferencesDataSource,
    private val analyticsHelper: AnalyticsHelper,
) : UserDataRepository {

    // prefereneceDatasource에 저장되어 있는 userData
    override val userData: Flow<UserData> =
        niaPreferencesDataSource.userData

    @VisibleForTesting
    override suspend fun setFollowedTopicIds(followedTopicIds: Set<String>) =
        niaPreferencesDataSource.setFollowedTopicIds(followedTopicIds)

    // 특정 토픽을 팔로우
    override suspend fun setTopicIdFollowed(followedTopicId: String, followed: Boolean) {
        // id, 팔로우 상태를 조작
        niaPreferencesDataSource.setTopicIdFollowed(followedTopicId, followed)
        analyticsHelper.logTopicFollowToggled(followedTopicId, followed)
    }

    // 뉴스 북마크 상태를 조작
    override suspend fun setNewsResourceBookmarked(newsResourceId: String, bookmarked: Boolean) {
        niaPreferencesDataSource.setNewsResourceBookmarked(newsResourceId, bookmarked)
        analyticsHelper.logNewsResourceBookmarkToggled(
            newsResourceId = newsResourceId,
            isBookmarked = bookmarked,
        )
    }

    // 뉴스를 봤는지?
    override suspend fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean) =
        niaPreferencesDataSource.setNewsResourceViewed(newsResourceId, viewed)


    // SettingDialog에서 화면 테마, 색상등 을 적용하는 로직을 preference에 저장

    // default or android
    override suspend fun setThemeBrand(themeBrand: ThemeBrand) {
        niaPreferencesDataSource.setThemeBrand(themeBrand)
        analyticsHelper.logThemeChanged(themeBrand.name)
    }

    // 다크 테마
    override suspend fun setDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        niaPreferencesDataSource.setDarkThemeConfig(darkThemeConfig)
        analyticsHelper.logDarkThemeConfigChanged(darkThemeConfig.name)
    }

    // dynamicColor 설정
    override suspend fun setDynamicColorPreference(useDynamicColor: Boolean) {
        niaPreferencesDataSource.setDynamicColorPreference(useDynamicColor)
        analyticsHelper.logDynamicColorPreferenceChanged(useDynamicColor)
    }

    override suspend fun setShouldHideOnboarding(shouldHideOnboarding: Boolean) {
        niaPreferencesDataSource.setShouldHideOnboarding(shouldHideOnboarding)
        analyticsHelper.logOnboardingStateChanged(shouldHideOnboarding)
    }
    ///
}
