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
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.gateway.handler.scim.exception.SCIMException;
import io.gravitee.am.gateway.handler.scim.exception.UnauthorizedException;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.ext.web.handler.HttpException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    public static Optional<Error> fromThrowable(Throwable throwable) {
        Optional<Error> result = Optional.empty();
        if (throwable instanceof AbstractManagementException technicalManagementException) {
            result = Optional.of(buildError(technicalManagementException.getHttpStatusCode(), technicalManagementException.getMessage(), null));
        } else if (throwable instanceof OAuth2Exception oAuth2Exception) {
            result = Optional.of(buildError(oAuth2Exception.getHttpStatusCode(), oAuth2Exception.getMessage(), null));
        } else if (throwable instanceof SCIMException scimException) {
            result = Optional.of(buildError(scimException.getHttpStatusCode(), scimException.getMessage(), scimException.getScimType()));
        } else if (throwable instanceof HttpException httpException) {
            if (401 == httpException.getStatusCode()) {
                UnauthorizedException unauthorizedException = new UnauthorizedException();
                result = Optional.of(buildError(unauthorizedException.getHttpStatusCode(), unauthorizedException.getMessage(), null));
            }
        } else if (throwable instanceof PolicyChainException) {
            PolicyChainException policyChainException = (PolicyChainException) throwable;
            result = Optional.of(buildError(policyChainException.statusCode(), policyChainException.key() + " : " + policyChainException.getMessage(), null));
        }
        return result;
    }

    private static Error buildError(int httpStatusCode, String errorDetail, ScimType scimType) {
        Error error = new Error();
        error.setStatus(String.valueOf(httpStatusCode));
        error.setDetail(errorDetail);
        if (scimType != null) {
            error.setScimType(scimType.value());
        } else if(httpStatusCode == HttpStatusCode.BAD_REQUEST_400) {
            error.setScimType(ScimType.INVALID_VALUE.value());
        }
        return error;
    }
}
