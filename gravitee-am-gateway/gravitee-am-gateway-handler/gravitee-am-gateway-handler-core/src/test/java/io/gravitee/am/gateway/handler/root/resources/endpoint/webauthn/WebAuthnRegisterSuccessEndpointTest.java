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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.service.CredentialGatewayService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.common.template.TemplateEngine;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static io.vertx.core.http.HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnRegisterSuccessEndpointTest extends RxWebTestBase {

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private CredentialGatewayService credentialService;

    @Mock
    private Domain domain;

    private WebAuthnRegisterSuccessEndpoint webAuthnRegisterSuccessEndpoint;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        webAuthnRegisterSuccessEndpoint = new WebAuthnRegisterSuccessEndpoint(templateEngine, credentialService, domain);

        router.route()
                .handler(SessionHandler.create(LocalSessionStore.create(vertx)))
                .handler(BodyHandler.create());
    }

    @Test
    public void shouldNotRenderPage_noCredential() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/register/success")
                .handler(webAuthnRegisterSuccessEndpoint);

        testRequest(
                HttpMethod.GET, "/webauthn/register/success",
                null,
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotRenderPage_templateNotRendering() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/register/success")
                .handler(rc -> {
                    rc.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, "credentialId");
                    User endUser = new User();
                    endUser.setUsername("username");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    rc.next();
                })
                .handler(webAuthnRegisterSuccessEndpoint);

        when(templateEngine.render(anyMap(), any())).thenThrow(new RuntimeException("Cannot render template"));
        testRequest(
                HttpMethod.GET, "/webauthn/register/success",
                null,
                null,
                HttpStatusCode.SERVICE_UNAVAILABLE_503, "Service Unavailable", null);

    }

    @Test
    public void shouldRenderPage_nominalCase() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/register/success")
                .handler(rc -> {
                    rc.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, "credentialId");
                    User endUser = new User();
                    endUser.setUsername("username");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    rc.next();
                })
                .handler(webAuthnRegisterSuccessEndpoint);

        ArgumentCaptor<Map> argument = ArgumentCaptor.forClass(Map.class);
        when(templateEngine.render(argument.capture(), any())).thenReturn(Single.just(Buffer.buffer()));
        testRequest(
                HttpMethod.GET, "/webauthn/register/success",
                req -> {
                    req.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X x.y; rv:42.0) Gecko/20100101 Firefox/42.0");
                },
                null,
                HttpStatusCode.OK_200, "OK", null);

        Map map = argument.getValue();
        Assert.assertEquals("username's Mac", map.get(ConstantKeys.PASSWORDLESS_DEVICE_NAME));
    }

    @Test
    @Parameters
    public void shouldRenderPage_nominalCase_iphone() throws Exception {
        router.route(HttpMethod.GET, "/webauthn/register/success")
                .handler(rc -> {
                    rc.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, "credentialId");
                    User endUser = new User();
                    endUser.setUsername("username");
                    rc.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    rc.next();
                })
                .handler(webAuthnRegisterSuccessEndpoint);

        ArgumentCaptor<Map> argument = ArgumentCaptor.forClass(Map.class);
        when(templateEngine.render(argument.capture(), any())).thenReturn(Single.just(Buffer.buffer()));
        testRequest(
                HttpMethod.GET, "/webauthn/register/success",
                req -> {
                    req.headers().add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPhone; U; CPU iPhone OS 4_1 like Mac OS X; en-us) AppleWebKit/532.9 (KHTML, like Gecko) Version/4.0.5 Mobile/8B117 Safari/6531.22.7 (compatible; Googlebot-Mobile/2.1; +http://www.google.com/bot.html)");
                },
                null,
                HttpStatusCode.OK_200, "OK", null);

        Map map = argument.getValue();
        Assert.assertEquals("username's iPhone", map.get(ConstantKeys.PASSWORDLESS_DEVICE_NAME));
    }

    @Test
    public void shouldNotRegister_noCredential() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register/success")
                .handler(webAuthnRegisterSuccessEndpoint);

        testRequest(
                HttpMethod.POST, "/webauthn/register/success",
                null,
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotRegister_noDeviceName() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register/success")
                .handler(rc -> {
                    rc.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, "credentialId");
                    rc.next();
                })
                .handler(webAuthnRegisterSuccessEndpoint);

        testRequest(
                HttpMethod.POST, "/webauthn/register/success",
                null,
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotRegister_noDevice_empty() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register/success")
                .handler(rc -> {
                    rc.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, "credentialId");
                    rc.next();
                })
                .handler(webAuthnRegisterSuccessEndpoint);

        testRequest(
                HttpMethod.POST, "/webauthn/register/success",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("deviceName="));
                },
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotRegister_noDevice_above_64() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register/success")
                .handler(rc -> {
                    rc.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, "credentialId");
                    rc.next();
                })
                .handler(webAuthnRegisterSuccessEndpoint);

        testRequest(
                HttpMethod.POST, "/webauthn/register/success",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("deviceName=fuuikdjhbcvzcrpjbvzpxrpjfshgoagrasttjzmrywmcjmftenlhbiwkgbdjlxxfuiggqsshgntdxwafttzyourxctahemkgzpcsnmosuhurele"));
                },
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotRegister_technicalException() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register/success")
                .handler(rc -> {
                    rc.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, "credentialId");
                    rc.next();
                })
                .handler(webAuthnRegisterSuccessEndpoint);

        when(credentialService.findByCredentialId(any(), any())).thenReturn(Flowable.empty());

        testRequest(HttpMethod.POST,
                "/webauthn/register/success",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("deviceName=deviceName"));
                },
                null,
                500,
                "Internal Server Error", null);
    }

    @Test
    public void shouldRegister_nominalCase() throws Exception {
        router.route(HttpMethod.POST, "/webauthn/register/success")
                .handler(rc -> {
                    rc.session().put(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY, "credentialId");
                    rc.next();
                })
                .handler(webAuthnRegisterSuccessEndpoint);

        when(credentialService.findByCredentialId(any(), any())).thenReturn(Flowable.just(new Credential()));
        when(credentialService.update(any(), any())).thenReturn(Single.just(new Credential()));

        testRequest(HttpMethod.POST,
                "/webauthn/register/success",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
                    req.write(Buffer.buffer("deviceName=deviceName"));
                },
                res -> {
                    String location = res.getHeader("Location");
                    Assert.assertTrue(location.contains("/oauth/authorize"));
                },
                302,
                "Found", null);
    }
}
