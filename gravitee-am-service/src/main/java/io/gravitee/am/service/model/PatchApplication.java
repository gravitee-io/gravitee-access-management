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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PatchApplication {

    private Optional<ApplicationType> type;
    private Optional<String> name;
    private Optional<String> description;
    private Optional<Boolean> enabled;
    private Optional<Boolean> template;
    private Optional<Set<String>> identities;
    private Optional<String> certificate;
    private Optional<Map<String, Object>> metadata;
    private Optional<PatchApplicationSettings> settings;

    public Optional<ApplicationType> getType() {
        return type;
    }

    public void setType(Optional<ApplicationType> type) {
        this.type = type;
    }

    public Optional<String> getName() {
        return name;
    }

    public void setName(Optional<String> name) {
        this.name = name;
    }

    public Optional<String> getDescription() {
        return description;
    }

    public void setDescription(Optional<String> description) {
        this.description = description;
    }

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public Optional<Boolean> getTemplate() {
        return template;
    }

    public void setTemplate(Optional<Boolean> template) {
        this.template = template;
    }

    public Optional<Set<String>> getIdentities() {
        return identities;
    }

    public void setIdentities(Optional<Set<String>> identities) {
        this.identities = identities;
    }

    public Optional<String> getCertificate() {
        return certificate;
    }

    public void setCertificate(Optional<String> certificate) {
        this.certificate = certificate;
    }

    public Optional<Map<String, Object>> getMetadata() {
        return metadata;
    }

    public void setMetadata(Optional<Map<String, Object>> metadata) {
        this.metadata = metadata;
    }

    public Optional<PatchApplicationSettings> getSettings() {
        return settings;
    }

    public void setSettings(Optional<PatchApplicationSettings> settings) {
        this.settings = settings;
    }

    public Application patch(Application _toPatch) {
        // create new object for audit purpose (patch json result)
        Application toPatch = new Application(_toPatch);

        SetterUtils.safeSet(toPatch::setType, this.getType());
        SetterUtils.safeSet(toPatch::setName, this.getName());
        SetterUtils.safeSet(toPatch::setDescription, this.getDescription());
        SetterUtils.safeSet(toPatch::setEnabled, this.getEnabled(), boolean.class);
        SetterUtils.safeSet(toPatch::setTemplate, this.getTemplate(), boolean.class);
        SetterUtils.safeSet(toPatch::setIdentities, this.getIdentities());
        SetterUtils.safeSet(toPatch::setCertificate, this.getCertificate());
        SetterUtils.safeSet(toPatch::setMetadata, this.getMetadata());
        if (this.getSettings() != null && this.getSettings().isPresent()) {
            toPatch.setSettings(this.getSettings().get().patch(toPatch.getSettings()));
        }

        return toPatch;
    }
}
