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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.SAMLSettings;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SAMLSettingsMongo {

    private boolean enabled;
    private String entityId;
    private String certificate;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public SAMLSettings convert() {
        SAMLSettings samlSettings = new SAMLSettings();
        samlSettings.setEnabled(isEnabled());
        samlSettings.setEntityId(getEntityId());
        samlSettings.setCertificate(getCertificate());
        return samlSettings;
    }

    public static SAMLSettingsMongo convert(SAMLSettings other) {
        if (other == null) {
            return null;
        }
        SAMLSettingsMongo samlSettings = new SAMLSettingsMongo();
        samlSettings.setEnabled(other.isEnabled());
        samlSettings.setEntityId(other.getEntityId());
        samlSettings.setCertificate(other.getCertificate());
        return samlSettings;
    }
}
