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
package io.gravitee.am.management.service.impl.notifications;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.safe.ApplicationProperties;
import io.gravitee.am.model.safe.CertificateProperties;
import io.gravitee.am.model.safe.ClientSecretProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import io.gravitee.node.api.notifier.NotificationDefinition;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationDefinitionUtils {
    public static final String TYPE_UI_NOTIFIER = "ui-notifier";
    public static final String TYPE_EMAIL_NOTIFIER = "email-notifier";
    public static final String TYPE_LOG_NOTIFIER = "log-notifier";

    public static final String NOTIFIER_DATA_USER = "user";
    public static final String NOTIFIER_DATA_DOMAIN = "domain";
    public static final String NOTIFIER_DATA_CERTIFICATE = "certificate";
    public static final String NOTIFIER_DATA_CLIENT_SECRET = "clientSecret";
    public static final String NOTIFIER_DATA_APPLICATION = "application";

    public static Optional<Date> getExpirationDate(NotificationDefinition definition) {
        Date expiry = null;
        final Map<String, Object> data = definition.getData();
        if (data != null && data.containsKey(NOTIFIER_DATA_CERTIFICATE)) {
            expiry = ((CertificateProperties) data.get(NOTIFIER_DATA_CERTIFICATE)).getExpiresAt();
        }
        else if (data != null && data.containsKey(NOTIFIER_DATA_CLIENT_SECRET)) {
            expiry = ((ClientSecretProperties) data.get(NOTIFIER_DATA_CLIENT_SECRET)).getExpiresAt();
        }
        return Optional.ofNullable(expiry);
    }

    public static class ParametersBuilder {
        private User user;
        private Domain domain;
        private Application application;
        private Certificate certificate;
        private ClientSecret clientSecret;

        public ParametersBuilder withUser(User user) {
            this.user = user;
            return this;
        }

        public ParametersBuilder withDomain(Domain domain) {
            this.domain = domain;
            return this;
        }

        public ParametersBuilder withCertificate(Certificate certificate) {
            this.certificate = certificate;
            return this;
        }

        public ParametersBuilder withClientSecret(ClientSecret clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public ParametersBuilder withApplication(Application application) {
            this.application = application;
            return this;
        }

        public Map<String, Object> build() {
            var result = new HashMap();
            if (user != null) {
                result.put(NOTIFIER_DATA_USER, new UserProperties(user, false));
            }

            if (domain != null) {
                result.put(NOTIFIER_DATA_DOMAIN, new DomainProperties(domain));
            }

            if (certificate != null) {
                result.put(NOTIFIER_DATA_CERTIFICATE, new CertificateProperties(certificate));
            }

            if (clientSecret != null) {
                result.put(NOTIFIER_DATA_CLIENT_SECRET, new ClientSecretProperties(clientSecret.getName(), clientSecret.getExpiresAt()));
            }

            if (application != null) {
                result.put(NOTIFIER_DATA_APPLICATION, new ApplicationProperties(application.getName()));
            }

            return result;
        }


    }
}
