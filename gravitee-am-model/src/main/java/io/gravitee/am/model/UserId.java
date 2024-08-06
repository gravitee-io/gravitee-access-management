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
    public UserId {
        if (externalId == null ^ source == null) {
            throw new IllegalArgumentException("externalId and source must both be null or neither can be null");
        }
    }

    /**
     * Create a new UserId without source information
     */
    public static UserId internal(String id) {
        return new UserId(id, null, null);
    }

    public boolean isExternal() {
        return source != null && externalId != null;
    }
}
