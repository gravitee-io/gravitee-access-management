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
package io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.gravitee.am.gateway.handler.scim.UserService;
import io.gravitee.am.gateway.handler.scim.model.Meta;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.handler.scim.handler.ErrorHandler;
import io.gravitee.am.service.authentication.crypto.password.PasswordValidator;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CreateUserEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private UserService userService;

    @Mock
    private PasswordValidator passwordValidator;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ObjectWriter objectWriter;

    @InjectMocks
    private CreateUserEndpointHandler createUserEndpointHandler = new CreateUserEndpointHandler(userService);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // set handler
        createUserEndpointHandler.setPasswordValidator(passwordValidator);
        createUserEndpointHandler.setObjectMapper(objectMapper);

        // object mapper
        when(objectWriter.writeValueAsString(any())).thenReturn("UserObject");
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        router.route()
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldNotInvokeSCIMCreateUserEndpoint_invalid_password() throws Exception {
        router.route("/Users").handler(createUserEndpointHandler);
        when(passwordValidator.validate(anyString())).thenReturn(false);

        testRequest(
                HttpMethod.POST,
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
    public void shouldInvokeSCIMCreateUserEndpoint_valid_password() throws Exception {
        router.route("/Users").handler(createUserEndpointHandler);
        when(passwordValidator.validate(anyString())).thenReturn(true);
        when(userService.create(any(), any())).thenReturn(Single.just(getUser()));

        testRequest(
                HttpMethod.POST,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getUser()));
                },
                201,
                "Created", null);
    }

    @Test
    public void shouldNotInvokeSCIMCreateUserEndpoint_invalid_identity_provider() throws Exception {
        User user = getUser();
        user.setSource("unknown-idp");

        router.route("/Users").handler(createUserEndpointHandler);
        when(passwordValidator.validate(anyString())).thenReturn(true);
        when(userService.create(any(), any())).thenReturn(Single.error(new UserProviderNotFoundException(user.getSource())));

        testRequest(
                HttpMethod.POST,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(user));
                },
                404,
                "Not Found",
                "{\n" +
                        "  \"status\" : \"404\",\n" +
                        "  \"detail\" : \"User provider [unknown-idp] can not be found.\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
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
}
