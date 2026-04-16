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

package studio.lunabee.core.model

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class LBResultTest {

    @Test
    fun firstFailure_test() {
        val success1 = LBResult.Success("Success 1")
        val failure1 = LBResult.Failure<String>("Failure 1")
        val success2 = LBResult.Success("Success 2")
        val failure2 = LBResult.Failure<String>("Failure 2")

        val list = listOf(success1, failure1, success2, failure2)
        val firstFailure = list.firstFailure()
        assertSame(failure1, firstFailure)
    }

    @Test
    fun firstFailure_no_failure_test() {
        val success1 = LBResult.Success("Success 1")
        val success2 = LBResult.Success("Success 2")

        val list = listOf(success1, success2)
        val firstFailure = list.firstFailure()
        assertNull(firstFailure)
    }
}
