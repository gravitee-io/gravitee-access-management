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
package io.gravitee.am.gateway.handler.root.resources.endpoint.rememberdevice;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicReference;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEFAULT_REMEMBER_DEVICE_COOKIE_NAME;
import static io.gravitee.am.common.utils.ConstantKeys.ETAG_DEVICE_ID;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.rememberdevice.RememberDeviceEtagEndpoint.ETAG_ISSUED_CLAIM;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.rememberdevice.RememberDeviceEtagEndpoint.parseEtag;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RememberDeviceEtagEndpointTest extends RxWebTestBase {

    private static final String PATH = "/remember-device/etag.js";

    @Mock
    private DeviceIdentifierManager deviceIdentifierManager;
    @Mock
    private JWTService jwtService;

    private final Client client = new Client();
    private final AtomicReference<Session> session = new AtomicReference<>();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        client.setClientId("client-id");
        var rememberDevice = new RememberDeviceSettings();
        rememberDevice.setActive(true);
        rememberDevice.setDeviceIdentifierId("device-identifier-id");
        rememberDevice.setExpirationTimeSeconds(3600L);
        var mfaSettings = new MFASettings();
        mfaSettings.setRememberDevice(rememberDevice);
        client.setMfaSettings(mfaSettings);

        router.route(HttpMethod.GET, PATH)
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(rc -> {
                    rc.put(CLIENT_CONTEXT_KEY, client);
                    session.set(rc.session());
                    rc.next();
                })
                .handler(new RememberDeviceEtagEndpoint(deviceIdentifierManager, jwtService, DEFAULT_REMEMBER_DEVICE_COOKIE_NAME));
    }

    @Test
    public void shouldReturnNoContentWhenEtagDisabled() throws Exception {
        testRequest(HttpMethod.GET, PATH, null, resp -> {
            Assertions.assertThat(resp.getHeader(HttpHeaders.ETAG)).isNull();
            Assertions.assertThat(resp.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        }, 204, "No Content", null);
    }

    @Test
    public void shouldReturnNoContentWhenRememberDeviceInactive() throws Exception {
        client.getMfaSettings().getRememberDevice().setActive(false);

        testRequest(HttpMethod.GET, PATH, null, resp -> Assertions.assertThat(resp.getHeader(HttpHeaders.ETAG)).isNull(), 204, "No Content", null);
        verify(deviceIdentifierManager, never()).useEtagBasedDeviceIdentifier(any());
    }

    @Test
    public void shouldIssueEtagOnFirstContact() throws Exception {
        givenEtagEnabled();
        var captor = ArgumentCaptor.forClass(JWT.class);
        when(jwtService.encode(captor.capture(), eq(client))).thenReturn(Single.just("issued-jwt"));

        testRequest(HttpMethod.GET, PATH, null, resp -> {
            Assertions.assertThat(resp.getHeader(HttpHeaders.ETAG)).isEqualTo("\"issued-jwt\"");
            Assertions.assertThat(resp.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-cache");
            Assertions.assertThat(resp.cookies()).noneMatch(c -> c.startsWith(DEFAULT_REMEMBER_DEVICE_COOKIE_NAME));
        }, 200, "OK", RememberDeviceEtagEndpoint.BODY);

        var issued = captor.getValue();
        Assertions.assertThat(issued.get(ETAG_ISSUED_CLAIM)).isEqualTo(true);
        Assertions.assertThat(issued.getExp() - issued.getIat()).isEqualTo(3600L);
        Assertions.assertThat(session.get().<String>get(ETAG_DEVICE_ID)).isEqualTo(issued.getJti());
    }

    @Test
    public void shouldIssueNewEtagWhenEtagInvalid() throws Exception {
        givenEtagEnabled();
        when(jwtService.decodeAndVerify(eq("forged"), eq(client), eq(JWTService.TokenType.SESSION))).thenReturn(Single.error(new IllegalArgumentException("bad signature")));
        when(jwtService.encode(any(JWT.class), eq(client))).thenReturn(Single.just("issued-jwt"));

        testRequest(HttpMethod.GET, PATH, req -> req.putHeader(HttpHeaders.IF_NONE_MATCH, "\"forged\""),
                resp -> Assertions.assertThat(resp.getHeader(HttpHeaders.ETAG)).isEqualTo("\"issued-jwt\""),
                200, "OK", null);
    }

    @Test
    public void shouldReturnNotModifiedForEtagIssuedByEndpoint() throws Exception {
        givenEtagEnabled();
        givenValidToken("etag-jwt", "etag-device-id", true);

        testRequest(HttpMethod.GET, PATH, req -> req.putHeader(HttpHeaders.IF_NONE_MATCH, "\"etag-jwt\""), resp -> {
            Assertions.assertThat(resp.getHeader(HttpHeaders.ETAG)).isEqualTo("\"etag-jwt\"");
            Assertions.assertThat(resp.cookies()).noneMatch(c -> c.startsWith(DEFAULT_REMEMBER_DEVICE_COOKIE_NAME));
        }, 304, "Not Modified", null);

        Assertions.assertThat(session.get().<String>get(ETAG_DEVICE_ID)).isEqualTo("etag-device-id");
    }

    @Test
    public void shouldRestoreCookieFromRememberedDeviceEtag() throws Exception {
        givenEtagEnabled();
        givenValidToken("remembered-jwt", "remembered-device-id", false);

        testRequest(HttpMethod.GET, PATH, req -> req.putHeader(HttpHeaders.IF_NONE_MATCH, "\"remembered-jwt\""), resp -> {
            Assertions.assertThat(resp.getHeader(HttpHeaders.ETAG)).isEqualTo("\"remembered-jwt\"");
            Assertions.assertThat(resp.cookies()).anyMatch(c -> c.startsWith(DEFAULT_REMEMBER_DEVICE_COOKIE_NAME + "=remembered-jwt"));
        }, 304, "Not Modified", null);

        Assertions.assertThat(session.get().<String>get(ETAG_DEVICE_ID)).isEqualTo("remembered-device-id");
    }

    @Test
    public void shouldPrimeEtagFromCookie() throws Exception {
        givenEtagEnabled();
        givenValidToken("cookie-jwt", "cookie-device-id", false);

        testRequest(HttpMethod.GET, PATH, req -> req.putHeader(HttpHeaders.COOKIE, DEFAULT_REMEMBER_DEVICE_COOKIE_NAME + "=cookie-jwt"),
                resp -> Assertions.assertThat(resp.getHeader(HttpHeaders.ETAG)).isEqualTo("\"cookie-jwt\""),
                200, "OK", null);

        Assertions.assertThat(session.get().<String>get(ETAG_DEVICE_ID)).isEqualTo("cookie-device-id");
    }

    @Test
    public void shouldReprimeEtagWhenCookieDiffers() throws Exception {
        givenEtagEnabled();
        givenValidToken("cookie-jwt", "device-id", false);
        givenValidToken("etag-jwt", "device-id", true);

        testRequest(HttpMethod.GET, PATH, req -> {
                    req.putHeader(HttpHeaders.COOKIE, DEFAULT_REMEMBER_DEVICE_COOKIE_NAME + "=cookie-jwt");
                    req.putHeader(HttpHeaders.IF_NONE_MATCH, "\"etag-jwt\"");
                },
                resp -> Assertions.assertThat(resp.getHeader(HttpHeaders.ETAG)).isEqualTo("\"cookie-jwt\""),
                200, "OK", null);
    }

    @Test
    public void shouldReturnNotModifiedWhenEtagMatchesCookie() throws Exception {
        givenEtagEnabled();
        givenValidToken("cookie-jwt", "device-id", false);

        testRequest(HttpMethod.GET, PATH, req -> {
                    req.putHeader(HttpHeaders.COOKIE, DEFAULT_REMEMBER_DEVICE_COOKIE_NAME + "=cookie-jwt");
                    req.putHeader(HttpHeaders.IF_NONE_MATCH, "W/\"cookie-jwt\"");
                },
                resp -> Assertions.assertThat(resp.cookies()).noneMatch(c -> c.startsWith(DEFAULT_REMEMBER_DEVICE_COOKIE_NAME)),
                304, "Not Modified", null);
    }

    @Test
    public void shouldParseIfNoneMatch() {
        Assertions.assertThat(parseEtag(null)).isNull();
        Assertions.assertThat(parseEtag("*")).isNull();
        Assertions.assertThat(parseEtag("\"\"")).isNull();
        Assertions.assertThat(parseEtag("\"a.b.c\"")).isEqualTo("a.b.c");
        Assertions.assertThat(parseEtag("W/\"a.b.c\"")).isEqualTo("a.b.c");
        Assertions.assertThat(parseEtag("\"a.b.c\", \"d\"")).isEqualTo("a.b.c");
    }

    private void givenEtagEnabled() {
        when(deviceIdentifierManager.useEtagBasedDeviceIdentifier(client)).thenReturn(true);
    }

    private void givenValidToken(String raw, String deviceId, boolean issuedByEndpoint) {
        var jwt = new JWT();
        jwt.setJti(deviceId);
        jwt.setExp(System.currentTimeMillis() / 1000 + 3600);
        if (issuedByEndpoint) {
            jwt.put(ETAG_ISSUED_CLAIM, true);
        }
        when(jwtService.decodeAndVerify(eq(raw), eq(client), eq(JWTService.TokenType.SESSION))).thenReturn(Single.just(jwt));
    }
}
