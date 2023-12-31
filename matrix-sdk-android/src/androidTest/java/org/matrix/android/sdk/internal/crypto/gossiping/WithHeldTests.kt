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

package org.matrix.android.sdk.internal.crypto.gossiping

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.matrix.android.sdk.InstrumentedTest
import org.matrix.android.sdk.api.crypto.MXCryptoConfig
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.crypto.MXCryptoError
import org.matrix.android.sdk.api.session.crypto.RequestResult
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.events.model.content.EncryptedEventContent
import org.matrix.android.sdk.api.session.events.model.content.WithHeldCode
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.room.getTimelineEvent
import org.matrix.android.sdk.common.CommonTestHelper.Companion.runCryptoTest
import org.matrix.android.sdk.common.MockOkHttpInterceptor
import org.matrix.android.sdk.common.RetryTestRule
import org.matrix.android.sdk.common.SessionTestParams
import org.matrix.android.sdk.common.TestConstants
import org.matrix.android.sdk.mustFail

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.JVM)
@LargeTest
class WithHeldTests : InstrumentedTest {

    @get:Rule val rule = RetryTestRule(3)

    @Test
    fun test_WithHeldUnverifiedReason() = runCryptoTest(context()) { cryptoTestHelper, testHelper ->

        // =============================
        // ARRANGE
        // =============================

        val aliceSession = testHelper.createAccount(TestConstants.USER_ALICE, SessionTestParams(true))
        val bobSession = testHelper.createAccount(TestConstants.USER_BOB, SessionTestParams(true))

        // Initialize cross signing on both
        cryptoTestHelper.initializeCrossSigning(aliceSession)
        cryptoTestHelper.initializeCrossSigning(bobSession)

        val roomId = cryptoTestHelper.createDM(aliceSession, bobSession)
        cryptoTestHelper.verifySASCrossSign(aliceSession, bobSession, roomId)

        val roomAlicePOV = aliceSession.getRoom(roomId)!!

        val bobUnverifiedSession = testHelper.logIntoAccount(bobSession.myUserId, SessionTestParams(true))

        // =============================
        // ACT
        // =============================

        // Alice decide to not send to unverified sessions
        aliceSession.cryptoService().setGlobalBlacklistUnverifiedDevices(true)

        val eventId = testHelper.sendMessageInRoom(roomAlicePOV, "Hello Bob")

        // await for bob unverified session to get the message
        testHelper.retryWithBackoff {
            bobUnverifiedSession.getRoom(roomId)?.getTimelineEvent(eventId) != null
        }

        val eventBobPOV = bobUnverifiedSession.getRoom(roomId)?.getTimelineEvent(eventId)!!

        val megolmSessionId = eventBobPOV.root.content.toModel<EncryptedEventContent>()!!.sessionId!!
        // =============================
        // ASSERT
        // =============================

        // Bob should not be able to decrypt because the keys is withheld
        // .. might need to wait a bit for stability?
        // WILL FAIL for rust until this fixed https://github.com/matrix-org/matrix-rust-sdk/issues/1806
        mustFail(
                message = "This session should not be able to decrypt",
                failureBlock = { failure ->
                    val type = (failure as MXCryptoError.Base).errorType
                    val technicalMessage = failure.technicalMessage
                    Assert.assertEquals("Error should be withheld", MXCryptoError.ErrorType.KEYS_WITHHELD, type)
                    Assert.assertEquals("Cause should be unverified", WithHeldCode.UNVERIFIED.value, technicalMessage)
                }
        ) {
            bobUnverifiedSession.cryptoService().decryptEvent(eventBobPOV.root, "")
        }

        if (bobUnverifiedSession.cryptoService().supportKeyRequestInspection()) {
            // Let's see if the reply we got from bob first session is unverified
            testHelper.retryWithBackoff {
                bobUnverifiedSession.cryptoService().getOutgoingRoomKeyRequests()
                        .firstOrNull { it.sessionId == megolmSessionId }
                        ?.results
                        ?.firstOrNull { it.fromDevice == bobSession.sessionParams.deviceId }
                        ?.result
                        ?.let {
                            it as? RequestResult.Failure
                        }
                        ?.code == WithHeldCode.UNVERIFIED
            }
        }
        // enable back sending to unverified
        aliceSession.cryptoService().setGlobalBlacklistUnverifiedDevices(false)

        val secondEventId = testHelper.sendMessageInRoom(roomAlicePOV, "Verify your device!!")

        testHelper.retryWithBackoff {
            val ev = bobUnverifiedSession.getRoom(roomId)?.getTimelineEvent(secondEventId)
            // wait until it's decrypted
            ev?.root?.getClearType() == EventType.MESSAGE
        }

        // Previous message should still be undecryptable (partially withheld session)
        // .. might need to wait a bit for stability?
        mustFail(
                message = "This session should not be able to decrypt",
                failureBlock = { failure ->
                    val type = (failure as MXCryptoError.Base).errorType
                    val technicalMessage = failure.technicalMessage
                    Assert.assertEquals("Error should be withheld", MXCryptoError.ErrorType.KEYS_WITHHELD, type)
                    Assert.assertEquals("Cause should be unverified", WithHeldCode.UNVERIFIED.value, technicalMessage)
                }) {
            bobUnverifiedSession.cryptoService().decryptEvent(eventBobPOV.root, "")
        }
    }

