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
package io.gravitee.am.model.oauth2;

import java.util.Date;

/**
 * The name, description and schema of an {@link AuthorizationDetailType} at one version.
 *
 * @param version starts at 1, unique per type
 */
public record AuthorizationDetailTypeVersion(String authorizationDetailTypeId,
                                             int version,
                                             String domain,
                                             String name,
                                             String description,
                                             String schema,
                                             Date createdAt) {

    public static AuthorizationDetailTypeVersion of(AuthorizationDetailType type) {
        return new AuthorizationDetailTypeVersion(type.getId(), type.getVersion(), type.getDomain(), type.getName(),
                type.getDescription(), type.getSchema(), type.getUpdatedAt());
    }
}
