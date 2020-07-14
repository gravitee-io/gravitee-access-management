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
package io.gravitee.am.management.handlers.management.api.resources.swagger;

import io.swagger.annotations.SwaggerDefinition;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderListener;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@SwaggerDefinition
public class GraviteeApiDefinition implements ReaderListener {

    public static final String TOKEN_AUTH_SCHEME = "gravitee-auth";

    @Override
    public void beforeScan(Reader reader, Swagger swagger) { }

    @Override
    public void afterScan(Reader reader, Swagger swagger) {
        swagger.addSecurityDefinition(TOKEN_AUTH_SCHEME, new ApiKeyAuthDefinition("Authorization", In.HEADER));
        swagger.getPaths().values()
                .stream()
                .forEach(
                        path -> path.getOperations()
                                .stream()
                                .forEach(
                                        operation -> operation.addSecurity(GraviteeApiDefinition.TOKEN_AUTH_SCHEME, null)));
    }
}
