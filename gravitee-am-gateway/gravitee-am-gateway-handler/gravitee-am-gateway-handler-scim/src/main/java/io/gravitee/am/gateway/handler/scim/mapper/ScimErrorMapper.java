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
package io.gravitee.am.gateway.handler.scim.mapper;

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.gateway.handler.scim.exception.SCIMException;
import io.gravitee.am.gateway.handler.scim.exception.UnauthorizedException;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.Error;
import io.gravitee.am.gateway.handler.scim.model.ScimType;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.ext.web.handler.HttpException;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class ScimErrorMapper {

    private final boolean includeErrorDetails;

    public Optional<Error> fromThrowable(Throwable throwable) {
        return switch (throwable) {
            case AbstractManagementException ex ->
                    Optional.of(buildError(ex.getHttpStatusCode(), ex.getMessage(), null));
            case OAuth2Exception ex -> Optional.of(buildError(ex.getHttpStatusCode(), ex.getMessage(), null));
            case UniquenessException ex -> Optional.of(buildErrorWithDetails(ex));
            case SCIMException ex -> Optional.of(buildError(ex.getHttpStatusCode(), ex.getMessage(), ex.getScimType()));
            case HttpException ex when 401 == ex.getStatusCode() -> Optional.of(buildError(401, UnauthorizedException.MESSAGE, null));
            case PolicyChainException ex -> Optional.of(buildError(ex.statusCode(), ex.key() + " : " + ex.getMessage(), null));
            case null, default -> Optional.empty();
        };
    }

    private Error buildErrorWithDetails(UniquenessException ex) {
        Error error = buildError(ex.getHttpStatusCode(), ex.getMessage(), ex.getScimType());
        if (includeErrorDetails && (ex.getExistingUserId() != null || ex.getExistingUsername() != null)) {
            return error.withErrorDetails(Error.ErrorDetails.builder()
                    .existingUserId(ex.getExistingUserId())
                    .existingUsername(ex.getExistingUsername())
                    .build());
        } else {
            return error;
        }
    }

    private Error buildError(int httpStatusCode, String errorDetail, ScimType scimType) {
        Error error = Error.withDefaultSchemas();
        error.setStatus(String.valueOf(httpStatusCode));
        error.setDetail(errorDetail);
        if (scimType != null) {
            error.setScimType(scimType.value());
        } else if (httpStatusCode == HttpStatusCode.BAD_REQUEST_400) {
            error.setScimType(ScimType.INVALID_VALUE.value());
        }
        return error;
    }
}
