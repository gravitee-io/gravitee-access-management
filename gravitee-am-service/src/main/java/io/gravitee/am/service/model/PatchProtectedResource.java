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

import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ProtectedResourceFeature;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.utils.SetterUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author GraviteeSource Team
 */
@NoArgsConstructor
@Getter
@Setter
public class PatchProtectedResource {

    private Optional<String> name;
    private Optional<String> description;
    private Optional<List<String>> resourceIdentifiers;
    private Optional<List<UpdateProtectedResourceFeature>> features;

    public ProtectedResource patch(ProtectedResource _toPatch) {
        // create new object for audit purpose (patch json result)
        ProtectedResource toPatch = new ProtectedResource(_toPatch);

        SetterUtils.safeSet(toPatch::setName, this.getName());
        SetterUtils.safeSet(toPatch::setDescription, this.getDescription());
        SetterUtils.safeSet(toPatch::setResourceIdentifiers, this.getResourceIdentifiers());

        if (this.getFeatures() != null && this.getFeatures().isPresent()) {
            List<ProtectedResourceFeature> patchedFeatures = buildProtectedResourceFeatures(getFeatures().get());
            toPatch.setFeatures(patchedFeatures);
        }

        return toPatch;
    }

    private List<ProtectedResourceFeature> buildProtectedResourceFeatures(List<UpdateProtectedResourceFeature> updateFeatures) {
        return updateFeatures.stream()
                .map(patchFeature -> {
                    ProtectedResourceFeature feature = patchFeature.asFeature();
                    return switch (patchFeature) {
                        case UpdateMcpTool updateMcpTool -> new io.gravitee.am.model.McpTool(feature, updateMcpTool.getScopes());
                        default -> feature;
                    };
                })
                .toList();
    }

    public Set<Permission> getRequiredPermissions() {
        Set<Permission> requiredPermissions = new java.util.HashSet<>();

        if ((getName() != null && getName().isPresent())
                || (getDescription() != null && getDescription().isPresent())
                || (getResourceIdentifiers() != null && getResourceIdentifiers().isPresent())
                || (getFeatures() != null && getFeatures().isPresent())) {
            requiredPermissions.add(Permission.PROTECTED_RESOURCE);
        }

        return requiredPermissions;
    }
}
