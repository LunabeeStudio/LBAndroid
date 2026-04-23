/*
 * Copyright (c) 2026 Lunabee Studio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package studio.lunabee.monitoring.room.di

import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.component.KoinComponent
import org.koin.dsl.koinApplication
import studio.lunabee.monitoring.room.dao.RoomRequestDao

@Module
@ComponentScan("studio.lunabee.monitoring.room")
internal class RoomMonitoringModule

internal object RoomMonitoringKoinProvider : RoomMonitoringIsolatedComponent {
    val requestDao: RoomRequestDao
        get() = getKoin().get()
}

internal object RoomMonitoringIsolatedContext {
    private var koinApp: KoinApplication? = null

    val koin: Koin
        get() = koinApp?.koin ?: throw IllegalArgumentException("KoinApp not initialized")

    fun init(block: KoinApplication.() -> Unit) {
        koinApp = koinApplication {
            block()
            modules(RoomMonitoringModule().module())
        }
    }
}

internal interface RoomMonitoringIsolatedComponent : KoinComponent {
    override fun getKoin(): Koin = RoomMonitoringIsolatedContext.koin
}
