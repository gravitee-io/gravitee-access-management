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
package io.gravitee.am.management.handlers.management.api.provider;

import com.fasterxml.jackson.core.JacksonException;
import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import io.gravitee.common.http.HttpStatusCode;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukasz GAWEL (lukasz.gawel at graviteesource.com)
 * @author GraviteeSource Team
 */
@Provider
public class JacksonExceptionMapper extends AbstractExceptionMapper<JacksonException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JacksonExceptionMapper.class);

    @Override
    public Response toResponse(JacksonException e) {
        LOGGER.debug("Malformed json, msg={}", e.getMessage());
        return Response
                .status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorEntity("Malformed json", HttpStatusCode.BAD_REQUEST_400))
                .build();
    }
}
