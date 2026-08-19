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

import static io.gravitee.am.common.utils.ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginAuthenticationHandler.SOCIAL_PROVIDER_MAP_CONTEXT_KEY;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

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
                    rc.put(SOCIAL_PROVIDER_CONTEXT_KEY, socialProviders);
                    rc.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, urls);
                    rc.put(SOCIAL_PROVIDER_MAP_CONTEXT_KEY, socialProviders.stream().collect(toMap(IdentityProvider::getId, identity())));
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

    /**
     * The handler takes the first provider whose rule matches, and the client holds them ordered by
     * priority. So when two tenants' rules both match a user, priority alone decides which tenant
     * they reach — and getting that wrong looks like a successful login, not an error.
     */
    @Test
    public void shouldRedirectToTheHighestPriorityIdP_whenSeveralSelectionRulesMatch() throws Exception {
        // both providers whitelist the same domain, so both rules match the submitted username
        var lowerPriority = applicationIdp("idp-priority-5", 5, WHITELISTED_DOMAIN_RULE);
        var higherPriority = applicationIdp("idp-priority-1", 1, WHITELISTED_DOMAIN_RULE);

        var client = new Client();
        SortedSet<ApplicationIdentityProvider> applicationIdps = new TreeSet<>();
        // added lower priority first, so a pass here cannot come from insertion order
        applicationIdps.add(lowerPriority);
        applicationIdps.add(higherPriority);
        client.setIdentityProviders(applicationIdps);

        seedRoutingContext(client,
                List.of(socialProvider("idp-priority-5", "mail.com"), socialProvider("idp-priority-1", "mail.com")),
                Map.of("idp-priority-5", "https://tenant-five.example.com/authorize",
                        "idp-priority-1", "https://tenant-one.example.com/authorize"));

        testRequest(
                HttpMethod.POST,
                "/login/identifier?username=john.doe@mail.com",
                null,
                resp -> assertEquals("https://tenant-one.example.com/authorize", resp.headers().get("location")),
                302, "Found", null);
    }

    /**
     * A user no rule matches must be left to the normal login flow rather than pushed at whichever
     * provider happens to be configured first.
     */
    @Test
    public void shouldNotRedirect_whenNoSelectionRuleMatches() throws Exception {
        var client = new Client();
        SortedSet<ApplicationIdentityProvider> applicationIdps = new TreeSet<>();
        applicationIdps.add(applicationIdp("idp-1", 1, WHITELISTED_DOMAIN_RULE));
        client.setIdentityProviders(applicationIdps);

        // the provider only accepts another-mail.com, the user is on mail.com
        seedRoutingContext(client,
                List.of(socialProvider("idp-1", "another-mail.com")),
                Map.of("idp-1", "https://another-mail.example.com/authorize"));

        // reached only if the handler calls next() instead of redirecting
        router.route(HttpMethod.POST, "/login/identifier")
                .handler(rc -> rc.response().setStatusCode(200).end());

        testRequest(
                HttpMethod.POST,
                "/login/identifier?username=john.doe@mail.com",
                null,
                resp -> assertNull("must not redirect a user no rule matched", resp.headers().get("location")),
                200, "OK", null);
    }

    /**
     * The username is handed to the external provider so the user is not asked for it twice. It has
     * to survive encoding to do that — the handler carries a note that Azure AD turns a '+' into a
     * space, so a username containing one is the case worth pinning down.
     */
    @Test
    public void shouldPassUsernameAsEncodedLoginHint_onIdentifierFirstLogin() throws Exception {
        seedIdentifierFirstRoute();

        testRequest(
                HttpMethod.POST,
                "/login/identifier-first?username=john%2Btest@mail.com",
                null,
                resp -> assertEquals(
                        "https://tenant-one.example.com/authorize?login_hint=john%2Btest%40mail.com",
                        resp.headers().get("location")),
                302, "Found", null);
    }

    @Test
    public void shouldPassRememberMeHint_onIdentifierFirstLogin() throws Exception {
        seedIdentifierFirstRoute();

        testRequest(
                HttpMethod.POST,
                "/login/identifier-first?username=john.doe@mail.com&rememberMe=on",
                null,
                resp -> assertEquals(
                        "https://tenant-one.example.com/authorize?login_hint=john.doe%40mail.com&remember_me_hint=on",
                        resp.headers().get("location")),
                302, "Found", null);
    }

    /** Matches a username whose domain is the one the identity provider whitelists. */
    private static final String WHITELISTED_DOMAIN_RULE =
            "{#request.params['username'][0] matches '.+@' + #identityProvider.domainWhitelist[0] + '$'}";

    /**
     * Mounts the handler in its identifier-first mode on a separate path, behind a single matching
     * provider, so the login hint assertions have one unambiguous redirect target.
     */
    private void seedIdentifierFirstRoute() {
        var client = new Client();
        SortedSet<ApplicationIdentityProvider> applicationIdps = new TreeSet<>();
        applicationIdps.add(applicationIdp("idp-1", 1, WHITELISTED_DOMAIN_RULE));
        client.setIdentityProviders(applicationIdps);

        seedRoutingContext(client,
                List.of(socialProvider("idp-1", "mail.com")),
                Map.of("idp-1", "https://tenant-one.example.com/authorize"));

        router.route(HttpMethod.POST, "/login/identifier-first")
                .handler(new LoginSelectionRuleHandler(true));
    }

    /** Seeds what LoginAuthenticationHandler would have put in the context before this handler runs. */
    private void seedRoutingContext(Client client, List<IdentityProvider> socialProviders, Map<String, String> authorizeUrls) {
        router.route()
                .order(-1)
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.put(SOCIAL_PROVIDER_CONTEXT_KEY, socialProviders);
                    rc.put(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY, authorizeUrls);
                    rc.put(SOCIAL_PROVIDER_MAP_CONTEXT_KEY,
                            socialProviders.stream().collect(toMap(IdentityProvider::getId, identity())));
                    rc.next();
                });
    }

    private static ApplicationIdentityProvider applicationIdp(String identity, int priority, String selectionRule) {
        var appIdp = new ApplicationIdentityProvider();
        appIdp.setIdentity(identity);
        appIdp.setPriority(priority);
        appIdp.setSelectionRule(selectionRule);
        return appIdp;
    }

    private static IdentityProvider socialProvider(String id, String whitelistedDomain) {
        var identityProvider = new IdentityProvider();
        identityProvider.setId(id);
        identityProvider.setDomainWhitelist(List.of(whitelistedDomain));
        return identityProvider;
    }
}
