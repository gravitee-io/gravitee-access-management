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
package io.gravitee.am.reporter.api.audit.model;

import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.UserProperties;

/**
 * Carries the user and client behind an {@link Audit}, for reporters that resolve attribute mappings.
 *
 * <p>Exposes them only as the sanitized {@link UserProperties} and {@link ClientProperties}
 * projections; the raw models must not be exposed.
 *
 * @author GraviteeSource Team
 */
public class AuditEnrichmentContext {

    private final User user;
    private final Client client;

    public AuditEnrichmentContext(User user, Client client) {
        this.user = user;
        this.client = client;
    }

    public UserProperties user() {
        return user == null ? null : new UserProperties(user, false);
    }

    public ClientProperties client() {
        return client == null ? null : new ClientProperties(client);
    }
}
