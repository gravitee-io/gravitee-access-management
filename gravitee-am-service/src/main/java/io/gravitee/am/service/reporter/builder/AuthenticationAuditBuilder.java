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
package io.gravitee.am.service.reporter.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.model.User;
import io.gravitee.am.reporter.api.audit.model.Audit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationAuditBuilder extends AuditBuilder<AuthenticationAuditBuilder> {

    public AuthenticationAuditBuilder() {
        super();
        type(EventType.USER_LOGIN);
    }

    public AuthenticationAuditBuilder principal(Authentication principal) {
        if (principal != null) {
            if (principal.getContext().get(Claims.IP_ADDRESS) != null) {
                ipAddress((String) principal.getContext().get(Claims.IP_ADDRESS));
            }
            if (principal.getContext().get(Claims.USER_AGENT) != null) {
                userAgent((String) principal.getContext().get(Claims.USER_AGENT));
            }

            setActor(null, EntityType.USER, (String) principal.getPrincipal(), null, null, null);
        }
        return this;
    }

    public AuthenticationAuditBuilder user(User user) {
        if (user != null) {
            setActor(user.getId(), EntityType.USER, user.getUsername(), getDisplayName(user), user.getReferenceType(), user.getReferenceId(), user.getExternalId(), user.getSource());
        }
        return this;
    }

    private String getDisplayName(User user) {
        return user.getDisplayName() != null ?
                user.getDisplayName() :
                // first name + last name
                user.getFirstName() != null ?
                        user.getFirstName() + (user.getLastName() != null ? user.getLastName() : "") :
                        // OIDC name claim
                        user.getAdditionalInformation() != null && user.getAdditionalInformation().containsKey(StandardClaims.NAME) ?
                                (String) user.getAdditionalInformation().get(StandardClaims.NAME) :
                                // default to username
                                user.getUsername();
    }

    @Override
    public Audit build(ObjectMapper mapper) {
        if (throwable instanceof BadCredentialsException) {
            // removes username from audit log
            throwable(new BadCredentialsException(throwable.getMessage()));
        }
        return super.build(mapper);
    }

}
