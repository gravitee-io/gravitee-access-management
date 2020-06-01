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

import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Optional;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class PatchScope {

    private Optional<String> name;

    private Optional<String> description;

    private Optional<String> iconUri;

    private Optional<Integer> expiresIn;

    private Optional<Boolean> discovery;

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

    public Optional<String> getIconUri() {
        return iconUri;
    }

    public void setIconUri(Optional<String> iconUri) {
        this.iconUri = iconUri;
    }

    public Optional<Integer> getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Optional<Integer> expiresIn) {
        this.expiresIn = expiresIn;
    }

    public Optional<Boolean> getDiscovery() {
        return discovery;
    }

    public void setDiscovery(Optional<Boolean> discovery) {
        this.discovery = discovery;
    }

    public Scope patch(Scope toPatch) {
        Scope patched = new Scope(toPatch);
        SetterUtils.safeSet(patched::setName,this.getName());
        SetterUtils.safeSet(patched::setDescription, this.getDescription());
        SetterUtils.safeSet(patched::setIconUri, this.getIconUri());
        SetterUtils.safeSet(patched::setExpiresIn, this.getExpiresIn());
        if(!toPatch.isSystem()){
            SetterUtils.safeSet(patched::setDiscovery, this.getDiscovery(), boolean.class);
        }
        return patched;
    }
}
