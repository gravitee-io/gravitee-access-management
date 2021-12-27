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
import io.gravitee.am.common.email.Email;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.User;
import io.gravitee.am.model.ReferenceType;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailAuditBuilder extends AuditBuilder<EmailAuditBuilder> {

    private static final String SYSTEM = "system";
    private static final String EVENT_SUFFIX = "_EMAIL_SENT";
    private static final String HTML_SUFFIX = ".html";

    public EmailAuditBuilder() {
        super();
        // emails are sent by system actor
        setActor(SYSTEM, SYSTEM, SYSTEM, SYSTEM, ReferenceType.PLATFORM, Platform.DEFAULT);
    }

    public EmailAuditBuilder email(Email email) {
        if (email != null) {
            type(email.getTemplate().replace(HTML_SUFFIX, "").toUpperCase() + EVENT_SUFFIX);
        }
        return this;
    }

    public EmailAuditBuilder user(User user) {
        if (user != null) {
            setTarget(user.getId(), EntityType.USER, user.getUsername(), user.getDisplayName(), user.getReferenceType(), user.getReferenceId());
        }
        return this;
    }
}
