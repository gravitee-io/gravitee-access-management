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
package io.gravitee.am.management.handlers.management.api.authentication.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.service.ReCaptchaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.GenericFilterBean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RecaptchaFilter extends GenericFilterBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecaptchaFilter.class);
    public static final String DEFAULT_RECAPTCHA_HEADER_NAME = "X-Recaptcha-Token";
    private static final Set<String> RESTRICTED_PATHS = new HashSet<>(Arrays.asList("POST /login"));

    private ReCaptchaService reCaptchaService;
    private ObjectMapper objectMapper;

    public RecaptchaFilter(ReCaptchaService reCaptchaService, ObjectMapper objectMapper) {
        this.reCaptchaService = reCaptchaService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if(reCaptchaService.isEnabled() && RESTRICTED_PATHS.contains(httpRequest.getMethod() + " " + httpRequest.getPathInfo())) {

            LOGGER.debug("Checking captcha");
            String reCaptchaToken = httpRequest.getHeader(DEFAULT_RECAPTCHA_HEADER_NAME);

            if (reCaptchaToken == null) {
                reCaptchaToken = httpRequest.getParameter(DEFAULT_RECAPTCHA_HEADER_NAME);
            }

            if(!reCaptchaService.isValid(reCaptchaToken).blockingGet()) {

                HashMap<String, Object> error = new HashMap<>();

                error.put("message", "Something goes wrong. Please try again.");
                error.put("http_status", SC_BAD_REQUEST);

                httpResponse.setStatus(SC_BAD_REQUEST);
                httpResponse.setContentType(MediaType.APPLICATION_JSON.toString());
                httpResponse.getWriter().write(objectMapper.writeValueAsString(error));
                httpResponse.getWriter().close();
            }else {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}