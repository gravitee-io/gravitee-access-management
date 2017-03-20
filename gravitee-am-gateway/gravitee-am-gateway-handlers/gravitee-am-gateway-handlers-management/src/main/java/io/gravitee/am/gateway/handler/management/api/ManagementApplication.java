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
package io.gravitee.am.gateway.handler.management.api;

import io.gravitee.am.gateway.handler.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.gateway.handler.management.api.provider.*;
import io.gravitee.am.gateway.handler.management.api.resources.DomainsResource;
import io.gravitee.am.gateway.handler.management.api.resources.platform.PlatformResource;
import io.gravitee.common.util.Version;
import io.swagger.jaxrs.config.BeanConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementApplication extends ResourceConfig {

    public ManagementApplication() {

        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setVersion(Version.RUNTIME_VERSION.MAJOR_VERSION);
        beanConfig.setResourcePackage("io.gravitee.am.management.api");
        beanConfig.setTitle("Gravitee.io - Access Management API");
        beanConfig.setScan(true);

        register(DomainsResource.class);
        register(PlatformResource.class);

        register(ObjectMapperResolver.class);
        register(ManagementExceptionMapper.class);
        register(UnrecognizedPropertyExceptionMapper.class);
        register(ThrowableMapper.class);
        register(NotFoundExceptionMapper.class);
        register(BadRequestExceptionMapper.class);

        register(CorsResponseFilter.class);
        register(UriBuilderRequestFilter.class);
        register(ByteArrayOutputStreamWriter.class);
        register(JacksonFeature.class);

        property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true);
    }
}