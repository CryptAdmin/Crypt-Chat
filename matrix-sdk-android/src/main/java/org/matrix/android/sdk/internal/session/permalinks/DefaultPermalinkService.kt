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

package org.matrix.android.sdk.internal.session.permalinks

import androidx.core.net.toUri
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.permalinks.PermalinkService
import javax.inject.Inject

internal class DefaultPermalinkService @Inject constructor(
        private val permalinkFactory: PermalinkFactory
) : PermalinkService {

    override fun createPermalink(event: Event, forceMatrixTo: Boolean): String? {
        return permalinkFactory.createPermalink(event, forceMatrixTo)
    }

    override fun createPermalink(id: String, forceMatrixTo: Boolean): String? {
        return permalinkFactory.createPermalink(id, forceMatrixTo)
    }

    override fun createRoomPermalink(roomId: String, viaServers: List<String>?, forceMatrixTo: Boolean): String? {
        return permalinkFactory.createRoomPermalink(roomId, viaServers, forceMatrixTo)
    }

    override fun createPermalink(roomId: String, eventId: String, forceMatrixTo: Boolean): String {
        return permalinkFactory.createPermalink(roomId, eventId, forceMatrixTo)
    }

    override fun getLinkedId(url: String): String? {
        return permalinkFactory.getLinkedId(url)
    }

    override fun createMentionSpanTemplate(type: PermalinkService.SpanTemplateType, forceMatrixTo: Boolean): String {
        return permalinkFactory.createMentionSpanTemplate(type, forceMatrixTo)
    }

    override fun isPermalinkSupported(supportedHosts: Array<String>, url: String): Boolean {
        return url.startsWith(PermalinkService.MATRIX_TO_URL_BASE) ||
                supportedHosts.any { url.toUri().host == it }
    }
}
