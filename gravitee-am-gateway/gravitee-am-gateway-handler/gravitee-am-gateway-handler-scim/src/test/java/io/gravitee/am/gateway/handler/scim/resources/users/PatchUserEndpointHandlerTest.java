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
import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.scim.model.*;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PatchUserEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private UserService userService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Domain domain;

    @Mock
    private SubjectManager subjectManager;

    @InjectMocks
    private UserEndpoint userEndpoint = new UserEndpoint(domain, userService, objectMapper, subjectManager);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        ObjectWriter objectWriter = mock(ObjectWriter.class);
        when(objectWriter.writeValueAsString(any())).thenReturn("UserObject");
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        router.route()
                .handler(BodyHandler.create())
                .handler(rc -> {
                    JWT token = new JWT();
                    token.put("sub", "user-id");
                    rc.put(ConstantKeys.TOKEN_CONTEXT_KEY, token);
                    rc.next();
                })
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldReturn400WhenInvalidOperations() throws Exception {
        router.route("/Users").handler(userEndpoint::patch);

        testRequest(
                HttpMethod.PATCH,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(emptyPatchOp()));
                },
                400,
                "Bad Request",
                "{\n" +
                        "  \"status\" : \"400\",\n" +
                        "  \"scimType\" : \"invalidValue\",\n" +
                        "  \"detail\" : \"Field [Operations] is required\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
    }

    @Test
    public void shouldPatchCustomGraviteeUser() throws Exception {
        router.route("/Users").handler(userEndpoint::patch);
        User scimUser = mock(User.class);
        when(scimUser.getMeta()).thenReturn(new Meta());
        when(subjectManager.getPrincipal(any())).thenReturn(Maybe.just(mock(io.gravitee.am.identityprovider.api.User.class)));
        when(userService.patch(any(), any(), any(), any(), any(), any())).thenReturn(Single.just(scimUser));

        testRequest(
                HttpMethod.PATCH,
                "/Users",
                req -> {
                    req.setChunked(true);
                    req.write("{\n" +
                            "     \"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],\n" +
                            "     \"Operations\": [{\n" +
                            "        \"op\":\"Add\",\n" +
                            "        \"path\":\"urn:ietf:params:scim:schemas:extension:custom:2.0:User\",\n" +
                            "        \"value\": {\n" +
                            "            \"customClaim\": \"customValue\"\n" +
                            "        }\n" +
                            "     }]\n" +
                            "\n" +
                            "}");
                },
                200,
                "OK", null);

        ArgumentCaptor<PatchOp> patchOpArgumentCaptor = ArgumentCaptor.forClass(PatchOp.class);
        verify(userService).patch(any(), patchOpArgumentCaptor.capture(),  any(), any(), any(), any());
        PatchOp patchOp = patchOpArgumentCaptor.getValue();
        assertEquals(Schema.SCHEMA_URI_CUSTOM_USER, patchOp.getOperations().get(0).getPath().getAttributePath());
    }

    private PatchOp emptyPatchOp() {
        PatchOp patchOp = new PatchOp();
        patchOp.setSchemas(PatchOp.SCHEMAS);
        patchOp.setOperations(Collections.emptyList());
        return patchOp;
    }
}
