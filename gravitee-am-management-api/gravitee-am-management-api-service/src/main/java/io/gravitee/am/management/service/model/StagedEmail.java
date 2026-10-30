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
package io.gravitee.am.management.service.model;

import io.gravitee.am.common.email.Email;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;

public record StagedEmail(User user, Application client, EmailStaging staging, Email email) {

    public StagedEmail(User user, Application client, EmailStaging staging) {
        this(user, client, staging, null);
    }

    public StagedEmail with(Email email) {
        return new StagedEmail(user, client, staging, email);
    }

    public Template template() {
        return Template.valueOf(staging.getEmailTemplateName());
    }
}
