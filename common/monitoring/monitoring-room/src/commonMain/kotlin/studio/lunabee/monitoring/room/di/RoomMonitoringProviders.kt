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

import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Singleton
import studio.lunabee.monitoring.room.RoomMonitoringDatabase
import studio.lunabee.monitoring.room.RoomPlatformBuilder
import studio.lunabee.monitoring.room.dao.RoomRequestDao
import studio.lunabee.monitoring.room.getRoomDb

@Singleton
internal fun provideRoomMonitoringDatabase(
    @Provided builder: RoomPlatformBuilder,
    @Provided dispatcher: CoroutineDispatcher,
): RoomMonitoringDatabase = getRoomDb(builder = builder, dispatcher = dispatcher)

@Singleton
internal fun provideRoomRequestDao(database: RoomMonitoringDatabase): RoomRequestDao = database.requestDao()
