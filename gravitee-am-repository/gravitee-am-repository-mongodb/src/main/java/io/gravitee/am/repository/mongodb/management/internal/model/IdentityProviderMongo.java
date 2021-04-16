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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IdentityProviderMongo extends Auditable {

    @BsonId
    private String id;

    private String name;

    private String type;

    private String configuration;

    /**
     * Map codec support is planned for version 3.7 jira.mongodb.org issue: JAVA-2695
     */
    private Document mappers;

    /**
     * Map codec support is planned for version 3.7 jira.mongodb.org issue: JAVA-2695
     */
    private Document roleMapper;

    private ReferenceType referenceType;

    private String referenceId;

    private boolean external;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public Document getMappers() {
        return mappers;
    }

    public void setMappers(Document mappers) {
        this.mappers = mappers;
    }

    public Document getRoleMapper() {
        return roleMapper;
    }

    public void setRoleMapper(Document roleMapper) {
        this.roleMapper = roleMapper;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IdentityProviderMongo that = (IdentityProviderMongo) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
