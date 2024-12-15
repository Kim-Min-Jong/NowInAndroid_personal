/*
 * Copyright 2024 The Android Open Source Project
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

package com.google.samples.apps.nowinandroid.ui.interests2pane

import androidx.activity.compose.BackHandler
import androidx.annotation.Keep
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.samples.apps.nowinandroid.feature.interests.InterestsRoute
import com.google.samples.apps.nowinandroid.feature.interests.navigation.InterestsRoute
import com.google.samples.apps.nowinandroid.feature.topic.TopicDetailPlaceholder
import com.google.samples.apps.nowinandroid.feature.topic.navigation.TopicRoute
import com.google.samples.apps.nowinandroid.feature.topic.navigation.navigateToTopic
import com.google.samples.apps.nowinandroid.feature.topic.navigation.topicScreen
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable internal object TopicPlaceholderRoute

// TODO: Remove @Keep when https://issuetracker.google.com/353898971 is fixed
@Serializable internal object DetailPaneNavHostRoute

fun NavGraphBuilder.interestsListDetailScreen() {
    composable<InterestsRoute> {
        InterestsListDetailScreen()
    }
}

// 관심 정보 리스트 화면
@Composable
internal fun InterestsListDetailScreen(
    viewModel: Interests2PaneViewModel = hiltViewModel(),
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
) {
    val selectedTopicId by viewModel.selectedTopicId.collectAsStateWithLifecycle()
    InterestsListDetailScreen(
        // 선택된 토픽의 고유값
        selectedTopicId = selectedTopicId,
        // 선택시 실행될 로직
        onTopicClick = viewModel::onTopicClick,
        // 화면 크기 정보 (적응형)
        windowAdaptiveInfo = windowAdaptiveInfo,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun InterestsListDetailScreen(
    selectedTopicId: String?, // 선택된 주제 ID (없을 수도 있음)
    onTopicClick: (String) -> Unit, // 주제를 클릭했을 때 호출되는 콜백 함수
    windowAdaptiveInfo: WindowAdaptiveInfo, // 화면 크기나 장치 적응 정보를 포함하는 객체
) {

    // ListDetailPaneScaffold의 네비게이터를 생성
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator(
        scaffoldDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo), // 화면 크기에 따라 리스트와 상세보기의 동작 설정
        initialDestinationHistory = listOfNotNull(
            // 초기 화면 히스토리를 설정 (리스트와 상세보기 패널을 조건부로 추가)
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List), // 리스트 패널 추가
            // 선택된 주제가 있을 때만 상세보기 패널 추가
            ThreePaneScaffoldDestinationItem<Nothing>(ListDetailPaneScaffoldRole.Detail).takeIf {
                selectedTopicId != null
            },
        ),
    )

    // 뒤로가기 버튼 처리
    BackHandler(listDetailNavigator.canNavigateBack()) {
        // 뒤로가기 가능할 때 이전 화면으로 이동
        listDetailNavigator.navigateBack()
    }

    // 네비게이션 시작 경로를 설정하는 상태를 기억
    var nestedNavHostStartRoute by remember {
        val route = selectedTopicId?.let { TopicRoute(id = it) } ?: TopicPlaceholderRoute // 선택된 주제가 없으면 기본 경로 사용
        mutableStateOf(route)
    }

    // 네비게이션 키를 저장하고 관리 (화면 갱신 시 새로 생성)
    var nestedNavKey by rememberSaveable(
        stateSaver = Saver({ it.toString() }, UUID::fromString), // UUID를 문자열로 저장하고 복원
    ) {
        mutableStateOf(UUID.randomUUID()) // 초기 UUID 생성
    }

    // 네비게이션 컨트롤러를 생성 (키를 기반으로 새로고침 가능)
    val nestedNavController = key(nestedNavKey) {
        rememberNavController()
    }

    // 주제를 클릭했을 때 상세보기 패널로 전환하는 로직
    fun onTopicClickShowDetailPane(topicId: String) {
        onTopicClick(topicId) // 외부로 전달되는 콜백 호출
        // 상세보기 패널이 이미 보이는 경우
        if (listDetailNavigator.isDetailPaneVisible()) {
            // 이전 경로를 제거하고 새 경로로 이동
            nestedNavController.navigateToTopic(topicId) {
                popUpTo<DetailPaneNavHostRoute>()
            }
        } else {
            // 상세보기 패널이 보이지 않는 경우, 새로운 네비게이션 경로로 시작

            // 새로운 시작 경로 설정
            nestedNavHostStartRoute = TopicRoute(id = topicId)
            nestedNavKey = UUID.randomUUID() // 새 키 생성
        }
        listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail) // 상세보기 패널로 이동
    }

    // List-Detail UI를 구성하는 Scaffold
    ListDetailPaneScaffold(
        value = listDetailNavigator.scaffoldValue, // 현재 Scaffold의 상태 값
        directive = listDetailNavigator.scaffoldDirective, // 화면 크기와 설정에 따른 지침
        // 리스트 패널 정의 (상위 패널)
        listPane = {

            AnimatedPane {
                InterestsRoute(
                    onTopicClick = ::onTopicClickShowDetailPane, // 주제 클릭 시 상세보기로 전환
                    highlightSelectedTopic = listDetailNavigator.isDetailPaneVisible(), // 상세보기 패널이 보이는 경우 강조 표시
                )
            }
        },
        // 상세보기 패널 정의 (하위 패널)
        detailPane = {

            AnimatedPane {
                key(nestedNavKey) { // 키가 변경되면 NavHost 재생성
                    NavHost(
                        navController = nestedNavController, // 네비게이션 컨트롤러 연결
                        startDestination = nestedNavHostStartRoute, // 시작 경로 설정
                        route = DetailPaneNavHostRoute::class, // 네비게이션의 라우트 정의
                    ) {
                        topicScreen(
                            showBackButton = !listDetailNavigator.isListPaneVisible(), // 리스트 패널이 보이지 않을 때만 뒤로가기 버튼 표시
                            onBackClick = listDetailNavigator::navigateBack, // 뒤로가기 클릭 시 네비게이션 상태 변경
                            onTopicClick = ::onTopicClickShowDetailPane, // 주제 클릭 처리
                        )
                        composable<TopicPlaceholderRoute> {
                            TopicDetailPlaceholder() // 상세보기 패널이 비어 있을 때의 기본 UI
                        }
                    }
                }
            }
        },
    )
}


@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun <T> ThreePaneScaffoldNavigator<T>.isListPaneVisible(): Boolean =
    scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun <T> ThreePaneScaffoldNavigator<T>.isDetailPaneVisible(): Boolean =
    scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
