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

package com.google.samples.apps.nowinandroid.core.data.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.tracing.trace
import com.google.samples.apps.nowinandroid.core.network.Dispatcher
import com.google.samples.apps.nowinandroid.core.network.NiaDispatchers.IO
import com.google.samples.apps.nowinandroid.core.network.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinTimeZone
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility for reporting current timezone the device has set.
 * It always emits at least once with default setting and then for each TZ change.
 */
// 추상화
interface TimeZoneMonitor {
    val currentTimeZone: Flow<TimeZone>
}

// 실 객체
// 싱글턴으로 생성
// 앱 내 전반적으로 사용되기에..
@Singleton
internal class TimeZoneBroadcastMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    // 애플리케이션 전체 스코프를 보고 있음 -> 전역에서 사용
    @ApplicationScope appScope: CoroutineScope,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : TimeZoneMonitor {

    override val currentTimeZone: SharedFlow<TimeZone> =
        callbackFlow {
            // Send the default time zone first.
            // callback 안에서 flow를 전달
            trySend(TimeZone.currentSystemDefault())

            // Registers BroadcastReceiver for the TimeZone changes
            // 시간대가 변경될 때 사용될 리시버를 등록
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return

                    val zoneIdFromIntent = if (VERSION.SDK_INT < VERSION_CODES.R) {
                        null
                    } else {
                        // Starting Android R we also get the new TimeZone.
                        intent.getStringExtra(Intent.EXTRA_TIMEZONE)?.let { timeZoneId ->
                            // We need to convert it from java.util.Timezone to java.time.ZoneId
                            val zoneId = ZoneId.of(timeZoneId, ZoneId.SHORT_IDS)
                            // Convert to kotlinx.datetime.TimeZone
                            zoneId.toKotlinTimeZone()
                        }
                    }

                    // If there isn't a zoneId in the intent, fallback to the systemDefault, which should also reflect the change
                    // 다시 전달
                    trySend(zoneIdFromIntent ?: TimeZone.currentSystemDefault())
                }
            }

            trace("TimeZoneBroadcastReceiver.register") {
                // 브로드캐스트 리시버 등록
                context.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED))
            }

            // Send here again, because registering the Broadcast Receiver can take up to several milliseconds.
            // This way, we can reduce the likelihood that a TZ change wouldn't be caught with the Broadcast Receiver.
            trySend(TimeZone.currentSystemDefault())

            // flow가 종료될 때 실행되는 콜백
            awaitClose {
                // 브로드캐스트 리시버 해제 (메모리 누수 방지)
                context.unregisterReceiver(receiver)
            }
        }
            // We use to prevent multiple emissions of the same type, because we use trySend multiple times.
            .distinctUntilChanged()
            .conflate()
            .flowOn(ioDispatcher)
            // Sharing the callback to prevent multiple BroadcastReceivers being registered
            // flow (cold Stream) -> sharedFlow (hot stream) (중간에 중지되도 초기화를 하지 않음)
            // 단일로 실행되는 업스트림 플로우에서 발행되는 데이터를 여러개의 다운스트림 구독자들이 공유하는 형태
            // replay 값에 따라 새로운 구독자들에게 값을 다시 발행
            // WhileSubscribed -> 첫번째 구독자가 생긴 후에 업스트림이 시작, 마지막 구독자가 사라지면 즉시 멈추게 되며, replay 된 캐시를 영원히 보존하게 된다.
            .shareIn(appScope, SharingStarted.WhileSubscribed(5_000), 1)
}
