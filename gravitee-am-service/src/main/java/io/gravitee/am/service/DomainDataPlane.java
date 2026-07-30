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
        Optional<String> entrypointOrigin = entrypointOrigin(requestOrigin);
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

    private Optional<String> entrypointOrigin(@Nullable String requestOrigin) {
        if (!managedCloud || domain.getReferenceId() == null) {
            return Optional.empty();
        }
        return entryPointManager.resolveForRequest(domain.getReferenceId(), requestOrigin)
                .map(Entrypoint::getUrl)
                .map(UriBuilder::toOrigin);
    }
}
