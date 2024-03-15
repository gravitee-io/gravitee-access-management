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
package io.gravitee.am.gateway.handler.oauth2.resources.handler;

import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.oidc.AcrValues;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseParametersHandler;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.root.resources.handler.common.RedirectUriValidationHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.impl.UserImpl;
import io.vertx.ext.web.Session;
import io.vertx.rxjava3.ext.auth.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.STRONG_AUTH_COMPLETED_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_ID_KEY;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestParseParametersHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;

    @Mock
    private Session session;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(new AuthorizationRequestParseParametersHandler(domain))
                .handler(new RedirectUriValidationHandler(domain))
                .handler(rc -> rc.response().end())
                .failureHandler(rc -> rc.response().setStatusCode(400).end());
    }

    @Test
    public void shouldRejectRequest_unsupportedAcrValues() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_BRONZE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_uriMismatch() throws Exception {
        doReturn(false).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://notCallback&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);

    }

    @Test
    public void shouldRejectRequest_uriNotFormattedCorrectly() throws Exception {
        doReturn(false).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback*"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://notCallback&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldAcceptRequest_supportedAcrValues() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldAcceptRequest_redirect_URI_ok() throws Exception {
        doReturn(true).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback/strict"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/strict&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldRejectRequest_ChallengeMethod_Invalid_defaultValue_plain() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback/strict"));
        client.setForceS256CodeChallengeMethod(true);
        client.setForcePKCE(true);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/strict&code_challenge=plaincodevalueplaincodevalueplaincodevalue123465789",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldAcceptRequest_ChallengeMethod_Plain() throws Exception {
        doReturn(true).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback/strict"));
        client.setForceS256CodeChallengeMethod(false);
        client.setForcePKCE(true);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/strict&code_challenge=plaincodevalueplaincodevalueplaincodevalue123465789",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldRejectRequest_ChallengeMethod_Invalid_provided_plain() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback/strict"));
        client.setForceS256CodeChallengeMethod(true);
        client.setForcePKCE(true);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/strict&code_challenge=plaincodevalueplaincodevalueplaincodevalue123465789&code_challenge_method=plain",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldAcceptRequest_ChallengeMethod_S256() throws Exception {
        doReturn(true).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback/strict"));
        client.setForceS256CodeChallengeMethod(true);
        client.setForcePKCE(true);

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/strict&code_challenge=codechallenges256codechallenges256codechallenges256codechallenges256&code_challenge_method=S256",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldAcceptRequest_redirect_URI_ok_2() throws Exception {
        doReturn(false).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback/"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/strict&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldAcceptRequest_redirect_URI_ok_3() throws Exception {
        doReturn(false).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback/str"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/strict&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldAcceptRequest_redirect_URI_ok_4() throws Exception {
        doReturn(false).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://*all*ack"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldAcceptRequest_redirect_URI_ok_5() throws Exception {
        doReturn(false).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://callback/*"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/auth&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldAcceptRequest_redirect_URI_ok_6() throws Exception {
        doReturn(false).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://call*ack:5892/*"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback:5892/auth&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldNotAcceptRequest_redirect_URI_KO() throws Exception {
        doReturn(false).when(domain).isRedirectUriStrictMatching();
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setAcrValuesSupported(Collections.singletonList(AcrValues.IN_COMMON_SILVER));
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        client.setRedirectUris(List.of("https://*all*ack/auth"));
        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback/login&claims={\"id_token\":{\"acr\":{\"value\":\"urn:mace:incommon:iap:silver\",\"essential\":true}}}",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRemoveAuthorizationFromTheSessionOnPromptLoginParam() throws Exception {
        doReturn(false).when(session).get(ConstantKeys.USER_LOGIN_COMPLETED_KEY);
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE));
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.AUTHORIZATION_CODE));
        client.setResponseTypes(Collections.singletonList(ResponseType.CODE));
        router.route().order(-1)
                .handler(context -> {
                    context.setSession(new io.vertx.rxjava3.ext.web.Session(session));
                    context.setUser(new User(new UserImpl()));
                    context.next();
                })
                .handler(routingContext -> {
                    routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
                    routingContext.put(ConstantKeys.USER_ID_KEY, "111");
                    routingContext.put(ConstantKeys.STRONG_AUTH_COMPLETED_KEY, true);
                    routingContext.next();
                });
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&redirect_uri=https://callback&prompt=login",
                null,
                HttpStatusCode.OK_200, "OK", null);

        Mockito.verify(session, times(1)).remove(STRONG_AUTH_COMPLETED_KEY);
        Mockito.verify(session, times(1)).remove(USER_ID_KEY);
    }

}
