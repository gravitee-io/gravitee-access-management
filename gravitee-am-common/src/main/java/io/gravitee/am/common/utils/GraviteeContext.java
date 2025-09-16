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

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@AllArgsConstructor
@Getter
public class GraviteeContext {
    private final String organizationId;
    private final String environmentId;
    private final String domainId;

    public static GraviteeContext defaultContext(String domain) {
        return new GraviteeContext("DEFAULT", "DEFAULT", domain);
    }

    @Override
    public String toString() {
        return "GraviteeContext{" +
                "orgId='" + organizationId + '\'' +
                ", envId='" + environmentId + '\'' +
                ", domainId='" + domainId + '\'' +
                '}';
    }
}
