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
package io.gravitee.am.management.service;

import io.gravitee.am.common.email.Email;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EmailService {

    Maybe<Email> send(Domain domain, Application client, Template template, User user);

    Maybe<io.gravitee.am.model.Email> getEmailTemplate(Template template, User user);

    default Maybe<Email> send(Domain domain, Template template, User user) {
        return send(domain, null, template, user);
    }

    Maybe<Email> getFinalEmail(Domain domain, Application client, io.gravitee.am.model.Template template, User user, Map<String, Object> params);

}
