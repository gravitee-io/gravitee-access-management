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
package io.gravitee.am.management.handlers.management.api;

import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.management.handlers.management.api.provider.*;
import io.gravitee.am.management.handlers.management.api.resources.organizations.CurrentUserResource;
import io.gravitee.am.management.handlers.management.api.resources.organizations.OrganizationsResource;
import io.gravitee.am.management.handlers.management.api.resources.platform.PlatformResource;
import io.gravitee.am.management.handlers.management.api.resources.swagger.GraviteeApiDefinition;
import io.swagger.v3.jaxrs2.SwaggerSerializers;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementApplication extends ResourceConfig {

    public ManagementApplication() {
        packages("io.gravitee.am.management.handlers.management.api.resources");

        register(SortParamConverterProvider.class);
        register(JacksonFeature.withoutExceptionMappers());
        register(JacksonExceptionMapper.class);

        register(OrganizationsResource.class);
        register(PlatformResource.class);
        register(CurrentUserResource.class);

        register(ObjectMapperResolver.class);
        register(ManagementExceptionMapper.class);
        register(UnrecognizedPropertyExceptionMapper.class);
        register(ThrowableMapper.class);
        register(ClientErrorExceptionMapper.class);
        register(Oauth2ExceptionMapper.class);
        register(ValidationExceptionMapper.class);
        register(WebApplicationExceptionMapper.class);
        register(UriBuilderRequestFilter.class);
        register(BulkPayloadSizeFilter.class);
        register(ByteArrayOutputStreamWriter.class);

        register(GraviteeApiDefinition.class);
        register(OpenApiResource.class);
        register(SwaggerSerializers.class);

        property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true);
        property(ServerProperties.WADL_FEATURE_DISABLE, true);
    }
}
