/*
 * Copyright (c) 2020 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.crypto.verification

import im.vector.app.core.platform.VectorViewEvents

/**
 * Transient events for the verification bottom sheet.
 */
sealed class VerificationBottomSheetViewEvents : VectorViewEvents {
    object Dismiss : VerificationBottomSheetViewEvents()
    object DismissAndOpenDeviceSettings : VerificationBottomSheetViewEvents()
    object AccessSecretStore : VerificationBottomSheetViewEvents()
    object ResetAll : VerificationBottomSheetViewEvents()
    object GoToSettings : VerificationBottomSheetViewEvents()
    data class ConfirmCancel(val otherUserId: String, val deviceId: String?) : VerificationBottomSheetViewEvents()
    data class ModalError(val errorMessage: CharSequence) : VerificationBottomSheetViewEvents()
    data class RequestNotFound(val transactionId: String) : VerificationBottomSheetViewEvents()
}
