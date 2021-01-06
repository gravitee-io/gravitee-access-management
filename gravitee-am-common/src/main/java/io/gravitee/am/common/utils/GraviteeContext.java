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
package io.gravitee.am.common.utils;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeContext {
    private final String organizationId;
    private final String environmentId;
    private final String domainId;

    public GraviteeContext(String organizationId, String environmentId, String domainId) {
        this.organizationId = organizationId;
        this.environmentId = environmentId;
        this.domainId = domainId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public String getDomainId() {
        return domainId;
    }

    public static GraviteeContext defaultContext(String domain) {
     return new GraviteeContext("DEFAULT", "DEFAULT", domain);
    }
}
