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
package io.gravitee.am.model.oauth2;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Scope {

    /**
     * Technical identifier
     */
    private String id;

    /**
     * Common identifier used during OAuth flow
     */
    private String key;

    private String name;

    private String description;

    /**
     * Security domain associated to the scope
     */
    private String domain;

    /**
     * Domain creation date
     */
    private Date createdAt;

    /**
     * Domain last updated date
     */
    private Date updatedAt;

    private boolean system;

    private List<String> claims;

    /**
     * Expiration time for user consent (in seconds)
     */
    private Integer expiresIn;

    public Scope() {
    }

    public Scope(String key) {
        this.key = key;
    }

    public Scope(Scope other) {
        this.id = other.id;
        this.key = other.key;
        this.name = other.name;
        this.description = other.description;
        this.domain = other.domain;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.system = other.system;
        this.claims = other.claims;
        this.expiresIn = other.expiresIn;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
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

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public List<String> getClaims() {
        return claims;
    }

    public void setClaims(List<String> claims) {
        this.claims = claims;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Scope scope = (Scope) o;
        return Objects.equals(key, scope.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