    @Test
    @Ignore("ignore NoOlm for now, implementation not correct")
    fun test_WithHeldNoOlm() = runCryptoTest(
            context(),
            cryptoConfig = MXCryptoConfig(limitRoomKeyRequestsToMyDevices = false)
    ) { cryptoTestHelper, testHelper ->

        val testData = cryptoTestHelper.doE2ETestWithAliceAndBobInARoom()
        val aliceSession = testData.firstSession
        Assume.assumeTrue("Not supported", aliceSession.cryptoService().supportKeyRequestInspection())
        val bobSession = testData.secondSession!!
        val aliceInterceptor = testHelper.getTestInterceptor(aliceSession)

        // Simulate no OTK
        aliceInterceptor!!.addRule(MockOkHttpInterceptor.SimpleRule(
                "/keys/claim",
                200,
                """
                   { "one_time_keys" : {} } 
                """
        ))
        Log.d("#TEST", "Recovery :${aliceSession.sessionParams.credentials.accessToken}")

        val roomAlicePov = aliceSession.getRoom(testData.roomId)!!

        val eventId = testHelper.sendMessageInRoom(roomAlicePov, "first message")

        // await for bob session to get the message
        testHelper.retryWithBackoff {
            bobSession.getRoom(testData.roomId)?.getTimelineEvent(eventId) != null
        }

        // Previous message should still be undecryptable (partially withheld session)
        val eventBobPOV = bobSession.getRoom(testData.roomId)?.getTimelineEvent(eventId)
        // .. might need to wait a bit for stability?
        mustFail(
                message = "This session should not be able to decrypt",
                failureBlock = { failure ->
                    val type = (failure as MXCryptoError.Base).errorType
                    val technicalMessage = failure.technicalMessage
                    Assert.assertEquals("Error should be withheld", MXCryptoError.ErrorType.KEYS_WITHHELD, type)
                    Assert.assertEquals("Cause should be unverified", WithHeldCode.NO_OLM.value, technicalMessage)
                }) {
            bobSession.cryptoService().decryptEvent(eventBobPOV!!.root, "")
        }

        // Ensure that alice has marked the session to be shared with bob
        val sessionId = eventBobPOV!!.root.content.toModel<EncryptedEventContent>()!!.sessionId!!
        val chainIndex = aliceSession.cryptoService().getSharedWithInfo(testData.roomId, sessionId).getObject(bobSession.myUserId, bobSession.sessionParams.credentials.deviceId)

        Assert.assertEquals("Alice should have marked bob's device for this session", 0, chainIndex)
        // Add a new device for bob

        aliceInterceptor.clearRules()
        val bobSecondSession = testHelper.logIntoAccount(bobSession.myUserId, SessionTestParams(withInitialSync = true))
        // send a second message
        val secondMessageId = testHelper.sendTextMessage(roomAlicePov, "second message", 1).first().eventId

        // Check that the
        // await for bob SecondSession session to get the message
        testHelper.retryPeriodically {
            bobSecondSession.getRoom(testData.roomId)?.getTimelineEvent(secondMessageId) != null
        }

        val chainIndex2 = aliceSession.cryptoService().getSharedWithInfo(testData.roomId, sessionId).getObject(bobSecondSession.myUserId, bobSecondSession.sessionParams.credentials.deviceId)

        Assert.assertEquals("Alice should have marked bob's device for this session", 1, chainIndex2)

        aliceInterceptor.clearRules()
    }

    @Test
    @Ignore("Outdated test, we don't request to others")
    fun test_WithHeldKeyRequest() = runCryptoTest(
            context(),
            cryptoConfig = MXCryptoConfig(limitRoomKeyRequestsToMyDevices = false)
    ) { cryptoTestHelper, testHelper ->

        val testData = cryptoTestHelper.doE2ETestWithAliceAndBobInARoom()
        val aliceSession = testData.firstSession
        Assume.assumeTrue("Not supported by rust sdk", aliceSession.cryptoService().supportsForwardedKeyWiththeld())
        val bobSession = testData.secondSession!!

        val roomAlicePov = aliceSession.getRoom(testData.roomId)!!

        val eventId = testHelper.sendTextMessage(roomAlicePov, "first message", 1).first().eventId

        testHelper.signOutAndClose(bobSession)

        // Create a new session for bob

        val bobSecondSession = testHelper.logIntoAccount(bobSession.myUserId, SessionTestParams(true))
        // initialize to force request keys if missing
        cryptoTestHelper.initializeCrossSigning(bobSecondSession)

        // Trust bob second device from Alice POV
        aliceSession.cryptoService().crossSigningService().trustDevice(bobSecondSession.sessionParams.deviceId)
        bobSecondSession.cryptoService().crossSigningService().trustDevice(aliceSession.sessionParams.deviceId)

        var sessionId: String? = null
        // Check that the
        // await for bob SecondSession session to get the message
        testHelper.retryPeriodically {
            val timeLineEvent = bobSecondSession.getRoom(testData.roomId)?.getTimelineEvent(eventId)?.also {
                // try to decrypt and force key request
                tryOrNull {
                    bobSecondSession.cryptoService().decryptEvent(it.root, "")
                }
            }
            sessionId = timeLineEvent?.root?.content?.toModel<EncryptedEventContent>()?.sessionId
            timeLineEvent != null
        }

        // Check that bob second session requested the key
        testHelper.retryPeriodically {
            val wc = bobSecondSession.cryptoService().getWithHeldMegolmSession(roomAlicePov.roomId, sessionId!!)
            wc?.code == WithHeldCode.UNAUTHORISED
        }
//        // Check that bob second session requested the key
//        testHelper.retryPeriodically {
//            val wc = bobSecondSession.cryptoService().getWithHeldMegolmSession(roomAlicePov.roomId, sessionId!!)
//            wc?.code == WithHeldCode.UNAUTHORISED
//        }
    }
}
