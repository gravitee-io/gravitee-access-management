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
package io.gravitee.am.management.handlers.management.api.authentication.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.log.LogMessage;
import org.springframework.http.MediaType;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomRequestRejectedHandler extends HttpStatusRequestRejectedHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomRequestRejectedHandler.class);

    @Autowired
    private ObjectMapper objectMapper;

    public CustomRequestRejectedHandler() {
    }

    public void handle(HttpServletRequest request, HttpServletResponse response, RequestRejectedException requestRejectedException) throws IOException {
        logger.debug(String.format("Rejecting request due to: %s", requestRejectedException.getMessage()), requestRejectedException);
        response.setStatus(SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON.toString());
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorEntity(requestRejectedException.getMessage(), SC_BAD_REQUEST)));
        response.getWriter().close();
    }
}
