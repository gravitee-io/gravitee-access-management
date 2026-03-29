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
package io.gravitee.am.management.handlers.automation;

import io.gravitee.am.management.handlers.automation.resource.DomainResource;
import io.gravitee.am.management.handlers.automation.resource.DomainsResource;
import io.gravitee.am.management.handlers.automation.resource.EnvironmentResource;
import io.gravitee.am.management.handlers.automation.resource.EnvironmentsResource;
import io.gravitee.am.management.handlers.automation.resource.IdentityProviderResource;
import io.gravitee.am.management.handlers.automation.resource.IdentityProvidersResource;
import io.gravitee.am.management.handlers.automation.resource.OrganizationResource;
import io.gravitee.am.management.handlers.automation.swagger.AutomationApiDefinition;
import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.management.handlers.management.api.provider.ManagementExceptionMapper;
import io.gravitee.am.management.handlers.management.api.provider.ThrowableMapper;
import io.gravitee.am.management.handlers.management.api.provider.ValidationExceptionMapper;
import io.gravitee.am.management.handlers.management.api.provider.WebApplicationExceptionMapper;
import io.swagger.v3.jaxrs2.SwaggerSerializers;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;

/**
 * JAX-RS Application for the Automation API.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
public class AutomationApplication extends ResourceConfig {

    public AutomationApplication() {
        register(OrganizationResource.class);
        register(EnvironmentsResource.class);
        register(EnvironmentResource.class);
        register(DomainsResource.class);
        register(DomainResource.class);
        register(IdentityProvidersResource.class);
        register(IdentityProviderResource.class);

        register(ObjectMapperResolver.class);
        register(ManagementExceptionMapper.class);
        register(ValidationExceptionMapper.class);
        register(WebApplicationExceptionMapper.class);
        register(ThrowableMapper.class);

        // Swagger / OpenAPI auto-generation
        register(AutomationApiDefinition.class);
        register(OpenApiResource.class);
        register(SwaggerSerializers.class);

        property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true);
        property(ServerProperties.BV_DISABLE_VALIDATE_ON_EXECUTABLE_OVERRIDE_CHECK, true);
    }
}
