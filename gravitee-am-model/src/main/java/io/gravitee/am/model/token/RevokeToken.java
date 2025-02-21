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

package io.gravitee.am.model.token;


import io.gravitee.am.model.UserId;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
@NoArgsConstructor
public class RevokeToken {
    private RevokeType revokeType;
    private String domainId;
    /**
     * the client_id oauth2 parameter
     */
    private String clientId;
    private UserId userId;

    public static RevokeToken byUser(String domainId, UserId userId) {
        final var request = new RevokeToken();
        request.setRevokeType(RevokeType.BY_USER);
        request.setDomainId(domainId);
        request.setUserId(userId);
        return request;
    }

    public static RevokeToken byClientId(String domainId, String clientId) {
        final var request = new RevokeToken();
        request.setRevokeType(RevokeType.BY_CLIENT);
        request.setDomainId(domainId);
        request.setClientId(clientId);
        return request;
    }

    public static RevokeToken byUserAndClientId(String domainId, String clientId, UserId userId) {
        final var request = new RevokeToken();
        request.setRevokeType(RevokeType.BY_USER_AND_CLIENT);
        request.setDomainId(domainId);
        request.setClientId(clientId);
        request.setUserId(userId);
        return request;
    }
}
