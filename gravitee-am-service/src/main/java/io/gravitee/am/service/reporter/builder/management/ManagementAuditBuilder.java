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
package io.gravitee.am.service.reporter.builder.management;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.service.reporter.builder.AuditBuilder;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class ManagementAuditBuilder<T> extends AuditBuilder<T> {

    /**
     * Management events are triggered by the admin client (the portal/API)
     */
    private static final String ADMIN_CLIENT = "admin";
    private static final String SYSTEM = "system";

    public ManagementAuditBuilder() {
        super();
        client(ADMIN_CLIENT);
        setActor(SYSTEM, SYSTEM, SYSTEM, SYSTEM, SYSTEM);
    }

    public T principal(User principal) {
        if (principal != null) {
            setActor(principal.getId(), EntityType.USER, principal.getUsername(), getDisplayName(principal), getDomain(principal));
            if (principal.getAdditionalInformation() != null) {
                if (principal.getAdditionalInformation().containsKey(Claims.ip_address)) {
                    ipAddress((String) principal.getAdditionalInformation().get(Claims.ip_address));
                }
                if (principal.getAdditionalInformation().containsKey(Claims.user_agent)) {
                    userAgent((String) principal.getAdditionalInformation().get(Claims.user_agent));
                }
            }
        }
        return (T) this;
    }

    private String getDisplayName(User user) {
        final String displayName =
                // display name
                user.getAdditionalInformation() != null && user.getAdditionalInformation().containsKey(StandardClaims.NAME) ?
                                        (String) user.getAdditionalInformation().get(StandardClaims.NAME) :
                                        // default to username
                                        user.getUsername();

        return displayName;
    }

    private String getDomain(User user) {
        return
                user.getAdditionalInformation() != null && user.getAdditionalInformation().containsKey(Claims.domain) ?
                        (String) user.getAdditionalInformation().get(Claims.domain) : null;
    }
}
