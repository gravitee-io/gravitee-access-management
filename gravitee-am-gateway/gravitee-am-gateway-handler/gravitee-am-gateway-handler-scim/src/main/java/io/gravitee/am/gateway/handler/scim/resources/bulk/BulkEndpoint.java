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

package io.gravitee.am.gateway.handler.scim.resources.bulk;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.utils.Tuple;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.scim.exception.InvalidSyntaxException;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.exception.TooManyOperationException;
import io.gravitee.am.gateway.handler.scim.model.BulkOperation;
import io.gravitee.am.gateway.handler.scim.model.BulkRequest;
import io.gravitee.am.gateway.handler.scim.service.BulkService;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@AllArgsConstructor
public class BulkEndpoint {
    /**
     * Max payload size for Bulk request limited to 1MB
     */
    public final static int BULK_MAX_REQUEST_LENGTH = 1048576; // TODO make this configurable in AM-3572

    /**
     * Maximum number of operations for Bulk request
     */
    public final static int BULK_MAX_REQUEST_OPERATIONS = 1000; // TODO make this configurable in AM-3572

    private BulkService bulkService;
    private ObjectMapper objectMapper;
    private SubjectManager subjectManager;

    public void execute(RoutingContext context) {
        // accessToken is used to determine the principal user
        final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);

        parseRequestBody(context)
                .switchIfEmpty(Maybe.error(() -> new InvalidSyntaxException("BulkRequest is required")))
                .toSingle()
                .map(this::checkBulkRequest)
                .zipWith(Single.defer(() -> principal(accessToken)), Tuple::of)
                .flatMap(tuple -> {
                    final var bulkRequest = tuple.getT1();
                    final var principal = tuple.getT2();

                    // we need to build a context in order to evaluate the sourceId during user creation
                    SimpleAuthenticationContext authenticationContext = new SimpleAuthenticationContext(new VertxHttpServerRequest(context.request().getDelegate()));
                    authenticationContext.attributes().putAll(context.data());

                    return bulkService.processBulkRequest(bulkRequest, authenticationContext, location(context.request()), context.get(ConstantKeys.CLIENT_CONTEXT_KEY), principal.orElse(null));
                })
                .subscribe(bulkResponse -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(bulkResponse)),
                        context::fail);
    }

    private Maybe<@NonNull BulkRequest> parseRequestBody(RoutingContext context) {
        return Maybe.fromSupplier(() -> {
            try {
                return context.body().asPojo(BulkRequest.class, BULK_MAX_REQUEST_LENGTH);// TODO make this configurable in AM-3572
            } catch (IllegalStateException e) {
                log.warn("The size of the bulk operation exceeds the maxPayloadSize ");
                throw TooManyOperationException.payloadLimitReached(BULK_MAX_REQUEST_LENGTH);// TODO make this configurable in AM-3572
            }
        });
    }

    private BulkRequest checkBulkRequest(BulkRequest bulkRequest) {
        if (isEmpty(bulkRequest.getOperations())) {
            throw new InvalidValueException("Bulk request requires at least one operation");
        }
        if (bulkRequest.getOperations().size() > BULK_MAX_REQUEST_OPERATIONS) {// TODO make this configurable in AM-3572
            throw TooManyOperationException.tooManyOperation(BULK_MAX_REQUEST_OPERATIONS);// TODO make this configurable in AM-3572
        }
        final var allBulkId = bulkRequest.getOperations().stream().filter(op -> op.getMethod() == HttpMethod.POST || hasText(op.getBulkId())).map(BulkOperation::getBulkId).count();
        final var uniqBulkId = bulkRequest.getOperations().stream().filter(op -> op.getMethod() == HttpMethod.POST || hasText(op.getBulkId())).map(BulkOperation::getBulkId).distinct().count();
        if (allBulkId != uniqBulkId){
            throw new InvalidValueException("bulkId must be unique across all Operations");
        }
        return bulkRequest;
    }

    protected Single<Optional<io.gravitee.am.identityprovider.api.User>> principal(JWT jwt) {
        return this.subjectManager.getPrincipal(jwt)
                .map(Optional::ofNullable)
                .switchIfEmpty(Maybe.just(Optional.empty()))
                .toSingle();
    }

    protected String location(HttpServerRequest request) {
        return UriBuilderRequest.resolveProxyRequest(request, request.path());
    }
}
