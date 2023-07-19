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
package io.gravitee.am.management.handlers.management.api.authentication.controller;

import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.ReCaptchaService;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.swing.text.html.Option;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static io.gravitee.am.management.handlers.management.api.authentication.provider.generator.RedirectCookieGenerator.DEFAULT_REDIRECT_COOKIE_NAME;
import static java.util.Collections.emptyList;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Controller
public class LoginController {

    public static final String ORGANIZATION_PARAMETER_NAME = "org";

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginController.class);
    private static final String LOGIN_VIEW = "login";
    private static final Map<String, String> socialProviderTypes;
    static {
        Map<String, String> sMap = new HashMap<>();
        sMap.put("github-am-idp", "github");
        sMap.put("google-am-idp", "google");
        sMap.put("twitter-am-idp", "twitter");
        sMap.put("facebook-am-idp", "facebook");
        sMap.put("franceconnect-am-idp", "franceconnect");
        sMap.put("azure-ad-am-idp", "microsoft");
        sMap.put("linkedin-am-idp", "linkedin");
        socialProviderTypes = Collections.unmodifiableMap(sMap);
    }

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private ReCaptchaService reCaptchaService;

    @RequestMapping(value = "/login")
    public ModelAndView login(HttpServletRequest request, @RequestParam(value=ORGANIZATION_PARAMETER_NAME, defaultValue = "DEFAULT") String organizationId) {
        Map<String, Object> params = new HashMap<>();

        // fetch domain social identity providers
        List<IdentityProvider> socialProviders = null;
        try {
            socialProviders = organizationService.findById(organizationId)
                    .map(org -> Optional.ofNullable(org.getIdentities()).orElse(emptyList()))
                    .blockingGet()
                    .stream()
                    .map(identity -> identityProviderManager.getIdentityProvider(identity))
                    .filter(Objects::nonNull)
                    .filter(IdentityProvider::isExternal)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            LOGGER.error("An error has occurred while loading the organization social providers. It probably means that a social provider is not well started", ex);
        }

        // enhance social providers data
        if (socialProviders != null && !socialProviders.isEmpty()) {
            Set<IdentityProvider> enhancedSocialProviders = socialProviders.stream().map(identityProvider -> {
                // get social identity provider type (currently use for display purpose (logo, description, ...)
                identityProvider.setType(socialProviderTypes.getOrDefault(identityProvider.getType(), identityProvider.getType()));
                return identityProvider;
            }).collect(Collectors.toSet());

            Map<String, String> authorizeUrls = new HashMap<>();
            socialProviders.forEach(identity -> {
                String identityId = identity.getId();
                SocialAuthenticationProvider socialAuthenticationProvider = (SocialAuthenticationProvider) identityProviderManager.get(identityId);
                if (socialAuthenticationProvider != null) {
                    final Maybe<Optional<Request>> maybe = socialAuthenticationProvider.asyncSignInUrl(buildRedirectUri(request, identityId), RandomString.generate()).map(Optional::ofNullable);
                    maybe.blockingGet()
                            .ifPresent(idpAuthzRequest -> authorizeUrls.put(identityId, idpAuthzRequest.getUri()));
                }
            });

            params.put("oauth2Providers", enhancedSocialProviders);
            params.put("socialProviders", enhancedSocialProviders);
            params.put("authorizeUrls", authorizeUrls);
        }

        params.put("reCaptchaEnabled", reCaptchaService.isEnabled());
        params.put("reCaptchaSiteKey", reCaptchaService.getSiteKey());
        params.put("org", organizationId);

        return new ModelAndView(organizationId + "#" + LOGIN_VIEW, params);
    }

    @RequestMapping(value = "/login/callback")
    public void loginCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Redirect to the original request.
        response.sendRedirect((String) request.getAttribute(DEFAULT_REDIRECT_COOKIE_NAME));
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
                String[] parts = host.split(":");
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
        builder.pathSegment("auth/login/callback");

        // append identity provider id
        builder.queryParam("provider", identity);

        return builder.build().toUriString();
    }

}
