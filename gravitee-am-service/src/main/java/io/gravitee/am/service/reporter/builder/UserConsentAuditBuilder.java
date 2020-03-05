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

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.model.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.service.reporter.builder.management.ManagementAuditBuilder;

import java.util.Collection;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserConsentAuditBuilder extends ManagementAuditBuilder<UserConsentAuditBuilder> {

    public UserConsentAuditBuilder() {
        super();
    }

    public UserConsentAuditBuilder approvals(Collection<ScopeApproval> approvals) {
        setNewValue(approvals);
        return this;
    }

    public UserConsentAuditBuilder user(User user) {
        setTarget(user.getId(), EntityType.USER, user.getUsername(), getDisplayName(user), user.getReferenceType() == ReferenceType.DOMAIN ? user.getReferenceId() : null);
        return this;
    }

    private String getDisplayName(User user) {
        final String displayName =
                // display name
                user.getDisplayName() != null ?
                        user.getDisplayName() :
                        // first name + last name
                        user.getFirstName() != null ?
                                user.getFirstName() + (user.getLastName() != null ? user.getLastName() : "") :
                                // OIDC name claim
                                user.getAdditionalInformation() != null && user.getAdditionalInformation().containsKey(StandardClaims.NAME) ?
                                        (String) user.getAdditionalInformation().get(StandardClaims.NAME) :
                                        // default to username
                                        user.getUsername();

        return displayName;
    }
}
