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
package io.gravitee.am.service;

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.login.WebAuthnSettings;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
@Getter
public class DomainDataPlane {
    @NonNull
    private final Domain domain;
    @NonNull
    private final DataPlaneDescription dataPlaneDescription;
    @NonNull
    private final EntryPointManager entryPointManager;
    private final boolean managedCloud;

    public String getWebAuthnOrigin(@Nullable String requestOrigin) {
        Optional<String> entrypointOrigin = getWebAuthnEntrypointOrigin(requestOrigin);
        if (entrypointOrigin.isPresent()) {
            return entrypointOrigin.get();
        }
        WebAuthnSettings webAuthnSettings = domain.getWebAuthnSettings();
        if (webAuthnSettings != null && webAuthnSettings.getOrigin() != null) {
            return webAuthnSettings.getOrigin();
        } else {
            return dataPlaneDescription.gatewayUrl();
        }
    }

    /**
     * Empty unless the origin came from an environment entrypoint. Callers that derive the relying party
     * id need to tell that apart from the fallbacks: a domain that configured its own relying party id
     * keeps it, and deriving one from the fallback origin would silently replace it.
     * <p>
     * Present does not mean the entrypoint matched {@code requestOrigin} — the environment's primary
     * entrypoint stands in when nothing matches, so an unrecognised host resolves to a host the browser
     * is not on and fails the ceremony rather than being trusted.
     */
    public Optional<String> getWebAuthnEntrypointOrigin(@Nullable String requestOrigin) {
        if (!managedCloud || domain.getReferenceId() == null) {
            return Optional.empty();
        }
        return entryPointManager.resolveForRequest(domain.getReferenceId(), requestOrigin)
                .map(Entrypoint::getUrl)
                .map(UriBuilder::toOrigin);
    }
}
