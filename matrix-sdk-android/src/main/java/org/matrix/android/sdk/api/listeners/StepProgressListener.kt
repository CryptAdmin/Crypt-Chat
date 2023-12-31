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

package org.matrix.android.sdk.api.listeners

/**
 * Interface to send a progress info.
 */
interface StepProgressListener {

    sealed class Step {
        data class ComputingKey(val progress: Int, val total: Int) : Step()
        object DownloadingKey : Step()
        data class DecryptingKey(val progress: Int, val total: Int) : Step()
        data class ImportingKey(val progress: Int, val total: Int) : Step()
    }

    /**
     * @param step The current step, containing progress data if available. Else you should consider progress as indeterminate
     */
    fun onStepProgress(step: Step)
}
