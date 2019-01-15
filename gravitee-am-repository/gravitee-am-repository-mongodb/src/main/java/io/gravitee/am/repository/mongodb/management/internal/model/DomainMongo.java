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

import io.gravitee.am.repository.mongodb.common.model.Auditable;
import io.gravitee.common.event.Event;
import org.bson.Document;
import io.gravitee.am.repository.mongodb.management.internal.model.oidc.OIDCSettingsMongo;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainMongo extends Auditable {

    @BsonId
    private String id;

    private String name;

    private String description;

    private boolean enabled;

    private boolean master;

    private String path;

    private LoginFormMongo loginForm;

    private Set<String> identities;

    private Set<String> oauth2Identities;

    private Document lastEvent;

    private OIDCSettingsMongo oidc;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public LoginFormMongo getLoginForm() {
        return loginForm;
    }

    public void setLoginForm(LoginFormMongo loginForm) {
        this.loginForm = loginForm;
    }

    public Set<String> getIdentities() {
        return identities;
    }

    public void setIdentities(Set<String> identities) {
        this.identities = identities;
    }

    public Set<String> getOauth2Identities() {
        return oauth2Identities;
    }

    public void setOauth2Identities(Set<String> oauth2Identities) {
        this.oauth2Identities = oauth2Identities;
    }

    public Document getLastEvent() {
        return lastEvent;
    }

    public void setLastEvent(Document lastEvent) {
        this.lastEvent = lastEvent;
    }

    public OIDCSettingsMongo getOidc() {
        return oidc;
    }

    public void setOidc(OIDCSettingsMongo oidc) {
        this.oidc = oidc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DomainMongo that = (DomainMongo) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
