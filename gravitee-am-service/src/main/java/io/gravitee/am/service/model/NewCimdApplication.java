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
package io.gravitee.am.service.model;

import io.gravitee.am.model.application.ApplicationType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Payload for creating an application bootstrapped from a CIMD (Client Identity Metadata Document) URL.
 * The server fetches the URL, validates it against the domain's CIMD trust policy, and seeds the
 * application's OAuth settings from the parsed metadata. {@code clientId} is forced to the URL.
 */
@Getter
@Setter
public class NewCimdApplication {

    @NotNull
    private String name;

    @NotNull
    private ApplicationType type;

    @NotNull
    private String cimdUrl;

    private String description;

    /** User-supplied client_name when the document does not provide one. */
    private String clientName;

    /** Optional default identity provider IDs to attach to the new application. */
    private List<String> identityProviders;
}
