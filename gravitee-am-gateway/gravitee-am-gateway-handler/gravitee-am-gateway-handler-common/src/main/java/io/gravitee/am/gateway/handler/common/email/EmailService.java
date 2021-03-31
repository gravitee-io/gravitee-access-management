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
package io.gravitee.am.gateway.handler.common.email;

import freemarker.template.TemplateException;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface EmailService {

    void send(Template template, User user, Client client);

    EmailWrapper createEmail(io.gravitee.am.model.Template template, Client client, List<String> recipients, Map<String, Object> params) throws IOException, TemplateException;

    final class EmailWrapper {
        final io.gravitee.am.common.email.Email email;
        long expireAt;

        public EmailWrapper(Email email) {
            this.email = email;
        }

        public Email getEmail() {
            return email;
        }

        public long getExpireAt() {
            return expireAt;
        }

        public void setExpireAt(long expireAt) {
            this.expireAt = expireAt;
        }
    }
}
