/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.model;

public record UserId(String id, String externalId, String source) {

    public static UserId parse(String rawUserId) {
        var firstSeparatorLocation = rawUserId.indexOf(':');
        if (firstSeparatorLocation == -1) {
            // "userid"
            return new UserId(rawUserId, null, null);
        } else if (firstSeparatorLocation != rawUserId.length() - 1) {
            // "source:userid"
            var source = rawUserId.substring(0, firstSeparatorLocation);
            var externalId = rawUserId.substring(firstSeparatorLocation + 1);
            return new UserId(null, externalId, source);
        } else {
            // "source:" (missing external id)
            throw new IllegalArgumentException("Malformed userId: " + rawUserId);
        }

    }

    public String getInternalSubject() {
        if (hasExternal()) {
            return generateInternalSubject(source, externalId);
        } else {
            return id;
        }
    }

    public static String generateInternalSubject(String source, String externalId) {
        return source + ":" + externalId;

    }

    /**
     * Create a new UserId without source information
     */
    public static UserId internal(String id) {
        return new UserId(id, null, null);
    }

    public boolean hasExternal() {
        return source != null && externalId != null;
    }
}
