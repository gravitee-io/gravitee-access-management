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
package io.gravitee.am.service.reporter.builder.gateway;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.VerifyAttempt;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */

public class VerifyAttemptAuditBuilder extends GatewayAuditBuilder<VerifyAttemptAuditBuilder> {
    public VerifyAttemptAuditBuilder() {
        super();
    }

    public VerifyAttemptAuditBuilder verifyAttempt(VerifyAttempt verifyAttempt) {
        if (verifyAttempt != null) {
            if (EventType.MFA_VERIFICATION_LIMIT_EXCEED.equals(getType())) {
                setNewValue(verifyAttempt);
            }
            referenceType(verifyAttempt.getReferenceType());
            referenceId(verifyAttempt.getReferenceId());
        }

        return this;
    }

    public VerifyAttemptAuditBuilder user(User user) {
        if (user != null) {
            setActor(user.getId(), EntityType.USER, user.getUsername(), user.getDisplayName(), user.getReferenceType(), user.getReferenceId(), user.getExternalId(), user.getSource());
        }

        return this;
    }
}
