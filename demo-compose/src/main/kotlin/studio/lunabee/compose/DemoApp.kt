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

package studio.lunabee.compose

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import studio.lunabee.compose.demo.presenter.generatedReducerFactoryModule
import studio.lunabee.compose.demo.presenter.timer.TimerInjectedParam
import studio.lunabee.compose.demo.presenter.timer.TimerKoinPresenter

@HiltAndroidApp
class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            modules(
                module {
                    single { TimerInjectedParam(prefix = "Current time = ") }
                    viewModelOf(::TimerKoinPresenter)
                },
                generatedReducerFactoryModule,
            )
        }
    }
}
