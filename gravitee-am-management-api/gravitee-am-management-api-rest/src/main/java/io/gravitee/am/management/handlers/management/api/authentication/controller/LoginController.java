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

import io.gravitee.am.common.crypto.CryptoUtils;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.management.handlers.management.api.utils.RedirectUtils;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.ReCaptchaService;
import io.reactivex.rxjava3.core.Single;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private static final Map<String, String> socialProviderTypes = Map.of(
            "github-am-idp", "github",
            "google-am-idp", "google",
            "twitter-am-idp", "twitter",
            "facebook-am-idp", "facebook",
            "franceconnect-am-idp", "franceconnect",
            "azure-ad-am-idp", "microsoft",
            "linkedin-am-idp", "linkedin"
    );

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Autowired
    private ReCaptchaService reCaptchaService;

    @Autowired
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Autowired
    @Qualifier("managementSecretKey")
    private Key key;

    /**
     * How long the state JWT generated for social providers remains valid
     */
    @Value("${security.socialProviderStateExpirationSeconds:900}")
    private int socialIdpStateExpirationSeconds;

    private Duration getSocialIdpStateExpiration() {
        return Duration.ofSeconds(socialIdpStateExpirationSeconds);
    }

    @RequestMapping(value = "/login")
    public ModelAndView login(HttpServletRequest request, @RequestParam(value = ORGANIZATION_PARAMETER_NAME, defaultValue = "DEFAULT") String organizationId) {
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
                    .toList();
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
                    var now = Instant.now();
                    var state = new JWT(Map.of(
                            Claims.NONCE, SecureRandomString.generate(),
                            Claims.IAT, now.getEpochSecond(),
                            Claims.EXP, now.plus(getSocialIdpStateExpiration()).getEpochSecond()));
                    socialAuthenticationProvider.asyncSignInUrl(buildRedirectUri(request, identityId), state, this::processState)
                            .map(Optional::ofNullable)
                            .blockingGet()
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

    private Single<String> processState(JWT jwt) {
        for (var claim : io.gravitee.am.common.jwt.Claims.requireEncryption())
            if (jwt.containsKey(claim)) {
                jwt.put(claim, CryptoUtils.encrypt((String) jwt.get(claim), key));
            }
        return Single.just(jwtBuilder.sign(jwt));
    }

    @RequestMapping(value = "/login/callback")
    public void loginCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Redirect to the original request.
        response.sendRedirect((String) request.getAttribute(DEFAULT_REDIRECT_COOKIE_NAME));
    }

    private String buildRedirectUri(HttpServletRequest request, String identity) {
        final var builder = RedirectUtils.preBuildLocationHeader(request);
        // append context path
        builder.path(request.getContextPath());
        builder.pathSegment("auth/login/callback");

        // append identity provider id
        builder.queryParam("provider", identity);

        return builder.build().toUriString();
    }

}
