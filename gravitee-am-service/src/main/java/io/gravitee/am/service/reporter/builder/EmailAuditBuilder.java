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
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.Platform;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;

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

    /**
     * Set the audit type from the built-in template the email was sent for.
     */
    public EmailAuditBuilder template(Template template) {
        if (template != null) {
            type(switch (template) {
                case RESET_PASSWORD -> EventType.RESET_PASSWORD_EMAIL_SENT;
                case BLOCKED_ACCOUNT -> EventType.BLOCKED_ACCOUNT_EMAIL_SENT;
                case VERIFY_ATTEMPT -> EventType.VERIFY_ATTEMPT_EMAIL_SENT;
                case REGISTRATION_VERIFY -> EventType.REGISTRATION_VERIFY_EMAIL_SENT;
                case REGISTRATION_CONFIRMATION -> EventType.REGISTRATION_CONFIRMATION_EMAIL_SENT;
                default -> null;
            });
        }
        return this;
    }

    /**
     * Set the audit type from a free-form template name.
     */
    public EmailAuditBuilder customTemplate(String templateName) {
        if (templateName != null) {
            type(templateName.replace(HTML_SUFFIX, "").toUpperCase() + EVENT_SUFFIX);
        }
        return this;
    }

    public EmailAuditBuilder user(User user) {
        if (user != null) {
            setTarget(user.getId(), EntityType.USER, user.getUsername(), user.getDisplayName(), user.getReferenceType(), user.getReferenceId(), user.getExternalId(), user.getSource());
        }
        return this;
    }
}
