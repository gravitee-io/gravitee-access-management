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


import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Data
@NoArgsConstructor
public class RevokeToken extends RevokeTokenDeprecated {

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    public static class UserData {
        private String userId;
        private String referenceId;
        private ReferenceType referenceType;
        private String username;
    }

    @Data
    @NoArgsConstructor
    @Builder
    @AllArgsConstructor
    public static class ApplicationData {
        private String clientId;
        private String applicationId;
        private String applicationName;
    }

    private RevokeType revokeType;
    private String domainId;

    private UserData user;
    private UserData principal;
    private ApplicationData application;

    public static RevokeToken byUser(Domain domain, String userId, String username, String principalId, String principalUsername) {
        final var request = new RevokeToken();
        request.setRevokeType(RevokeType.BY_USER);
        request.setDomainId(domain.getId());
        request.setUser(UserData.builder()
                .userId(userId)
                .username(username)
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(domain.getId())
                .build());
        request.setPrincipal(UserData.builder()
                .userId(principalId)
                .username(principalUsername)
                .referenceType(ReferenceType.ORGANIZATION)
                .referenceId(domain.getReferenceId())
                .build());

        // For backward compatibility MAPI in newer version may sent an event which is read by GW in older version
        request.setUserId(UserId.internal(userId));
        return request;
    }


    public static RevokeToken byApplication(Domain domain, Application application, String principalId, String principalUsername) {
        var clientId = Optional.ofNullable(application.getSettings())
                .map(ApplicationSettings::getOauth)
                .map(ApplicationOAuthSettings::getClientId)
                .orElse(null);
        final var request = new RevokeToken();
        request.setRevokeType(RevokeType.BY_CLIENT);
        request.setDomainId(domain.getId());
        request.setApplication(ApplicationData.builder()
                .applicationId(application.getId())
                .clientId(clientId)
                .applicationName(application.getName())
                .build());
        request.setPrincipal(UserData.builder()
                .userId(principalId)
                .username(principalUsername)
                .referenceId(domain.getReferenceId())
                .referenceType(ReferenceType.ORGANIZATION)
                .build());

        // For backward compatibility MAPI in newer version may sent an event which is read by GW in older version
        request.setClientId(clientId);

        return request;
    }

    public static RevokeToken byClientId(Domain domain, String clientId) {
        final var request = new RevokeToken();
        request.setRevokeType(RevokeType.BY_CLIENT);
        request.setDomainId(domain.getId());
        request.setApplication(ApplicationData.builder()
                .clientId(clientId)
                .build());

        // For backward compatibility MAPI in newer version may sent an event which is read by GW in older version
        request.setClientId(clientId);
        return request;
    }

    public static RevokeToken byUserAndClientId(Domain domain, String clientId, String userId, String username, String principalId, String principalUsername) {
        final var request = new RevokeToken();
        request.setRevokeType(RevokeType.BY_USER_AND_CLIENT);
        request.setDomainId(domain.getId());
        request.setApplication(ApplicationData.builder()
                .clientId(clientId)
                .build());
        request.setUser(UserData.builder()
                .userId(userId)
                .username(username)
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(domain.getId())
                .build());
        request.setPrincipal(UserData.builder()
                .userId(principalId)
                .username(principalUsername)
                .referenceId(domain.getReferenceId())
                .referenceType(ReferenceType.ORGANIZATION)
                .build());

        // For backward compatibility MAPI in newer version may sent an event which is read by GW in older version
        request.setUserId(UserId.internal(userId));
        request.setClientId(clientId);
        return request;
    }
}
