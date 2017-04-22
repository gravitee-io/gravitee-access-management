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
package io.gravitee.am.gateway.handler.management.api.provider;

import io.gravitee.am.gateway.service.exception.AbstractManagementException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Provider
public class ManagementExceptionMapper extends AbstractExceptionMapper<AbstractManagementException> {

    @Override
    public Response toResponse(AbstractManagementException mex) {
        return Response
                .status(mex.getHttpStatusCode())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(convert(mex))
                .build();
    }
}
