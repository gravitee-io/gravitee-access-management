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
import io.gravitee.am.common.scim.filter.Filter;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.scim.model.ListResponse;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UsersEndpointTest extends RxWebTestBase {

    @Mock
    private UserService userService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ObjectWriter objectWriter;

    @InjectMocks
    private UsersEndpoint usersEndpoint = new UsersEndpoint(userService, objectMapper);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // object mapper
        when(objectWriter.writeValueAsString(any())).thenReturn("UserObject");
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        router.get("/Users")
                .handler(usersEndpoint::list)
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldListUsers() throws Exception {
        when(userService.list(eq(null), eq(0), eq(100), anyString())).thenReturn(Single.just(new ListResponse<>()));
        testRequest(
                HttpMethod.GET,
                "/Users",
                req -> {},
                200,
                "OK",
                "UserObject");
    }

    @Test
    public void shouldNotListUsers_invalidFilter() throws Exception {
        testRequest(
                HttpMethod.GET,
                "/Users?filter=wrong",
                req -> {},
                400,
                "Bad Request",
                "{\n" +
                        "  \"status\" : \"400\",\n" +
                        "  \"scimType\" : \"invalidSyntax\",\n" +
                        "  \"detail\" : \"Invalid filter 'wrong': End of input at position 5 but expected an attribute operator\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
    }

    @Test
    public void shouldListUsers_validFilter() throws Exception {
        when(userService.list(any(Filter.class), eq(0), eq(100), anyString())).thenReturn(Single.just(new ListResponse<>()));
        testRequest(
                HttpMethod.GET,
                "/Users?filter=userName%20eq%20%22bjensen%22",
                request -> {
                    request.query();
                },
                200,
                "OK",
                "UserObject");
    }
}
