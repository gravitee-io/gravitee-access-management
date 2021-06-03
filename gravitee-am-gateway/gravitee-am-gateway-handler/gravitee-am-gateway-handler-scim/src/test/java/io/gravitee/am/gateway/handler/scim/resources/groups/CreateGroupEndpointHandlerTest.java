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
package io.gravitee.am.gateway.handler.scim.resources.groups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.Group;
import io.gravitee.am.gateway.handler.scim.model.Meta;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.service.exception.InvalidGroupException;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CreateGroupEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private GroupService groupService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ObjectWriter objectWriter;

    @InjectMocks
    private final GroupsEndpoint groupsEndpoint = new GroupsEndpoint(groupService, objectMapper);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // object mapper
        when(objectWriter.writeValueAsString(any())).thenReturn("GroupObject");
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);

        router.route()
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldInvokeSCIMCreateGroupsEndpoint() throws Exception {
        router.route("/Groups").handler(groupsEndpoint::create);
        when(groupService.create(any(), any())).thenReturn(Single.just(getGroup()));

        testRequest(
                HttpMethod.POST,
                "/Groups",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getGroup()));
                },
                201,
                "Created", null);
    }


    @Test
    public void shouldReturn409WhenNameAlreadyExists() throws Exception {
        router.route("/Groups").handler(groupsEndpoint::create);
        when(groupService.create(any(), any())).thenReturn(Single.error(new UniquenessException("Display name already exists")));

        testRequest(
                HttpMethod.POST,
                "/Groups",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getGroup()));
                },
                409,
                "Conflict",
                "{\n" +
                        "  \"status\" : \"409\",\n" +
                        "  \"scimType\" : \"uniqueness\",\n" +
                        "  \"detail\" : \"Display name already exists\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
    }

    @Test
    public void shouldReturn400WhenInvalidGroupException() throws Exception {
        router.route("/Groups").handler(groupsEndpoint::create);
        when(groupService.create(any(), any())).thenReturn(Single.error(new InvalidGroupException("Invalid group infos")));

        testRequest(
                HttpMethod.POST,
                "/Groups",
                req -> {
                    req.setChunked(true);
                    req.write(Json.encode(getGroup()));
                },
                400,
                "Bad Request",
                "{\n" +
                        "  \"status\" : \"400\",\n" +
                        "  \"scimType\" : \"invalidValue\",\n" +
                        "  \"detail\" : \"Invalid group infos\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");
    }

    private Group getGroup() {
        Group group = new Group();
        group.setDisplayName("my group");
        group.setSchemas(Group.SCHEMAS);

        Meta meta = new Meta();
        meta.setLocation("http://test");
        group.setMeta(meta);

        return group;
    }
}
