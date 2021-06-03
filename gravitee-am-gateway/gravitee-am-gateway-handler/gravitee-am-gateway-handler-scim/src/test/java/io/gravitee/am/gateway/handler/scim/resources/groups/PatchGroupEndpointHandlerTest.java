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
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.scim.model.PatchOp;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;


/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PatchGroupEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private GroupService groupService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GroupEndpoint groupEndpoint = new GroupEndpoint(groupService, objectMapper);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route()
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldReturn400WhenInvalidOperations() throws Exception {
        router.route("/Groups").handler(groupEndpoint::patch);

        testRequest(
                HttpMethod.PUT,
                "/Groups",
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

    private PatchOp emptyPatchOp() {
        PatchOp patchOp = new PatchOp();
        patchOp.setSchemas(PatchOp.SCHEMAS);
        patchOp.setOperations(Collections.emptyList());
        return patchOp;
    }
}
