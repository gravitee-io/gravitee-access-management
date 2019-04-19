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
package io.gravitee.am.gateway.handler.scim.model;

/**
 * SCIM Resource metadata
 *
 * See <a href="https://tools.ietf.org/html/rfc7643#section-3.1">3.1. Common Attributes</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Meta {

    /**
     * The name of the resource type of the resource.
     * This attribute has a mutability of "readOnly" and "caseExact" as "true".
     */
    private String resourceType;

    /**
     * The "DateTime" that the resource was added to the service provider.
     * This attribute MUST be a DateTime.
     */
    private String created;

    /**
     * The most recent DateTime that the details of this resource were updated at the service provider.
     * If this resource has never been modified since its initial creation, the value MUST be the same as the value of "created".
     */
    private String lastModified;

    /**
     * The URI of the resource being returned.
     * This value MUST be the same as the "Content-Location" HTTP response header.
     */
    private String location;

    /**
     * The version of the resource being returned.
     * This value must be the same as the entity-tag (ETag) HTTP response header.
     * Service provider support for this attribute is optional and subject to the service provider's support for versioning.
     */
    private String version;

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
