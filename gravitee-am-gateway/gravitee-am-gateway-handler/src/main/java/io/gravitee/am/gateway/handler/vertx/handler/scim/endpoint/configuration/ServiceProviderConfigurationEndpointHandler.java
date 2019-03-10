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
package io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.scim.ServiceProviderConfigService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * An HTTP GET to this endpoint will return a JSON structure that describes the SCIM specification features available on a service provider.
 *
 * See <a href="https://tools.ietf.org/html/rfc7644#section-4">4. Service Provider Configuration Endpoints</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne@graviteesource.com)
 * @author GraviteeSource Team
 */
public class ServiceProviderConfigurationEndpointHandler implements Handler<RoutingContext> {

    private ServiceProviderConfigService serviceProviderConfigService;
    private ObjectMapper objectMapper;

    public ServiceProviderConfigurationEndpointHandler(ServiceProviderConfigService serviceProviderConfigService) {
        this.serviceProviderConfigService = serviceProviderConfigService;
    }

    @Override
    public void handle(RoutingContext context) {
        serviceProviderConfigService.get()
                .subscribe(
                        config -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config)),
                        error -> context.fail(error));
    }

    public static ServiceProviderConfigurationEndpointHandler create(ServiceProviderConfigService serviceProviderConfigService) {
        return new ServiceProviderConfigurationEndpointHandler(serviceProviderConfigService);
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
