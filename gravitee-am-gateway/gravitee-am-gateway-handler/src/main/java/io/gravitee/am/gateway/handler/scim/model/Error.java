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

import java.util.Arrays;
import java.util.List;

/**
 * The SCIM protocol uses the HTTP response status codes defined in
 *    Section 6 of [RFC7231] to indicate operation success or failure.  In
 *    addition to returning an HTTP response code, implementers MUST return
 *    the errors in the body of the response in a JSON format, using the
 *    attributes described below.  Error responses are identified using the
 *    following "schema" URI:
 *    "urn:ietf:params:scim:api:messages:2.0:Error".  The following
 *    attributes are defined for a SCIM error response using a JSON body:
 *
 *    status
 *       The HTTP status code (see Section 6 of [RFC7231]) expressed as a
 *       JSON string.  REQUIRED.
 *
 *    scimType
 *       A SCIM detail error keyword.  See Table 9.  OPTIONAL.
 *
 *    detail
 *       A detailed human-readable message.  OPTIONAL.
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-3.12">3.12. HTTP Status and Error Response Handling</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Error {

    private static final List<String> SCHEMAS = Arrays.asList("urn:ietf:params:scim:api:messages:2.0:Error");
    private String status;
    private String scimType;
    private String detail;

    public List<String> getSchemas() {
        return SCHEMAS;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getScimType() {
        return scimType;
    }

    public void setScimType(String scimType) {
        this.scimType = scimType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
