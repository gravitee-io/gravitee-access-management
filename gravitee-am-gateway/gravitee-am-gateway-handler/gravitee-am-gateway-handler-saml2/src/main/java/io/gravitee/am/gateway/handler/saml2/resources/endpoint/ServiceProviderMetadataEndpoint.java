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
package io.gravitee.am.gateway.handler.saml2.resources.endpoint;

import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.saml2.service.sp.ServiceProviderService;
import io.gravitee.am.service.exception.AbstractManagementException;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Like the identity provider, a service provider publishes data about itself in an <md:EntityDescriptor> element:
 *
 * - The entityID attribute is the unique identifier of the entity.
 * - The validUntil attribute gives the expiration date of the metadata.
 * - The <ds:Signature> element (which has been omitted for simplicity) contains a digital signature that ensures the authenticity and integrity of the metadata.
 * - The organization identified in the <md:Organization> element is "responsible for the entity" described by the entity descriptor.
 * - The contact information in the <md:ContactPerson> element identifies a technical contact responsible for the entity. Multiple contacts and contact types are possible.
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ServiceProviderMetadataEndpoint implements Handler<RoutingContext> {

    private ServiceProviderService serviceProviderService;

    public ServiceProviderMetadataEndpoint(ServiceProviderService serviceProviderService) {
        this.serviceProviderService = serviceProviderService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String providerId = routingContext.request().getParam("providerId");
        final String basePath = UriBuilderRequest.resolveProxyRequest(routingContext.request(), routingContext.get(CONTEXT_PATH));

        serviceProviderService.metadata(providerId, basePath)
                .subscribe(
                        result -> {
                            // prepare response
                            HttpServerResponse response = routingContext.response()
                                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                    .putHeader(HttpHeaders.PRAGMA, "no-cache");

                            // add custom headers
                            if (result.getHeaders() != null) {
                                result.getHeaders().forEach((k, v) -> response.putHeader(k, v));
                            }

                            // send response
                            response
                                    .setStatusCode(HttpStatusCode.OK_200)
                                    .end(result.getBody());
                        }
                        , error ->
                                routingContext
                                        .response()
                                        .setStatusCode(error instanceof AbstractManagementException ? ((AbstractManagementException) error).getHttpStatusCode() : 500)
                                        .end()
                );
    }
}
