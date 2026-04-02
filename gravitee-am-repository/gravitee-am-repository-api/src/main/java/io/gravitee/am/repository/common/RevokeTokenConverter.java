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
package io.gravitee.am.repository.common;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserId;
import io.gravitee.am.model.token.RevokeToken;
import io.gravitee.am.model.token.RevokeType;

import java.util.Map;

public class RevokeTokenConverter {

    public static RevokeToken toRevokeToken(Map revokeToken) {
        if (revokeToken == null) {
            return null;
        }

        RevokeToken document = new RevokeToken();
        document.setRevokeType(RevokeType.valueOf((String) revokeToken.get("revokeType")));
        document.setDomainId((String) revokeToken.get("domainId"));

        if(revokeToken.get("principal") != null) {
            Map principal = (Map) revokeToken.get("principal");
            document.setPrincipal(RevokeToken.UserData.builder()
                    .userId((String) principal.get("userId"))
                    .username((String) principal.get("username"))
                    .referenceType(ReferenceType.valueOf((String) principal.get("referenceType")))
                    .referenceId((String) principal.get("referenceId"))
                    .build());
        }

        if(revokeToken.get("user") != null) {
            Map user = (Map) revokeToken.get("user");
            document.setUser(RevokeToken.UserData.builder()
                    .userId((String) user.get("userId"))
                    .username((String) user.get("username"))
                    .referenceType(ReferenceType.valueOf((String) user.get("referenceType")))
                    .referenceId((String) user.get("referenceId"))
                    .build());
        } else {
            // For backward compatibility
            Map userIdMap = (Map) revokeToken.get("userId");
            if(userIdMap != null) {
                UserId userId = toUserId(userIdMap);
                document.setUser(RevokeToken.UserData.builder()
                        .userId(userId.id())
                        .username(userId.id())
                        .referenceType(ReferenceType.DOMAIN)
                        .referenceId(document.getDomainId())
                        .build());
            }
        }

        if(revokeToken.get("application") != null) {
            Map application = (Map) revokeToken.get("application");
            document.setApplication(RevokeToken.ApplicationData.builder()
                    .clientId((String) application.get("clientId"))
                    .applicationId((String) application.get("applicationId"))
                    .applicationName((String) application.get("applicationName"))
                    .build());
        } else {
            // For backward compatibility
            String clientId = (String) revokeToken.get("clientId");
            if(clientId != null) {
                document.setApplication(RevokeToken.ApplicationData.builder()
                        .clientId(clientId)
                        .build());
            }
        }

        return document;
    }

    private static UserId toUserId(Map userId) {
        if (userId == null) {
            return null;
        }
        return new UserId((String) userId.get("id"), (String) userId.get("externalId"), (String) userId.get("source"));
    }
}
