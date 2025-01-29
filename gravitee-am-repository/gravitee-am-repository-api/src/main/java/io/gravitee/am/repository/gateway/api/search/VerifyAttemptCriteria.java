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
package io.gravitee.am.repository.gateway.api.search;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VerifyAttemptCriteria {
    private final String userId;
    private final String client;
    private final String factorId;

    public VerifyAttemptCriteria(Builder builder) {
        this.userId = builder.userId;
        this.client = builder.client;
        this.factorId = builder.factorId;
    }

    public String userId() {
        return userId;
    }

    public String client() {
        return client;
    }

    public String factorId() {
        return factorId;
    }

    public static class Builder {
        private String userId;
        private String client;
        private String factorId;

        public VerifyAttemptCriteria.Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public VerifyAttemptCriteria.Builder client(String client) {
            this.client = client;
            return this;
        }

        public VerifyAttemptCriteria.Builder factorId(String factorId) {
            this.factorId = factorId;
            return this;
        }

        public VerifyAttemptCriteria build() {
            return new VerifyAttemptCriteria(this);
        }
    }

    @Override
    public String toString() {
        return "{\"_class\":\"VerifyAttemptCriteria\", " +
                "\"factorId\":" + (factorId == null ? "null" : "\"" + factorId + "\"") + ", " +
                "\"client\":" + (client == null ? "null" : "\"" + client + "\"") + ", " +
                "\"userId\":" + (userId == null ? "null" : "\"" + userId + "\"") +
                "}";
    }
}
