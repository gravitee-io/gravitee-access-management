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
package io.gravitee.am.model;

import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;

import java.util.Date;
import java.util.List;

public record ProtectedResourcePrimaryData (
        String id,
        String clientId,
        String name,
        String description,
        ProtectedResource.Type type,
        List<String> resourceIdentifiers,
        ApplicationSettings settings,
        List<ApplicationSecretSettings> secretSettings,
        List<? extends ProtectedResourceFeature> features,
        Date updatedAt,
        String certificate){

    public static ProtectedResourcePrimaryData of(ProtectedResource protectedResource) {
        return new ProtectedResourcePrimaryData(
                protectedResource.getId(),
                protectedResource.getClientId(),
                protectedResource.getName(),
                protectedResource.getDescription(),
                protectedResource.getType(),
                protectedResource.getResourceIdentifiers(),
                protectedResource.getSettings(),
                protectedResource.getSecretSettings(),
                protectedResource.getFeatures(),
                protectedResource.getUpdatedAt(),
                protectedResource.getCertificate()
        );
    }
}
