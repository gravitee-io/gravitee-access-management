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
import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.filter.GenericFilterBean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;

/**
 * Control allowed URLs for the login callback url
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CheckRedirectUriFilter extends GenericFilterBean {

    private String path;
    private String paramName;
    private List<String> allowedUrls;

    @Autowired
    private ObjectMapper objectMapper;

    public CheckRedirectUriFilter() {
    }

    public CheckRedirectUriFilter(String path) {
        this.path = path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public void setAllowedUrls(List<String> allowedUrls) {
        this.allowedUrls = allowedUrls;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        if (request.getPathInfo() == null) {
            chain.doFilter(req, resp);
            return;
        }

        if (!request.getPathInfo().endsWith(path)) {
            chain.doFilter(req, resp);
            return;
        }

        // check redirect_uri parameter
        final String redirectUri = request.getParameter(paramName);

        if (redirectUri == null) {
            chain.doFilter(req, resp);
            return;
        }

        if (allowedUrls == null || allowedUrls.isEmpty()) {
            chain.doFilter(req, resp);
            return;
        }

        if (allowedUrls.size() == 1 && allowedUrls.contains("*")) {
            chain.doFilter(req, resp);
            return;
        }

        if (allowedUrls.stream().anyMatch(url -> url.equals(redirectUri))) {
            chain.doFilter(req, resp);
            return;
        }

        response.setStatus(SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON.toString());
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorEntity("The redirect_uri or target_url MUST match the registered callback URL for this application", SC_FORBIDDEN)));
        response.getWriter().close();
    }
}
