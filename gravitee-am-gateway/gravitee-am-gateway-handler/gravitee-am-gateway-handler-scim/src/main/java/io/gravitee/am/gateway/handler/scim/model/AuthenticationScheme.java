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

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 *  A multi-valued complex type that specifies supported authentication scheme properties.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum AuthenticationScheme {
    OAUTH("oauth", "OAuth 1.0", "Authentication scheme using the OAuth 1.0 Standard", "https://oauth.net/core/1.0a/", null),
    OAUTH2("oauth2", "OAuth 2.0", "Authentication scheme using the OAuth 2.0 Standard", "https://tools.ietf.org/html/rfc6749", null),
    OAUTH_BEARER_TOKEN(
        "oauthbearertoken",
        "OAuth Bearer Token",
        "Authentication scheme using the OAuth Bearer Token Standard",
        "http://www.rfc-editor.org/info/rfc6750",
        null
    ),
    HTTP_BASIC(
        "httpbasic",
        "HTTP Basic",
        "Authentication scheme using the HTTP Basic Standard",
        "http://www.rfc-editor.org/info/rfc2617",
        null
    ),
    HTTP_DIGEST(
        "httpdigest",
        "HTTP Digest",
        "Authentication scheme using the HTTP Digest Standard",
        "https://tools.ietf.org/html/rfc7616",
        null
    );

    /**
     * The authentication scheme.
     * This specification defines the values "oauth", "oauth2", "oauthbearertoken", "httpbasic", and "httpdigest". REQUIRED.
     */
    private final String type;

    /**
     * The common authentication scheme name, e.g., HTTP Basic. REQUIRED.
     */
    private final String name;

    /**
     * description
     */
    private final String description;

    /**
     * An HTTP-addressable URL pointing to the authentication scheme's specification.  OPTIONAL.
     */
    private final String specUri;

    /**
     * An HTTP-addressable URL pointing to the authentication scheme's usage documentation.  OPTIONAL.
     */
    private final String documentationUri;

    AuthenticationScheme(String type, String name, String description, String specUri, String documentationUri) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.specUri = specUri;
        this.documentationUri = documentationUri;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSpecUri() {
        return specUri;
    }

    public String getDocumentationUri() {
        return documentationUri;
    }
}
