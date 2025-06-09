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

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class DomainDataPlane {
    @NonNull
    private final Domain domain;
    @NonNull
    private final DataPlaneDescription dataPlaneDescription;

    public String getWebAuthnOrigin() {
        WebAuthnSettings webAuthnSettings = domain.getWebAuthnSettings();
        if (webAuthnSettings != null && webAuthnSettings.getOrigin() != null) {
            return webAuthnSettings.getOrigin();
        } else {
            return dataPlaneDescription.gatewayUrl();
        }
    }
}
