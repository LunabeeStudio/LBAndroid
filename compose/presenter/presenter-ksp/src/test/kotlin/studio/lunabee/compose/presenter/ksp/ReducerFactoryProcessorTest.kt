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

package studio.lunabee.compose.presenter.ksp

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReducerFactoryProcessorTest {
    @Test
    fun should_recognize_concrete_reducer_class_test() {
        assertTrue(
            isConcreteReducerClass(
                classKind = ClassKind.CLASS,
                modifiers = emptySet(),
            ),
        )
    }

    @Test
    fun should_reject_abstract_reducer_class_test() {
        assertFalse(
            isConcreteReducerClass(
                classKind = ClassKind.CLASS,
                modifiers = setOf(Modifier.ABSTRACT),
            ),
        )
    }

    @Test
    fun resolve_named_qualifier_supports_typed_koin_named_test() {
        assertEquals(
            DiQualifier.Typed(ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifier")),
            resolveNamedQualifier(
                value = null,
                type = ClassName("studio.lunabee.compose.demo.presenter.timer", "TimerQualifier"),
            ),
        )
    }

    @Test
    fun resolve_named_qualifier_rejects_empty_named_qualifier_without_type_test() {
        val exception = assertFailsWith<InvalidReducerFactoryException> {
            resolveNamedQualifier(
                value = "",
                type = ClassName("kotlin", "Unit"),
            )
        }

        assertEquals("@Named qualifier must declare a non-empty String value or a type", exception.message)
    }
}
