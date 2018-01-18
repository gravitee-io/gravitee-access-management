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
package io.gravitee.am.gateway.handler.oauth2.controller;

import io.gravitee.am.gateway.handler.oauth2.security.IdentityProviderManager;
import io.gravitee.am.gateway.service.ClientService;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProviderConfiguration;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.common.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.common.exceptions.InvalidRequestException;
import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Controller
public class LoginController {

    private final static Logger logger = LoggerFactory.getLogger(LoginController.class);
    private final static String LOGIN_VIEW = "login";
    private final static List<String> socialProviders = Arrays.asList("github", "google", "twitter", "facebook", "bitbucket");

    @Autowired
    private ClientService clientService;

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @RequestMapping(value = "/login")
    public ModelAndView login(
            @RequestParam(value = OAuth2Utils.CLIENT_ID) String clientId, HttpServletRequest request) {
        if (clientId == null || clientId.isEmpty()) {
            logger.error(OAuth2Utils.CLIENT_ID + " parameter is required");
            return new ModelAndView("access_error");
        }

        Client client = clientService.findByDomainAndClientId(domain.getId(), clientId);

        Map<String, Object> params = new HashMap<>();
        params.put(OAuth2Utils.CLIENT_ID, client.getClientId());
        params.put("domain", domain);

        Set<String> clientOAuth2Providers = client.getOauth2Identities();
        if (clientOAuth2Providers != null && !clientOAuth2Providers.isEmpty()) {
            params.put("oauth2Providers", clientOAuth2Providers.stream().map(id -> {
                IdentityProvider identityProvider = identityProviderManager.getIdentityProvider(id);
                String identityProviderType = identityProvider.getType();
                Optional<String> identityProviderSocialType = socialProviders.stream().filter(socialProvider -> identityProviderType.toLowerCase().contains(socialProvider)).findFirst();
                if (identityProviderSocialType.isPresent()) {
                    identityProvider.setType(identityProviderSocialType.get());
                }
                return identityProvider;
            }).collect(Collectors.toSet()));

            Map<String, String> authorizeUrls = new HashMap<>();
            clientOAuth2Providers.forEach(identity -> {
                OAuth2AuthenticationProvider oAuth2AuthenticationProvider = (OAuth2AuthenticationProvider) identityProviderManager.get(identity);
                if (oAuth2AuthenticationProvider != null) {
                    OAuth2IdentityProviderConfiguration configuration = oAuth2AuthenticationProvider.configuration();
                    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(configuration.getUserAuthorizationUri());
                    builder.queryParam(OAuth2Utils.CLIENT_ID, configuration.getClientId());
                    builder.queryParam(OAuth2Utils.REDIRECT_URI, buildRedirectUri(request, identity));
                    if (configuration.getScopes() != null && !configuration.getScopes().isEmpty()) {
                        builder.queryParam(OAuth2Utils.SCOPE, String.join(" ", configuration.getScopes()));
                    }
                    authorizeUrls.put(identity, builder.build(false).toUriString());
                }
            });
            params.put("authorizeUrls", authorizeUrls);
        }

        return new ModelAndView(LOGIN_VIEW, params);
    }

    @RequestMapping(value = "/login/callback")
    public void loginCallback(HttpServletResponse response, HttpSession session) throws IOException {
        if (session != null && session.getAttribute("GRAVITEEIO_AM_SAVED_REQUEST") != null) {
            final SavedRequest savedRequest = (SavedRequest) session.getAttribute("GRAVITEEIO_AM_SAVED_REQUEST");
            response.sendRedirect(savedRequest.getRedirectUrl());
        } else {
            response.sendRedirect("/login");
        }
    }

    private String buildRedirectUri(HttpServletRequest request, String identity) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();

        String scheme = request.getHeader(HttpHeaders.X_FORWARDED_PROTO);
        if (scheme != null && !scheme.isEmpty()) {
            builder.scheme(scheme);
        } else {
            builder.scheme(request.getScheme());
        }

        String host = request.getHeader(HttpHeaders.X_FORWARDED_HOST);
        if (host != null && !host.isEmpty()) {
            if (host.contains(":")) {
                // Forwarded host contains both host and port
                String [] parts = host.split(":");
                builder.host(parts[0]);
                builder.port(parts[1]);
            } else {
                builder.host(host);
            }
        } else {
            builder.host(request.getServerName());
            if (request.getServerPort() != 80 && request.getServerPort() != 443) {
                builder.port(request.getServerPort());
            }
        }
        // append context path
        builder.path(request.getContextPath());
        builder.pathSegment("login/callback");

        // append identity provider id
        builder.queryParam("provider", identity);

        return builder.build().toUriString();
    }

}
