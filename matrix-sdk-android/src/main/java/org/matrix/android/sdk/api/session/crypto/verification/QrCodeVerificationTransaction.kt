/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.api.session.crypto.verification

enum class QRCodeVerificationState {
    // ie. we started
    Reciprocated,

    // When started/scanned by other side and waiting for confirmation
    // that was really scanned
    WaitingForScanConfirmation,
    WaitingForOtherDone,
    Done,
    Cancelled
}

interface QrCodeVerificationTransaction : VerificationTransaction {

    fun state(): QRCodeVerificationState

    /**
     * To use to display a qr code, for the other user to scan it.
     */
    val qrCodeText: String?

    /**
     * Call when you have scan the other user QR code.
     */
//    suspend fun userHasScannedOtherQrCode(otherQrCodeText: String)

    /**
     * Call when you confirm that other user has scanned your QR code.
     */
    suspend fun otherUserScannedMyQrCode()

    /**
     * Call when you do not confirm that other user has scanned your QR code.
     */
    suspend fun otherUserDidNotScannedMyQrCode()

    override fun isSuccessful() = state() == QRCodeVerificationState.Done
}
