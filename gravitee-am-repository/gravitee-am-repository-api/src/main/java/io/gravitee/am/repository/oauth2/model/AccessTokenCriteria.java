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
package io.gravitee.am.repository.oauth2.model;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccessTokenCriteria {
    private final String clientId;
    private final String subject;
    private final Set<String> scopes;

    public AccessTokenCriteria(Builder builder) {
        clientId = builder.clientId;
        subject = builder.subject;
        scopes = builder.scopes;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSubject() {
        return subject;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public static class Builder {
        private String clientId;
        private String subject;
        private Set<String> scopes;

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public AccessTokenCriteria build() {
            return new AccessTokenCriteria(this);
        }
    }
}
