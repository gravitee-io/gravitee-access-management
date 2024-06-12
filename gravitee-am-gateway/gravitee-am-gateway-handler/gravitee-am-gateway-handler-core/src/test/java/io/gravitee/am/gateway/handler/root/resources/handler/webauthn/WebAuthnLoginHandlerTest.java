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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.login.LoginFailureHandler;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.CredentialService;
import io.gravitee.am.service.FactorService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.ext.auth.webauthn.MetaDataService;
import io.vertx.rxjava3.ext.auth.webauthn.WebAuthn;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnLoginHandlerTest extends RxWebTestBase {

    private static String PASSWORDLESS_CHALLENGE_KEY = "KidEI7a_CXoW7dKjb8JEnL_WUb2xDPWBHtCUzTo4ouAyKNzc0SbGmC0t8mh0cKHdqo_4hYc3c2Iewp_QF746rA";
    private static String PASSWORDLESS_CHALLENGE_USERNAME_KEY = "username";

    @Mock
    private Domain domain;
    @Mock
    private FactorService factorService;
    @Mock
    private FactorManager factorManager;
    @Mock
    private CredentialService credentialService;
    @Mock
    private UserAuthenticationManager userAuthenticationManager;
    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;

    private WebAuthnLoginHandler webAuthnLoginHandler;

    @Mock
    private WebAuthn webAuthn;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // init webauthn objet
        MetaDataService metaDataService = mock(MetaDataService.class);
        when(metaDataService.verify(any())).thenReturn(new JsonObject());
        when(webAuthn.metaDataService()).thenReturn(metaDataService);
        when(webAuthn.rxAuthenticate(any(Credentials.class))).thenReturn(Single.just(io.vertx.rxjava3.ext.auth.User.fromName("username")));

        webAuthnLoginHandler =
                new WebAuthnLoginHandler(factorService, factorManager, domain, webAuthn, credentialService, userAuthenticationManager);

        router.route(HttpMethod.POST, "/webauthn/login")
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create())
                .failureHandler(new LoginFailureHandler(authenticationFlowContextService, domain, identityProviderManager));
    }

    @Test
    public void shouldNotLogin_v1_deviceIntegrityException() throws Exception {
        // init domain
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setEnforceAuthenticatorIntegrity(true);
        webAuthnSettings.setEnforceAuthenticatorIntegrityMaxAge(10);
        when(domain.getWebAuthnSettings()).thenReturn(webAuthnSettings);

        MetaDataService metaDataService = mock(MetaDataService.class);
        doThrow(RuntimeException.class).when(metaDataService).verify(any());
        when(webAuthn.metaDataService()).thenReturn(metaDataService);

        Credential credential = packedCredential();

        when(credentialService.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(credential));
        when(userAuthenticationManager.connectWithPasswordless(any(), any(), any())).thenReturn(Single.just(new User()));

        router.route(HttpMethod.POST, "/webauthn/login")
                .handler(rc -> {
                    Client client = new Client();
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, PASSWORDLESS_CHALLENGE_KEY);
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(webAuthnLoginHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                sendAssertion(),
                rep -> {
                    String location = rep.getHeader("Location");
                    Assert.assertTrue(location.contains("/webauthn/login?error=login_failed&error_code=account_device_integrity"));
                },
                302, "Found", null);
    }

    @Test
    public void shouldLogin_v1_nominalCase() throws Exception {
        // init domain
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        when(domain.getWebAuthnSettings()).thenReturn(webAuthnSettings);

        when(credentialService.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(new Credential()));
        when(credentialService.update(any(), any(), any(), any())).thenReturn(Single.just(new Credential()));
        when(userAuthenticationManager.connectWithPasswordless(any(), any(), any())).thenReturn(Single.just(new User()));

        router.route(HttpMethod.POST, "/webauthn/login")
                .handler(rc -> {
                    Client client = new Client();
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, PASSWORDLESS_CHALLENGE_KEY);
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(webAuthnLoginHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                sendAssertion(),
                null,
                200, "OK", null);
    }

    @Test
    public void shouldLogin_v1_deviceIntegrity_noMaxAge() throws Exception {
        // init domain
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setEnforceAuthenticatorIntegrity(true);
        webAuthnSettings.setEnforceAuthenticatorIntegrityMaxAge(null);
        when(domain.getWebAuthnSettings()).thenReturn(webAuthnSettings);

        Credential credential = packedCredential();

        when(credentialService.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(credential));
        when(credentialService.update(any(), any(), any(), any())).thenReturn(Single.just(new Credential()));
        when(userAuthenticationManager.connectWithPasswordless(any(), any(), any())).thenReturn(Single.just(new User()));

        router.route(HttpMethod.POST, "/webauthn/login")
                .handler(rc -> {
                    Client client = new Client();
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, PASSWORDLESS_CHALLENGE_KEY);
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(webAuthnLoginHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                sendAssertion(),
                null,
                200, "OK", null);
    }

    @Test
    public void shouldLogin_v1_deviceIntegrity() throws Exception {
        // init domain
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setEnforceAuthenticatorIntegrity(true);
        webAuthnSettings.setEnforceAuthenticatorIntegrityMaxAge(10);
        when(domain.getWebAuthnSettings()).thenReturn(webAuthnSettings);

        Credential credential = packedCredential();

        when(credentialService.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(credential));
        when(credentialService.update(any(), any(), any(), any())).thenReturn(Single.just(new Credential()));
        when(userAuthenticationManager.connectWithPasswordless(any(), any(), any())).thenReturn(Single.just(new User()));

        router.route(HttpMethod.POST, "/webauthn/login")
                .handler(rc -> {
                    Client client = new Client();
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, PASSWORDLESS_CHALLENGE_KEY);
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(webAuthnLoginHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                sendAssertion(),
                null,
                200, "OK", null);
    }

    @Test
    public void shouldLogin_v1_deviceIntegrity_attestationNone() throws Exception {
        // init domain
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setEnforceAuthenticatorIntegrity(true);
        webAuthnSettings.setEnforceAuthenticatorIntegrityMaxAge(10);
        when(domain.getWebAuthnSettings()).thenReturn(webAuthnSettings);

        Credential credential = noneCredential();

        when(credentialService.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(credential));
        when(credentialService.update(any(), any(), any(), any())).thenReturn(Single.just(new Credential()));
        when(userAuthenticationManager.connectWithPasswordless(any(), any(), any())).thenReturn(Single.just(new User()));

        router.route(HttpMethod.POST, "/webauthn/login")
                .handler(rc -> {
                    Client client = new Client();
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, PASSWORDLESS_CHALLENGE_KEY);
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(webAuthnLoginHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                sendAssertion(),
                null,
                200, "OK", null);
    }

    @Test
    public void shouldLogin_v1_deviceIntegrity_attestationPacked_emptyCertificateChain() throws Exception {
        // init domain
        WebAuthnSettings webAuthnSettings = new WebAuthnSettings();
        webAuthnSettings.setEnforceAuthenticatorIntegrity(true);
        webAuthnSettings.setEnforceAuthenticatorIntegrityMaxAge(10);
        when(domain.getWebAuthnSettings()).thenReturn(webAuthnSettings);

        Credential credential = packedCredential();
        credential.setAttestationStatement("{\"alg\":\"ES256\"}");

        when(credentialService.findByCredentialId(any(), any(), any())).thenReturn(Flowable.just(credential));
        when(credentialService.update(any(), any(), any(), any())).thenReturn(Single.just(new Credential()));
        when(userAuthenticationManager.connectWithPasswordless(any(), any(), any())).thenReturn(Single.just(new User()));

        router.route(HttpMethod.POST, "/webauthn/login")
                .handler(rc -> {
                    Client client = new Client();
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY, PASSWORDLESS_CHALLENGE_KEY);
                    rc.session().put(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY, PASSWORDLESS_CHALLENGE_USERNAME_KEY);
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.next();
                })
                .handler(webAuthnLoginHandler)
                .handler(rc -> rc.end());

        testRequest(
                HttpMethod.POST,
                "/webauthn/login",
                sendAssertion(),
                null,
                200, "OK", null);
    }

    private Consumer<HttpClientRequest> sendAssertion() {
        return req -> {
            req.headers().set("content-type", "application/x-www-form-urlencoded");
            req.setChunked(true);
            req.write(Buffer.buffer("assertion=%7B%22id%22%3A%22QAn8i7wToriAx38jzUrVw04Cxao_y285vX2CGwyFtO8%22%2C%22rawId%22%3A%22QAn8i7wToriAx38jzUrVw04Cxao_y285vX2CGwyFtO8%22%2C%22response%22%3A%7B%22clientDataJSON%22%3A%22eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiS2lkRUk3YV9DWG9XN2RLamI4SkVuTF9XVWIyeERQV0JIdENVelRvNG91QXlLTnpjMFNiR21DMHQ4bWgwY0tIZHFvXzRoWWMzYzJJZXdwX1FGNzQ2ckEiLCJvcmlnaW4iOiJodHRwOi8vbG9jYWxob3N0OjgwOTIiLCJjcm9zc09yaWdpbiI6ZmFsc2V9%22%2C%22authenticatorData%22%3A%22SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MBAAAAAg%22%2C%22signature%22%3A%22MEUCIBfUoc-FHIliWp_6bw2c74Bnx6d62eDce0klo2O79ksjAiEA5c0JmD5o9CGacnx3bjOausUFRobNhYlJatPqRZwObn0%22%2C%22userHandle%22%3A%22%22%7D%2C%22type%22%3A%22public-key%22%7D"));
        };
    }

    private Credential packedCredential() {
        Credential credential = new Credential();
        credential.setAaguid("01020304-0506-0708-0102-030405060708");
        credential.setCredentialId("QAn8i7wToriAx38jzUrVw04Cxao_y285vX2CGwyFtO8");
        credential.setPublicKey("pQECAyYgASFYILO4CFUFksL1no2vpWhmHMplz47TI9X8Wbt4UK936appIlggRrlbIdCLVdwBVkgdmYLVIJ0p2Zonb6fmha5vb-oXnro");
        credential.setAttestationStatementFormat("packed");
        credential.setAttestationStatement("{\"alg\":\"ES256\",\"x5c\":[\"MIIB2TCCAX2gAwIBAgIBATANBgkqhkiG9w0BAQsFADBgMQswCQYDVQQGEwJVUzERMA8GA1UECgwIQ2hyb21pdW0xIjAgBgNVBAsMGUF1dGhlbnRpY2F0b3IgQXR0ZXN0YXRpb24xGjAYBgNVBAMMEUJhdGNoIENlcnRpZmljYXRlMB4XDTE3MDcxNDAyNDAwMFoXDTQzMDIwNTE2MjYxNlowYDELMAkGA1UEBhMCVVMxETAPBgNVBAoMCENocm9taXVtMSIwIAYDVQQLDBlBdXRoZW50aWNhdG9yIEF0dGVzdGF0aW9uMRowGAYDVQQDDBFCYXRjaCBDZXJ0aWZpY2F0ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABI1hfmXJUI5kvMVnOsgqZ5naPBRGaCwljEY__99Y39L6Pmw3i1PXlcSk3_tBme3Xhi8jq68CA7S4kRugVpmU4QGjJTAjMAwGA1UdEwEB_wQCMAAwEwYLKwYBBAGC5RwCAQEEBAMCBSAwDQYJKoZIhvcNAQELBQADRwAwRAIgFi1LpPjfizV_IfXlbnY0gbzEzUgwr4QPTlFmruc8IooCICcHVpqabrs3fXrMwFa0fTISu7VpivzT2Mgl-gTTIxXb\"]}");
        credential.setCounter(0l);
        credential.setLastCheckedAt(new Date(Instant.now().minusSeconds(60).toEpochMilli()));
        return credential;
    }

    private Credential noneCredential() {
        Credential credential = new Credential();
        credential.setAaguid("00000000-0000-0000-0000-000000000000");
        credential.setCredentialId("PK-1RWP7jWwMRr5pp17_ZcUG_Bhw7-ClrrGvqvI0gnM");
        credential.setPublicKey("pQECAyYgASFYIP8eCDia3Raw23DYj_iBkZt1Mp1GF7PP2Oy9FaKvOfryIlggItxrdVVhl2m9LqUg2Pn0Ml1tYhXcyIf39dAoPjJfCmY");
        credential.setAttestationStatementFormat("none");
        credential.setAttestationStatement("{}");
        credential.setCounter(0l);
        credential.setLastCheckedAt(new Date(Instant.now().minusSeconds(60).toEpochMilli()));
        return credential;
    }
}
