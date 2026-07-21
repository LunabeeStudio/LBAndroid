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

package studio.lunabee.compose.demo.synchronization

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import studio.lunabee.compose.demo.synchronization.persistence.FakeRemoteServerDatabase
import studio.lunabee.compose.demo.synchronization.persistence.ServerItemDao
import studio.lunabee.synchronization.connectivity.LBConnectivityManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncDemoModule {

    @Provides
    @Singleton
    fun provideFakeRemoteServerDatabase(@ApplicationContext context: Context): FakeRemoteServerDatabase =
        Room.databaseBuilder(
            context = context,
            klass = FakeRemoteServerDatabase::class.java,
            name = FakeRemoteServerDatabase.DatabaseName,
        ).build()

    @Provides
    @Singleton
    fun provideServerItemDao(database: FakeRemoteServerDatabase): ServerItemDao = database.serverItemDao()

    @Provides
    @Singleton
    fun provideFakeRemoteServer(
        @ApplicationContext context: Context,
        dao: ServerItemDao,
    ): FakeRemoteServer = FakeRemoteServer(
        dao = dao,
        isOnline = { LBConnectivityManager.getNetworkState(context).isConnected },
    )

    @Provides
    @Singleton
    fun provideLocalItemDatabase(): LocalItemDatabase = LocalItemDatabase()

    @Provides
    @Singleton
    fun provideDemoItemSyncManager(
        localDb: LocalItemDatabase,
        server: FakeRemoteServer,
    ): DemoItemSyncManager = DemoItemSyncManager(localDb = localDb, server = server)
}
