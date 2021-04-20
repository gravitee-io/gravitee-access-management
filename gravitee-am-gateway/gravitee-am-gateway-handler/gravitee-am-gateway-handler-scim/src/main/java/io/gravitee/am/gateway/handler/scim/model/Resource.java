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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * SCIM Resource
 *
 * See <a href="https://tools.ietf.org/html/rfc7643#section-3">3. SCIM Resources</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Resource {

    /**
     * The "schemas" attribute is a REQUIRED attribute and is an array of
     *       Strings containing URIs that are used to indicate the namespaces
     *       of the SCIM schemas that define the attributes present in the
     *       current JSON structure.  This attribute may be used by parsers to
     *       define the attributes present in the JSON structure that is the
     *       body to an HTTP request or response.  Each String value must be a
     *       unique URI.  All representations of SCIM schemas MUST include a
     *       non-empty array with value(s) of the URIs supported by that
     *       representation.  The "schemas" attribute for a resource MUST only
     *       contain values defined as "schema" and "schemaExtensions" for the
     *       resource's defined "resourceType".  Duplicate values MUST NOT be
     *       included.  Value order is not specified and MUST NOT impact
     *       behavior.
     */
    private List<String> schemas;

    /**
     * A unique identifier for a SCIM resource as defined by the service provider.
     * Each representation of the resource MUST include a non-empty "id" value.
     * This identifier MUST be unique across the SCIM service provider's entire set of resources.
     * It MUST be a stable, non-reassignable identifier that does not change when the same resource is returned in subsequent requests.
     * The value of the "id" attribute is always issued by the service provider and MUST NOT be specified by the client.
     * The string "bulkId" is a reserved keyword and MUST NOT be used within any unique identifier value.
     * The attribute characteristics are "caseExact" as "true", a mutability of "readOnly", and a "returned" characteristic of "always".
     */
    private String id;

    /**
     * A String that is an identifier for the resource as defined by the provisioning client.
     * The "externalId" may simplify identification of a resource between the provisioning client and the service provider by allowing the client to use a filter to locate the
     * resource with an identifier from the provisioning domain, obviating the need to store a local mapping between the provisioning domain's identifier of the resource and the
     * identifier used by the service provider.
     * Each resource MAY include a non-empty "externalId" value.
     * The value of the "externalId" attribute is always issued by the provisioning client and MUST NOT be specified by the service provider.
     * The service provider MUST always interpret the externalId as scoped to the provisioning domain.
     * While the server does not enforce uniqueness, it is assumed that the value's uniqueness is controlled by the client setting the value.
     * This attribute has "caseExact" as "true" and a mutability of "readWrite". This attribute is OPTIONAL.
     */
    private String externalId;

    /**
     *  A complex attribute containing resource metadata.
     *  All "meta" sub-attributes are assigned by the service provider (have a "mutability" of "readOnly"), and all of these sub-attributes have a "returned" characteristic of "default".
     *  This attribute SHALL be ignored when provided by clients.
     */
    private Meta meta;

    public List<String> getSchemas() {
        return schemas;
    }

    public void setSchemas(List<String> schemas) {
        this.schemas = schemas;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }
}
