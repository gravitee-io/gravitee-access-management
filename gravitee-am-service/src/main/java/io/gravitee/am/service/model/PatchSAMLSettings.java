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

import io.gravitee.am.model.SAMLSettings;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchSAMLSettings {

    private Optional<Boolean> enabled;
    private Optional<String> entityId;
    private Optional<String>  certificate;

    public PatchSAMLSettings() {}

    public PatchSAMLSettings(PatchSAMLSettings other) {
        this.enabled = other.enabled;
        this.entityId = other.entityId;
        this.certificate = other.certificate;
    }

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public Optional<String> getEntityId() {
        return entityId;
    }

    public void setEntityId(Optional<String> entityId) {
        this.entityId = entityId;
    }

    public Optional<String> getCertificate() {
        return certificate;
    }

    public void setCertificate(Optional<String> certificate) {
        this.certificate = certificate;
    }

    public SAMLSettings patch(SAMLSettings _toPatch) {
        SAMLSettings toPatch = _toPatch == null ? new SAMLSettings() : new SAMLSettings(_toPatch);
        SetterUtils.safeSet(toPatch::setEnabled, this.getEnabled());
        SetterUtils.safeSet(toPatch::setEntityId, this.getEntityId());
        SetterUtils.safeSet(toPatch::setCertificate, this.getCertificate());
        return toPatch;
    }

    public Set<Permission> getRequiredPermissions() {

        Set<Permission> requiredPermissions = new HashSet<>();

        if (entityId != null && entityId.isPresent()
                || certificate != null && certificate.isPresent()) {
            requiredPermissions.add(Permission.DOMAIN_SAML);
        }

        return requiredPermissions;
    }
}
