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

import java.util.Collections;
import java.util.List;

/**
 *
 * See <a href="https://tools.ietf.org/html/rfc7643#section-8.5">8.5. Service Provider Configuration Representation</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ServiceProviderConfiguration extends Resource {

    private static final List<String> SCHEMAS = Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig");

    /**
     * An HTTP-addressable URL pointing to the service provider's human-consumable help documentation. OPTIONAL.
     */
    private String documentationUri;

    /**
     * A complex type that specifies PATCH configuration options. REQUIRED.
     */
    private ComplexType patch;

    /**
     * A complex type that specifies bulk configuration options. REQUIRED.
     */
    private ComplexType bulk;

    /**
     *  A complex type that specifies FILTER options. REQUIRED.
     */
    private ComplexType filter;

    /**
     * A complex type that specifies configuration options related to changing a password.  REQUIRED.
     */
    private ComplexType changePassword;

    /**
     * A complex type that specifies Sort configuration options. REQUIRED.
     */
    private ComplexType sort;

    /**
     * A complex type that specifies ETag configuration options. REQUIRED.
     */
    private ComplexType etag;

    /**
     * A multi-valued complex type that specifies supported
     *       authentication scheme properties.  To enable seamless discovery of
     *       configurations, the service provider SHOULD, with the appropriate
     *       security considerations, make the authenticationSchemes attribute
     *       publicly accessible without prior authentication.  REQUIRED.
     */
    private List<AuthenticationScheme> authenticationSchemes;

    public List<String> getSchemas() {
        return SCHEMAS;
    }

    public String getDocumentationUri() {
        return documentationUri;
    }

    public void setDocumentationUri(String documentationUri) {
        this.documentationUri = documentationUri;
    }

    public ComplexType getPatch() {
        return patch;
    }

    public void setPatch(ComplexType patch) {
        this.patch = patch;
    }

    public ComplexType getBulk() {
        return bulk;
    }

    public void setBulk(ComplexType bulk) {
        this.bulk = bulk;
    }

    public ComplexType getFilter() {
        return filter;
    }

    public void setFilter(ComplexType filter) {
        this.filter = filter;
    }

    public ComplexType getChangePassword() {
        return changePassword;
    }

    public void setChangePassword(ComplexType changePassword) {
        this.changePassword = changePassword;
    }

    public ComplexType getSort() {
        return sort;
    }

    public void setSort(ComplexType sort) {
        this.sort = sort;
    }

    public ComplexType getEtag() {
        return etag;
    }

    public void setEtag(ComplexType etag) {
        this.etag = etag;
    }

    public List<AuthenticationScheme> getAuthenticationSchemes() {
        return authenticationSchemes;
    }

    public void setAuthenticationSchemes(List<AuthenticationScheme> authenticationSchemes) {
        this.authenticationSchemes = authenticationSchemes;
    }
}
