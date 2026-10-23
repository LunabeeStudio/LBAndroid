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

package studio.lunabee.parse.error

/**
 * Parse error codes, to branch on `LBParseException.Parse.code` without magic numbers.
 */
object ParseErrorCode {
    const val ConnectionFailed: Int = 100
    const val ObjectNotFound: Int = 101
    const val InvalidQuery: Int = 102
    const val InvalidClassName: Int = 103
    const val MissingObjectId: Int = 104
    const val InvalidKeyName: Int = 105
    const val InvalidPointer: Int = 106
    const val InvalidJson: Int = 107
    const val CommandUnavailable: Int = 108
    const val NotInitialized: Int = 109
    const val IncorrectType: Int = 111
    const val ObjectTooLarge: Int = 116
    const val OperationForbidden: Int = 119
    const val InvalidFileName: Int = 122
    const val InvalidAcl: Int = 123
    const val Timeout: Int = 124
    const val InvalidEmailAddress: Int = 125
    const val DuplicateValue: Int = 137
    const val ScriptFailed: Int = 141
    const val ValidationFailed: Int = 142
    const val RequestLimitExceeded: Int = 155
    const val UsernameMissing: Int = 200
    const val PasswordMissing: Int = 201
    const val UsernameTaken: Int = 202
    const val EmailTaken: Int = 203
    const val EmailMissing: Int = 204
    const val EmailNotFound: Int = 205
    const val SessionMissing: Int = 206
    const val MustCreateUserThroughSignUp: Int = 207
    const val AccountAlreadyLinked: Int = 208
    const val InvalidSessionToken: Int = 209
}
