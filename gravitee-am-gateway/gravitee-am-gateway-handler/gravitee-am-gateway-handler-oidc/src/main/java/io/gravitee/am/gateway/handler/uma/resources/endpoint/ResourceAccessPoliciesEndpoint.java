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
package io.gravitee.am.gateway.handler.uma.resources.endpoint;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.model.uma.policy.AccessPolicyCondition;
import io.gravitee.am.model.uma.policy.AccessPolicyType;
import io.gravitee.am.service.ResourceService;
import io.gravitee.am.service.exception.AccessPolicyNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Single;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;

import static io.gravitee.am.gateway.handler.uma.constants.UMAConstants.*;
import static io.gravitee.am.gateway.handler.uma.constants.UMAConstants.RESOURCE_REGISTRATION_PATH;

/**
 * <pre>
 * A URI that allows the resource server to redirect an end-user resource owner to a specific user interface within the authorization server where the resource owner
 * can immediately set or modify access policies subsequent to the resource registration action just completed.
 *
 * See <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#reg-api">3.2 Resource Registration API</a>
 * </pre>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourceAccessPoliciesEndpoint {

    private Domain domain;
    private ResourceService resourceService;

    public ResourceAccessPoliciesEndpoint(Domain domain, ResourceService resourceService) {
        this.domain = domain;
        this.resourceService = resourceService;
    }

    public void list(RoutingContext context) {
        final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String resource = context.request().getParam(RESOURCE_ID);

        resourceService.findAccessPolicies(domain.getId(), client.getId(), accessToken.getSub(), resource)
                .map(AccessPolicy::getId)
                .toList()
                .subscribe(
                        response -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(response.isEmpty() ? HttpStatusCode.NO_CONTENT_204 : HttpStatusCode.OK_200)
                                .end(Json.encodePrettily(response))
                        , error -> context.fail(error)
                );
    }

    public void create(RoutingContext context) {
        final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String resource = context.request().getParam(RESOURCE_ID);
        final String basePath = UriBuilderRequest.resolveProxyRequest(context);

        // extract access policy payload
        AccessPolicy accessPolicy = extractRequest(context);

        // store the access policy
        resourceService.createAccessPolicy(accessPolicy, domain.getId(), client.getId(), accessToken.getSub(), resource)
                .subscribe(
                        p ->
                            context.response()
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                    .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                    .putHeader(HttpHeaders.LOCATION, resourceLocation(basePath, p))
                                    .setStatusCode(HttpStatusCode.CREATED_201)
                                    .end(Json.encodePrettily(p))
                        , error -> context.fail(error)
                );
    }

    public void get(RoutingContext context) {
        final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String resource = context.request().getParam(RESOURCE_ID);
        final String accessPolicyId = context.request().getParam(POLICY_ID);

        resourceService.findAccessPolicy(domain.getId(), client.getId(), accessToken.getSub(), resource, accessPolicyId)
                .switchIfEmpty(Single.error(new AccessPolicyNotFoundException(accessPolicyId)))
                .subscribe(
                        response -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(Json.encodePrettily(response))
                        , error -> context.fail(error)
                );
    }

    public void update(RoutingContext context) {
        final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String resource = context.request().getParam(RESOURCE_ID);
        final String accessPolicyId = context.request().getParam(POLICY_ID);

        // extract access policy payload
        AccessPolicy accessPolicy = extractRequest(context);

        // update the access policy
        resourceService.updateAccessPolicy(accessPolicy, domain.getId(), client.getId(), accessToken.getSub(), resource, accessPolicyId)
                .subscribe(
                        response -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(Json.encodePrettily(response))
                        , error -> context.fail(error)
                );
    }

    public void delete(RoutingContext context) {
        final JWT accessToken = context.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String resource = context.request().getParam(RESOURCE_ID);
        final String accessPolicy = context.request().getParam(POLICY_ID);

        resourceService.deleteAccessPolicy(domain.getId(), client.getId(), accessToken.getSub(), resource, accessPolicy)
                .subscribe(
                        () -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .setStatusCode(HttpStatusCode.NO_CONTENT_204)
                                .end()
                        , error -> context.fail(error)
                );
    }

    private AccessPolicy extractRequest(RoutingContext context) {
        try {
            // get body request
            JsonObject body = context.getBodyAsJson();
            // check missing values
            Arrays.asList("name", "type", "description", "condition").forEach(key -> {
                if (!body.containsKey(key)) {
                    throw new InvalidRequestException("["+ key +": must not be null]");
                }
            });
            // check type value
            AccessPolicyType accessPolicyType = AccessPolicyType.fromString(body.getString("type"));
            // check condition value
            AccessPolicyCondition condition = body.getJsonObject("condition").mapTo(accessPolicyType.getConditionClazz());
            // create access policy object
            AccessPolicy accessPolicy = new AccessPolicy();
            accessPolicy.setType(accessPolicyType);
            accessPolicy.setName(body.getString("name"));
            accessPolicy.setDescription(body.getString("description"));
            accessPolicy.setCondition(condition.toString());
            accessPolicy.setEnabled(body.getBoolean("enabled", true));
            return accessPolicy;
        } catch (DecodeException ex) {
            throw new InvalidRequestException("Bad request payload");
        } catch (Exception ex) {
            throw new InvalidRequestException(ex.getMessage());
        }
    }

    private String resourceLocation(String basePath, AccessPolicy accessPolicy) {
        return new StringBuilder()
                .append(basePath)
                .append(UMA_PATH)
                .append(RESOURCE_REGISTRATION_PATH)
                .append("/")
                .append(accessPolicy.getResource())
                .append(RESOURCE_ACCESS_POLICIES_PATH)
                .append("/")
                .append(accessPolicy.getId())
                .toString();
    }
}
