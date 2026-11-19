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

package studio.lunabee.feedback.core.config

/**
 * Settings the host app gives to the feedback library.
 *
 * A library cannot read the app's build configuration, so the app passes it here.
 *
 * @property appVersionName version name of the host app
 * @property appVersionCode version code of the host app
 * @property buildVariant build variant of the host app
 * @property extras extra fields added to the metadata of each feedback, for example the current route. Must never
 * return user data.
 */
class LBFeedbackConfig(
    val appVersionName: String,
    val appVersionCode: Long,
    val buildVariant: String,
    val extras: suspend () -> Map<String, String> = { emptyMap() },
)
