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
package io.gravitee.am.gateway.handler.scim.resources.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.scim.exception.InvalidValueException;
import io.gravitee.am.gateway.handler.scim.model.GraviteeUser;
import io.gravitee.am.gateway.handler.scim.model.Meta;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.service.exception.EmailFormatInvalidException;
import io.gravitee.am.service.exception.InvalidUserException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UpdateUserEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private UserService userService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ObjectWriter objectWriter;

    @Mock
    private Domain domain;

    @InjectMocks
    private UserEndpoint userEndpoint = new UserEndpoint(domain, userService, objectMapper);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // object mapper
        when(objectWriter.writeValueAsString(any())).thenReturn("UserObject");
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        when(userService.get(any(), any())).thenReturn(Maybe.empty());

        router.route()
                .handler(BodyHandler.create()).handler(rc -> {
                    JWT token = new JWT();
                    token.put("idp", "123456");
                    rc.put(ConstantKeys.TOKEN_CONTEXT_KEY, token);
                    rc.next();
                })
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldNotInvokeSCIMUpdateUserEndpoint_invalid_password() throws Exception {
        router.route("/Users").handler(userEndpoint::update);
        when(userService.update(eq(null), any(), eq(null), any(), any())).thenReturn(Single.error(new InvalidValueException("Field [password] is invalid")));

        testRequest(
                HttpMethod.PUT,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getUser()));
                },
                400,
                "Bad Request",
                "{\n" +
                        "  \"status\" : \"400\",\n" +
                        "  \"scimType\" : \"invalidValue\",\n" +
                        "  \"detail\" : \"Field [password] is invalid\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
    }

    @Test
    public void shouldInvokeSCIMUpdateUserEndpoint_valid_password() throws Exception {
        router.route("/Users").handler(userEndpoint::update);
        when(userService.update(any(), any(), eq(null), any(), any())).thenReturn(Single.just(getUser()));

        testRequest(
                HttpMethod.PUT,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getUser()));
                },
                200,
                "OK", null);
    }

    @Test
    public void shouldNotInvokeSCIMUpdateUserEndpoint_invalid_roles() throws Exception {
        router.route("/Users").handler(userEndpoint::update);
        when(userService.update(any(), any(), eq(null), anyString(), any())).thenReturn(Single.error(new InvalidValueException("Role [role-1] can not be found.")));

        testRequest(
                HttpMethod.PUT,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getUser()));
                },
                400,
                "Bad Request",
                "{\n" +
                        "  \"status\" : \"400\",\n" +
                        "  \"scimType\" : \"invalidValue\",\n" +
                        "  \"detail\" : \"Role [role-1] can not be found.\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
    }

    @Test
    public void shouldReturn400WhenInvalidUserException() throws Exception {
        router.route("/Users").handler(userEndpoint::update);
        when(userService.update(any(), any(), eq(null), anyString(), any())).thenReturn(Single.error(new InvalidUserException("Invalid user infos")));

        testRequest(
                HttpMethod.PUT,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getUser()));
                },
                400,
                "Bad Request",
                "{\n" +
                        "  \"status\" : \"400\",\n" +
                        "  \"scimType\" : \"invalidValue\",\n" +
                        "  \"detail\" : \"Invalid user infos\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
    }

    @Test
    public void shouldReturn400WhenEmailFormatInvalidException() throws Exception {
        router.route("/Users").handler(userEndpoint::update);
        when(userService.update(any(), any(), eq(null), anyString(), any())).thenReturn(Single.error(new EmailFormatInvalidException("Invalid email")));

        testRequest(
                HttpMethod.PUT,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getUser()));
                },
                400,
                "Bad Request",
                "{\n" +
                        "  \"status\" : \"400\",\n" +
                        "  \"scimType\" : \"invalidValue\",\n" +
                        "  \"detail\" : \"Value [Invalid email] is not a valid email.\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
    }

    @Test
    public void shouldUseASelectedIdp() throws Exception {
        SCIMSettings scimSettings = mock(SCIMSettings.class);
        when(scimSettings.isIdpSelectionEnabled()).thenReturn(true);
        when(scimSettings.getIdpSelectionRule()).thenReturn("{#context.attributes['token']['idp']}");
        when(domain.getScim()).thenReturn(scimSettings);
        router.route("/Users").handler(userEndpoint::update);
        when(userService.update(any(), any(), eq("123456"), any(), any())).thenReturn(Single.just(getUser()));

        testRequest(
                HttpMethod.PUT,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getUser()));
                },
                200,
                "OK", null);
    }

    @Test
    public void shouldAcceptCustomGraviteeUser() throws Exception {
        SCIMSettings scimSettings = mock(SCIMSettings.class);
        when(domain.getScim()).thenReturn(scimSettings);
        router.route("/Users").handler(userEndpoint::update);
        when(userService.update(any(), any(), any(), any(), any())).thenReturn(Single.just(getUser()));

        testRequest(
                HttpMethod.PUT,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getGraviteeUser()));
                },
                200,
                "OK", null);

        ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(userService).update(any(), userArgumentCaptor.capture(),  any(), any(), any());
        User scimUser = userArgumentCaptor.getValue();
        assertTrue(scimUser instanceof GraviteeUser);
        Map<String, Object> additionalInformation = ((GraviteeUser) scimUser).getAdditionalInformation();
        assertTrue(additionalInformation != null && additionalInformation.get("customClaim").equals("customValue"));
    }

    private User getUser() {
        User user = new User();
        user.setUserName("username");
        user.setSchemas(User.SCHEMAS);
        user.setPassword("toto");

        Meta meta = new Meta();
        meta.setLocation("http://test");
        user.setMeta(meta);

        return user;
    }

    private User getGraviteeUser() {
        GraviteeUser user = new GraviteeUser();
        user.setUserName("username");
        user.setSchemas(GraviteeUser.SCHEMAS);
        user.setPassword("toto");
        user.setAdditionalInformation(Collections.singletonMap("customClaim", "customValue"));

        Meta meta = new Meta();
        meta.setLocation("http://test");
        user.setMeta(meta);

        return user;
    }
}
