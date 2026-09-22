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
package io.gravitee.am.identityprovider.oauth2.authentication;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.DummyAuthenticationContext;
import io.gravitee.am.identityprovider.api.DummyRequest;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.trustedissuer.OAuthTrustedIssuer;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;
import io.gravitee.am.common.web.URLParametersUtils;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.authentication.spring.OAuth2GenericAuthenticationProviderConfiguration;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {OAuth2GenericAuthenticationProviderTestConfiguration.class, OAuth2GenericAuthenticationProviderConfiguration.class}, loader = AnnotationConfigContextLoader.class)
public class OAuth2GenericAuthenticationProviderTest {

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Autowired
    private OAuth2GenericIdentityProviderConfiguration configuration;

    private JWTProcessor jwtProcessor = mock(JWTProcessor.class);

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Before
    public void setUp() {
        String baseUrl = "http://localhost:" + wireMockRule.port();
        configuration.setAccessTokenUri(baseUrl + "/oauth/token");
        configuration.setUserAuthorizationUri(baseUrl + "/oauth/authorize");
        configuration.setUserProfileUri(baseUrl + "/profile");
    }

    @Test
    public void shouldLoadUserByUsername_authentication() {
        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLParametersUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"access_token\" : \"test_token\" }")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test_token"))
                .willReturn(okJson("{ \"sub\": \"bob\" }")));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "__social__";
            }

            @Override
            public Object getPrincipal() {
                return "__social__";
            }

            @Override
            public AuthenticationContext getContext() {
                DummyRequest dummyRequest = new DummyRequest();
                dummyRequest.setParameters(Collections.singletonMap("code", Arrays.asList("test-code")));
                final HashMap<String, Object> attributes = new HashMap<>();
                attributes.put("redirect_uri", "http://redirect_uri");
                return new DummyAuthenticationContext(attributes, dummyRequest);
            }
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    public void shouldStopRetryingWhenProviderStopped() throws Exception {
        OAuth2GenericAuthenticationProvider provider = (OAuth2GenericAuthenticationProvider) authenticationProvider;
        configuration.setWellKnownUri("http://localhost:" + wireMockRule.port() + "/.well-known/openid-configuration");
        stubFor(any(urlPathEqualTo("/.well-known/openid-configuration"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        provider.afterPropertiesSet();
        Thread.sleep(600);
        provider.stop();

        // stop() cancels the retry chain; under CI load the initial request can still be draining, so
        // settle first, then assert the request count stops growing (a broken stop() would keep retrying).
        Thread.sleep(2000);
        int afterSettle = wireMockRule.findAll(getRequestedFor(urlPathEqualTo("/.well-known/openid-configuration"))).size();
        Thread.sleep(2000);
        int later = wireMockRule.findAll(getRequestedFor(urlPathEqualTo("/.well-known/openid-configuration"))).size();

        assertEquals("provider must stop retrying after stop()", afterSettle, later);
    }


    @Test
    public void shouldLoadUserByUsername_authentication_jwt() throws Exception {
        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLParametersUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"access_token\" : \"test_token\" }")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test_token"))
                .willReturn(okForContentType("application/JWT", "a.jwt.value")));

        when(jwtProcessor.process(ArgumentMatchers.any(String.class), eq(null))).thenReturn(new JWTClaimsSet.Builder().subject("bob").build());

        ((OAuth2GenericAuthenticationProvider) authenticationProvider).setJwtProcessor(jwtProcessor);

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "__social__";
            }

            @Override
            public Object getPrincipal() {
                return "__social__";
            }

            @Override
            public AuthenticationContext getContext() {
                DummyRequest dummyRequest = new DummyRequest();
                dummyRequest.setParameters(Collections.singletonMap("code", Arrays.asList("test-code")));
                final HashMap<String, Object> attributes = new HashMap<>();
                attributes.put("redirect_uri", "http://redirect_uri");
                return new DummyAuthenticationContext(attributes, dummyRequest);
            }
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
        verify(jwtProcessor).process(anyString(), eq(null));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials() {
        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLParametersUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(unauthorized()));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "__social__";
            }

            @Override
            public Object getPrincipal() {
                return "__social__";
            }

            @Override
            public AuthenticationContext getContext() {
                DummyRequest dummyRequest = new DummyRequest();
                dummyRequest.setParameters(Collections.singletonMap("code", Arrays.asList("wrong-code")));
                final HashMap<String, Object> attributes = new HashMap<>();
                attributes.put("redirect_uri", "http://redirect_uri");
                return new DummyAuthenticationContext(attributes, dummyRequest);
            }
        }).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound() {
        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLParametersUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"access_token\" : \"test_token\" }")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test_token"))
                .willReturn(notFound()));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "__social__";
            }

            @Override
            public Object getPrincipal() {
                return "__social__";
            }

            @Override
            public AuthenticationContext getContext() {
                DummyRequest dummyRequest = new DummyRequest();
                dummyRequest.setParameters(Collections.singletonMap("code", Arrays.asList("test-code")));
                final HashMap<String, Object> attributes = new HashMap<>();
                attributes.put("redirect_uri", "http://redirect_uri");
                return new DummyAuthenticationContext(attributes, dummyRequest);
            }
        }).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_roleMapping() {
        // configure role mapping
        Map<String, String[]> roles = new HashMap<>();
        roles.put("admin", new String[]{"preferred_username=bob"});
        roleMapper.setRoles(roles);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLParametersUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"access_token\" : \"test_token\" }")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer test_token"))
                .willReturn(okJson("{ \"sub\": \"bob\", \"preferred_username\": \"bob\" }")));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "__social__";
            }

            @Override
            public Object getPrincipal() {
                return "__social__";
            }

            @Override
            public AuthenticationContext getContext() {
                DummyRequest dummyRequest = new DummyRequest();
                dummyRequest.setParameters(Collections.singletonMap("code", Arrays.asList("test-code")));
                final HashMap<String, Object> attributes = new HashMap<>();
                attributes.put("redirect_uri", "http://redirect_uri");
                return new DummyAuthenticationContext(attributes, dummyRequest);
            }
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
        testObserver.assertValue(u -> u.getRoles().contains("admin"));
    }

    @Test
    public void shouldNotBeATrustedIssuerWithoutWellKnownUri() {
        OAuth2GenericAuthenticationProvider provider = (OAuth2GenericAuthenticationProvider) authenticationProvider;

        assertTrue(provider.trustedIssuer().isEmpty());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    public void shouldKeepTheDiscoveredIssuerAndBuildTheVerifierOnce() throws Exception {
        OAuth2GenericAuthenticationProvider provider = (OAuth2GenericAuthenticationProvider) authenticationProvider;
        RSAKey signingKey = new RSAKeyGenerator(2048).keyID("rsa-1").generate();
        stubDiscovery(2000);
        stubFor(any(urlPathEqualTo("/jwks")).willReturn(okJson(new JWKSet(signingKey.toPublicJWK()).toString())));

        provider.afterPropertiesSet();

        assertTrue(provider.trustedIssuer().isEmpty());
        await().atMost(10, TimeUnit.SECONDS).until(() -> provider.trustedIssuer().isPresent());
        OAuthTrustedIssuer trustedIssuer = provider.trustedIssuer().orElseThrow();
        assertEquals(issuer(), trustedIssuer.issuer());
        assertSame(trustedIssuer, provider.trustedIssuer().orElseThrow());
        trustedIssuer.verifier().verify(idJag(JWSAlgorithm.RS256, "rsa-1", new RSASSASigner(signingKey))).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(claims -> "alice".equals(claims.getSubject()));
        provider.stop();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    public void shouldStayUntrustedWhenDiscoveryFails() throws Exception {
        OAuth2GenericAuthenticationProvider provider = (OAuth2GenericAuthenticationProvider) authenticationProvider;
        configuration.setWellKnownUri(baseUrl() + "/.well-known/openid-configuration");
        stubFor(any(urlPathEqualTo("/.well-known/openid-configuration")).willReturn(serverError()));

        provider.afterPropertiesSet();

        await().during(1, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS)
                .until(() -> provider.trustedIssuer().isEmpty());
        provider.stop();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    public void shouldStayUntrustedWithoutRaisingWhenTheDiscoveredJwksUriIsUnusable() throws Exception {
        OAuth2GenericAuthenticationProvider provider = (OAuth2GenericAuthenticationProvider) authenticationProvider;
        List<Throwable> unhandled = new CopyOnWriteArrayList<>();
        RxJavaPlugins.setErrorHandler(unhandled::add);
        stubDiscovery(0, "not a jwks uri");

        try {
            provider.afterPropertiesSet();

            await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                    .until(() -> provider.trustedIssuer().isEmpty() && unhandled.isEmpty());
        } finally {
            RxJavaPlugins.reset();
            provider.stop();
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    public void shouldVerifyEs256AssertionFromAProviderConfiguredForRs256IdTokens() throws Exception {
        OAuth2GenericAuthenticationProvider provider = (OAuth2GenericAuthenticationProvider) authenticationProvider;
        RSAKey idTokenKey = new RSAKeyGenerator(2048).keyID("rsa-1").generate();
        ECKey assertionKey = new ECKeyGenerator(Curve.P_256).keyID("ec-1").generate();
        configuration.setPublicKeyResolver(KeyResolver.JWKS_URL);
        configuration.setUseIdTokenForUserInfo(true);
        stubDiscovery(0);
        stubFor(any(urlPathEqualTo("/jwks"))
                .willReturn(okJson(new JWKSet(List.of(idTokenKey.toPublicJWK(), assertionKey.toPublicJWK())).toString())));
        stubFor(any(urlPathEqualTo("/oauth/token"))
                .willReturn(okJson("{\"access_token\" : \"test_token\", \"id_token\" : \"" + idToken(idTokenKey) + "\" }")));

        provider.afterPropertiesSet();
        await().atMost(10, TimeUnit.SECONDS).until(() -> provider.trustedIssuer().isPresent());

        TestObserver<User> login = authenticationProvider.loadUserByUsername(codeAuthentication()).test();
        login.awaitDone(10, TimeUnit.SECONDS);
        login.assertNoErrors();
        login.assertValue(u -> "bob".equals(u.getUsername()));
        provider.trustedIssuer().orElseThrow().verifier().verify(idJag(JWSAlgorithm.ES256, "ec-1", new ECDSASigner(assertionKey))).test()
                .awaitDone(10, TimeUnit.SECONDS)
                .assertValue(claims -> "alice".equals(claims.getSubject()));
        provider.stop();
    }

    private String baseUrl() {
        return "http://localhost:" + wireMockRule.port();
    }

    private String issuer() {
        return baseUrl() + "/oidc";
    }

    private void stubDiscovery(int delayMillis) {
        stubDiscovery(delayMillis, baseUrl() + "/jwks");
    }

    private void stubDiscovery(int delayMillis, String jwksUri) {
        configuration.setWellKnownUri(baseUrl() + "/.well-known/openid-configuration");
        stubFor(any(urlPathEqualTo("/.well-known/openid-configuration"))
                .willReturn(okJson("{"
                        + "\"issuer\": \"" + issuer() + "\","
                        + "\"authorization_endpoint\": \"" + baseUrl() + "/oauth/authorize\","
                        + "\"token_endpoint\": \"" + baseUrl() + "/oauth/token\","
                        + "\"userinfo_endpoint\": \"" + baseUrl() + "/profile\","
                        + "\"jwks_uri\": \"" + jwksUri + "\","
                        + "\"id_token_signing_alg_values_supported\": [\"RS256\"]"
                        + "}").withFixedDelay(delayMillis)));
    }

    private String idJag(JWSAlgorithm algorithm, String kid, JWSSigner signer) throws Exception {
        JWSHeader header = new JWSHeader.Builder(algorithm)
                .type(new JOSEObjectType("oauth-id-jag+jwt"))
                .keyID(kid)
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer())
                .subject("alice")
                .audience("https://rs.example.com")
                .claim("client_id", "client-1")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                .issueTime(new Date())
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private String idToken(RSAKey signingKey) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer())
                .subject("bob")
                .audience("test-client-id")
                .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static Authentication codeAuthentication() {
        DummyRequest dummyRequest = new DummyRequest();
        dummyRequest.setParameters(Collections.singletonMap("code", Arrays.asList("test-code")));
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("redirect_uri", "http://redirect_uri");
        AuthenticationContext context = new DummyAuthenticationContext(attributes, dummyRequest);
        return new Authentication() {
            @Override
            public Object getCredentials() {
                return "__social__";
            }

            @Override
            public Object getPrincipal() {
                return "__social__";
            }

            @Override
            public AuthenticationContext getContext() {
                return context;
            }
        };
    }

}
