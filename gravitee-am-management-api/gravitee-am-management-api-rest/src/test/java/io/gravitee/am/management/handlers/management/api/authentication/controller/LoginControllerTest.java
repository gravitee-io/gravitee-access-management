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

import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.management.handlers.management.api.authentication.manager.idp.IdentityProviderManager;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Organization;
import io.gravitee.am.service.OrganizationService;
import io.gravitee.am.service.ReCaptchaService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class LoginControllerTest {

    private static final String ORGANIZATION_ID = "DEFAULT";
    private static final String HEALTHY_IDP = "healthy-idp";
    private static final String BROKEN_IDP = "broken-idp";

    @Mock
    private OrganizationService organizationService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private ReCaptchaService reCaptchaService;

    @InjectMocks
    private LoginController loginController;

    private MockHttpServletRequest request;

    @BeforeEach
    public void setUp() {
        request = new MockHttpServletRequest();
        request.setServerName("localhost");
    }

    @Test
    public void shouldOmitProviderWhenItCannotBuildASignInUrl() {
        givenOrganizationWith(BROKEN_IDP);
        givenProvider(BROKEN_IDP, Maybe.empty());

        var model = loginController.login(request, ORGANIZATION_ID).getModel();

        assertTrue(socialProviders(model).isEmpty());
        assertTrue(authorizeUrls(model).isEmpty());
    }

    @Test
    public void shouldOmitProviderWhenSignInUrlBuildingFails() {
        givenOrganizationWith(BROKEN_IDP);
        givenProvider(BROKEN_IDP, Maybe.error(new IllegalStateException("discovery failed")));

        var model = loginController.login(request, ORGANIZATION_ID).getModel();

        assertTrue(socialProviders(model).isEmpty());
        assertTrue(authorizeUrls(model).isEmpty());
    }

    @Test
    public void shouldKeepHealthyProviderWhenAnotherOneIsBroken() {
        givenOrganizationWith(HEALTHY_IDP, BROKEN_IDP);
        givenProvider(HEALTHY_IDP, Maybe.just(Request.get("https://idp.example.com/authorize")));
        givenProvider(BROKEN_IDP, Maybe.empty());

        var model = loginController.login(request, ORGANIZATION_ID).getModel();

        assertEquals(Set.of(HEALTHY_IDP), socialProviders(model).stream().map(IdentityProvider::getId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Map.of(HEALTHY_IDP, "https://idp.example.com/authorize"), authorizeUrls(model));
    }

    private void givenOrganizationWith(String... identityProviderIds) {
        var organization = new Organization();
        organization.setId(ORGANIZATION_ID);
        organization.setIdentities(List.of(identityProviderIds));
        when(organizationService.findById(ORGANIZATION_ID)).thenReturn(Single.just(organization));

        for (String identityProviderId : identityProviderIds) {
            var identityProvider = new IdentityProvider();
            identityProvider.setId(identityProviderId);
            identityProvider.setName(identityProviderId);
            identityProvider.setType("oauth2-generic-am-idp");
            identityProvider.setExternal(true);
            when(identityProviderManager.getIdentityProvider(identityProviderId)).thenReturn(identityProvider);
        }
    }

    private void givenProvider(String identityProviderId, Maybe<Request> signInUrl) {
        var authenticationProvider = org.mockito.Mockito.mock(SocialAuthenticationProvider.class);
        when(authenticationProvider.asyncSignInUrl(any(), any(), any())).thenReturn(signInUrl);
        when(identityProviderManager.get(identityProviderId)).thenReturn(authenticationProvider);
    }

    @SuppressWarnings("unchecked")
    private Set<IdentityProvider> socialProviders(Map<String, Object> model) {
        return (Set<IdentityProvider>) model.get("socialProviders");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> authorizeUrls(Map<String, Object> model) {
        return (Map<String, String>) model.get("authorizeUrls");
    }
}
