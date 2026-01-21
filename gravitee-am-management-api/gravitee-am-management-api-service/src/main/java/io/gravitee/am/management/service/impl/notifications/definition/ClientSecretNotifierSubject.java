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
package io.gravitee.am.management.service.impl.notifications.definition;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Notifiable;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.safe.ApplicationProperties;
import io.gravitee.am.model.safe.ClientSecretProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.safe.UserProperties;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Getter
public class ClientSecretNotifierSubject implements NotifierSubject {
    public final static String RESOURCE_TYPE = "application/secret";

    private final ClientSecret clientSecret;
    private final Notifiable target;
    private final Domain domain;
    private final User user;

    public ClientSecretNotifierSubject(ClientSecret clientSecret, Notifiable target, Domain domain, User user) {
        this.clientSecret = clientSecret;
        this.target = target;
        this.domain = domain;
        this.user = user;
    }

    @Override
    public String getResourceId() {
        return clientSecret.getId();
    }

    @Override
    public Map<String, Object> getData() {
        Map<String, Object> result = new HashMap<>();
        if (user != null) {
            result.put(NOTIFIER_DATA_USER, new UserProperties(user, false));
        }

        if (domain != null) {
            result.put(NOTIFIER_DATA_DOMAIN, new DomainProperties(domain));
        }

        if (clientSecret != null) {
            result.put(NOTIFIER_DATA_CLIENT_SECRET, new ClientSecretProperties(clientSecret.getName(), clientSecret.getExpiresAt()));
        }

        if (target != null) {
            result.put(NOTIFIER_DATA_APPLICATION, new ApplicationProperties(target.getName()));
            result.put("resourceType", formatClassName(target));
        }

        return result;
    }

    @Override
    public Map<String, Object> getMetadata() {
        HashMap<String, Object> result = new HashMap<>();
        Optional.ofNullable(domain).ifPresent(o -> result.put("domainId", o.getId()));
        Optional.ofNullable(user).ifPresent(o -> result.put("domainOwner", o.getId()));
        Optional.ofNullable(target).ifPresent(o -> result.put(formatClassName(o) + "Id", o.getId()));
        Optional.ofNullable(clientSecret).ifPresent(o -> result.put("clientSecretId", o.getId()));
        return Map.copyOf(result);
    }

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    private String formatClassName(Notifiable target) {
        return target.getClass()
                .getSimpleName()
                .replaceAll("(?<!^)(?=[A-Z])", " ")
                .toLowerCase();
    }
}
