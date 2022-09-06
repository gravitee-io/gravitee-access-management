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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginSelectionRuleHandlerTest extends RxWebTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.POST, "/login/identifier")
                .handler(new LoginSelectionRuleHandler(false));
    }

    @Test
    public void shouldRedirectToExternalIdP_selectionRule_domainWhiteList() throws Exception {
        router.route()
                .order(-1)
                .handler(rc -> {
                    ApplicationIdentityProvider applicationIdentityProvider = new ApplicationIdentityProvider();
                    applicationIdentityProvider.setIdentity("idp-1");
                    applicationIdentityProvider.setSelectionRule("{#request.params['username'][0] matches '.+@' + #identityProvider.domainWhitelist[0] + '$'}");

                    ApplicationIdentityProvider applicationIdentityProvider2 = new ApplicationIdentityProvider();
                    applicationIdentityProvider2.setIdentity("idp-2");
                    applicationIdentityProvider2.setSelectionRule("{#request.params['username'][0] matches '.+@' + #identityProvider.domainWhitelist[0] + '$'}");

                    var client = new Client();
                    SortedSet<ApplicationIdentityProvider> sortedSet = new TreeSet<>();
                    sortedSet.add(applicationIdentityProvider);
                    sortedSet.add(applicationIdentityProvider2);
                    client.setIdentityProviders(sortedSet);

                    IdentityProvider identityProvider = new IdentityProvider();
                    identityProvider.setId("idp-1");
                    identityProvider.setDomainWhitelist(Collections.singletonList("mail.com"));
                    IdentityProvider identityProvider2 = new IdentityProvider();
                    identityProvider2.setId("idp-2");
                    identityProvider2.setDomainWhitelist(Collections.singletonList("another-mail.com"));
                    List<IdentityProvider> socialProviders = Arrays.asList(identityProvider, identityProvider2);

                    Map<String, String> urls = Map.of("idp-1", "https://mail.com", "idp-2", "https://another-mail.com");

                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
//                    rc.put(SOCIAL_PROVIDER_CONTEXT_KEY, socialProviders);
                    rc.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, urls);
                    rc.next();
                });

        testRequest(
                HttpMethod.POST,
                "/login/identifier?username=john.doe@mail.com",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://mail.com"));
                },
                302, "Found", null);
    }
}
