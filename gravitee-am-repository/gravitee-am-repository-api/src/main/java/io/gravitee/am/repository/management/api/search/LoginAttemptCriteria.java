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
package io.gravitee.am.repository.management.api.search;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginAttemptCriteria {

    private String domain;
    private String client;
    private String identityProvider;
    private String username;

    public LoginAttemptCriteria(Builder builder) {
        domain = builder.domain;
        client = builder.client;
        identityProvider = builder.identityProvider;
        username = builder.username;
    }

    public String domain() {
        return domain;
    }

    public String client() {
        return client;
    }

    public String identityProvider() {
        return identityProvider;
    }

    public String username() {
        return username;
    }

    public static class Builder {
        private String domain;
        private String client;
        private String identityProvider;
        private String username;

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder client(String client) {
            this.client = client;
            return this;
        }

        public Builder identityProvider(String identityProvider) {
            this.identityProvider = identityProvider;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public LoginAttemptCriteria build() {
            return new LoginAttemptCriteria(this);
        }
    }

    @Override
    public String toString() {
        return "{\"_class\":\"LoginAttemptCriteria\", " +
                "\"domain\":" + (domain == null ? "null" : "\"" + domain + "\"") + ", " +
                "\"client\":" + (client == null ? "null" : "\"" + client + "\"") + ", " +
                "\"identityProvider\":" + (identityProvider == null ? "null" : "\"" + identityProvider + "\"") + ", " +
                "\"username\":" + (username == null ? "null" : "\"" + username + "\"") +
                "}";
    }
}
