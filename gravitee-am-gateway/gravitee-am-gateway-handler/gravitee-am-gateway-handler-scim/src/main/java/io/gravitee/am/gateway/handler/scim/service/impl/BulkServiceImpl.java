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

package io.gravitee.am.gateway.handler.scim.service.impl;


import io.gravitee.am.gateway.handler.scim.business.CreateUserAction;
import io.gravitee.am.gateway.handler.scim.business.PatchUserAction;
import io.gravitee.am.gateway.handler.scim.business.UpdateUserAction;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.BulkOperation;
import io.gravitee.am.gateway.handler.scim.model.BulkRequest;
import io.gravitee.am.gateway.handler.scim.model.BulkResponse;
import io.gravitee.am.gateway.handler.scim.model.Error;
import io.gravitee.am.gateway.handler.scim.service.BulkService;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.regex.Pattern;

import static io.gravitee.common.http.HttpMethod.DELETE;
import static io.gravitee.common.http.HttpMethod.PATCH;
import static io.gravitee.common.http.HttpMethod.POST;
import static io.gravitee.common.http.HttpMethod.PUT;
import static java.lang.String.valueOf;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasLength;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class BulkServiceImpl implements BulkService {
    public static final Set<HttpMethod> ALLOWED_METHODS = Set.of(DELETE, POST, PATCH, PUT);

    private final static String USERS_PATH = "/Users";
    private final static String BULK_PATH_PATTERN = "/Bulk(/)?";
    private final static Pattern USER_PATH_PATTERN = Pattern.compile("/Users/(.*)");

    private UserService userService;
    private Domain domain;

    @Override
    public Single<BulkResponse> processBulkRequest(BulkRequest bulkRequest, AuthenticationContext authenticationContext, String baseUrl, Client client, User principal) {
        return Flowable.fromIterable(bulkRequest.getOperations())
                .concatMapSingle(operation -> checkOperation(operation)
                            .flatMap(validOperation -> {
                                switch (validOperation.getMethod()) {
                                    case POST:
                                        return new CreateUserAction(userService, domain, client)
                                                .execute(updateBaseUrl(baseUrl, validOperation.getPath()), operation.getData(), authenticationContext, principal)
                                                .map(scimUser -> {
                                                    validOperation.setLocation(scimUser.getMeta().getLocation());
                                                    validOperation.setStatus(valueOf(HttpStatusCode.CREATED_201));
                                                    // response attribute is not set for successful operation
                                                    // so no need to provide the scimUser in the operation.response
                                                    return validOperation.asResponse();
                                                });
                                    case PUT:
                                        return new UpdateUserAction(userService, domain, client)
                                                .execute(extractUserIdFromPath(validOperation), updateBaseUrl(baseUrl, validOperation.getPath()), operation.getData(), authenticationContext, principal)
                                                .map(scimUser -> {
                                                    validOperation.setLocation(scimUser.getMeta().getLocation());
                                                    validOperation.setStatus(valueOf(HttpStatusCode.OK_200));
                                                    // response attribute is not set for successful operation
                                                    // so no need to provide the scimUser in the operation.response
                                                    return validOperation.asResponse();
                                                });
                                    case PATCH:
                                        return new PatchUserAction(userService, domain, client)
                                                .execute(extractUserIdFromPath(validOperation), updateBaseUrl(baseUrl, validOperation.getPath()), operation.getData(), authenticationContext, principal)
                                                .map(scimUser -> {
                                                    validOperation.setLocation(scimUser.getMeta().getLocation());
                                                    validOperation.setStatus(valueOf(HttpStatusCode.OK_200));
                                                    // response attribute is not set for successful operation
                                                    // so no need to provide the scimUser in the operation.response
                                                    return validOperation.asResponse();
                                                });
                                    case DELETE:
                                        return userService.delete(extractUserIdFromPath(validOperation), principal)
                                                .andThen(Single.fromSupplier(() -> {
                                                    validOperation.setStatus(valueOf(HttpStatusCode.NO_CONTENT_204));
                                                    validOperation.setLocation(updateBaseUrl(baseUrl, validOperation.getPath()));
                                                    return validOperation.asResponse();
                                                }));
                                    default:
                                        io.gravitee.am.gateway.handler.scim.model.Error error = new Error();
                                        error.setScimType("invalidSyntax"); // should not happen
                                        validOperation.setResponse(error);
                                        return Single.just(validOperation.asResponse());

                                }
                            })
                            .onErrorResumeNext(ex-> {
                                final var knownError = io.gravitee.am.gateway.handler.scim.model.Error.fromThrowable(ex);
                                if (knownError.isPresent()) {
                                    operation.setResponse(knownError.get());
                                    operation.setStatus(knownError.get().getStatus());
                                } else {
                                    io.gravitee.am.gateway.handler.scim.model.Error error = new io.gravitee.am.gateway.handler.scim.model.Error();
                                    error.setStatus(valueOf(HttpStatusCode.INTERNAL_SERVER_ERROR_500));
                                    error.setDetail(ex.getMessage());
                                    operation.setResponse(error);
                                    operation.setStatus(error.getStatus());
                                }
                                return Single.just(operation.asResponse());
                            })
                ).toList()
                .map(responses -> {
                    final var bulkResponse = new BulkResponse();
                    bulkResponse.setOperations(responses);
                    return bulkResponse;
                });
    }

    private static String extractUserIdFromPath(BulkOperation validOperation) {
        return validOperation.getPath().substring(validOperation.getPath().lastIndexOf("/") + 1);
    }

    private Single<BulkOperation> checkOperation(BulkOperation operation) {
        if (!(hasLength(operation.getPath()) && operation.getPath().startsWith(USERS_PATH))) {
            // only Users operations are managed currently
            log.debug("Bulk operation requires path starting with /Users");
            return Single.error(new InvalidValueException("Bulk operation requires path starting with /Users"));
        }

        if (!ALLOWED_METHODS.contains(operation.getMethod())) {
            log.debug("Bulk operation doesn't support method {}", operation.getMethod());
            return Single.error(new InvalidValueException("Bulk operation doesn't support method " + operation.getMethod()));
        }

        if (operation.getMethod() != DELETE && isEmpty(operation.getData())) {
            log.debug("Bulk operation requires data with method {}", operation.getMethod());
            return Single.error(new InvalidValueException("Bulk operation requires data with method " + operation.getMethod()));
        }

        if (operation.getMethod() == POST && !hasText(operation.getBulkId())) {
            log.debug("Bulk operation requires bulkId with method POST");
            return Single.error(new InvalidValueException("Bulk operation requires bulkId with method POST"));
        }

        if ((operation.getMethod() == POST && USER_PATH_PATTERN.matcher(operation.getPath()).matches())
                || (operation.getMethod() != POST && !USER_PATH_PATTERN.matcher(operation.getPath()).matches())) {
            // only Users operations are managed currently
            log.debug("Bulk operation with PUT or PATCH method requires path with userId");
            return Single.error(new InvalidValueException("Bulk operation with PUT or PATCH method requires path with userId"));
        }

        return Single.just(operation);
    }

    private String updateBaseUrl(String baseUrl, String path) {
       return baseUrl.replaceFirst(BULK_PATH_PATTERN, path);
    }

}
