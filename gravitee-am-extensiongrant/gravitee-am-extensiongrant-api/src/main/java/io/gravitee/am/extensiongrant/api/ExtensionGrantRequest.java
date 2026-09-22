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
package io.gravitee.am.extensiongrant.api;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

@Getter
@EqualsAndHashCode
public class ExtensionGrantRequest {

    private final String clientId;
    private final Set<String> requestedScopes;
    private final Map<String, String> parameters;
    private final String authorizationServerIssuer;
    private final Set<String> requestedResources;
    private final Set<String> applicationScopes;

    @Builder
    private ExtensionGrantRequest(String clientId,
                                  Set<String> requestedScopes,
                                  Map<String, String> parameters,
                                  String authorizationServerIssuer,
                                  Set<String> requestedResources,
                                  Set<String> applicationScopes) {
        this.clientId = clientId;
        this.requestedScopes = requestedScopes == null ? Set.of() : Set.copyOf(requestedScopes);
        this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        this.authorizationServerIssuer = authorizationServerIssuer;
        this.requestedResources = requestedResources == null ? Set.of() : Set.copyOf(requestedResources);
        this.applicationScopes = applicationScopes == null ? Set.of() : Set.copyOf(applicationScopes);
    }

    public String parameter(String name) {
        return parameters.get(name);
    }
}
