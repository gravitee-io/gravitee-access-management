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
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.12>3.12. HTTP Status and Error Response Handling</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum ScimType {
    /**
     * The specified filter syntax was invalid, or the specified attribute and filter comparison combination is not supported.
     */
    INVALID_FILTER("invalidFilter"),
    /**
     * The specified filter yields  many more results than the server is willing to calculate or process.
     * For example, a filter such as "(userName pr)" by itself would return all entries with a "userName" and MAY not be acceptable to the service provider.
     */
    TOO_MANY("tooMany"),
    /**
     * One or more of the attribute values are already in use or are reserved.
     */
    UNIQUENESS("uniqueness"),
    /**
     * The attempted modification is not compatible with the target attribute's mutability or current state
     * (e.g., modification of an "immutable" attribute with an existing value).
     */
    MUTABILITY("mutability"),
    /**
     * The request body message structure was invalid or did not conform to the request schema.
     */
    INVALID_SYNTAX("invalidSyntax"),
    /**
     * The "path" attribute was invalid or malformed.
     */
    INVALID_PATH("invalidPath"),
    /**
     * The specified "path" did not  yield an attribute or  attribute value that could be  operated on.
     * This occurs when the specified "path" value contains a filter that yields no match.
     */
    NO_TARGET("noTarget"),
    /**
     * A required value was missing, or the value specified was not compatible with the operation or attribute type (see Section 2.2 of [RFC7643]),
     * or resource schema (see Section 4 of [RFC7643]).
     */
    INVALID_VALUE("invalidValue"),
    /**
     * The specified SCIM protocol version is not supported
     */
    INVALID_VERS("invalidVers"),
    /**
     * The specified request cannot be completed, due to the passing of sensitive (e.g., personal) information in a request URI.
     * For example, personal information SHALL NOT  be transmitted over request  URIs.  See Section 7.5.2.
     */
    SENSITIVE("sensitive");

    private final String value;

    ScimType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
