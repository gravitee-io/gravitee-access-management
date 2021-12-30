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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestParseParametersHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(new AuthorizationRequestParseParametersHandler(domain))
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
}
