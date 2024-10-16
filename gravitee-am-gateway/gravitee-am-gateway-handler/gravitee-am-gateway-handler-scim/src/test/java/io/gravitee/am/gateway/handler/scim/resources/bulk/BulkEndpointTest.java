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

package io.gravitee.am.gateway.handler.scim.resources.bulk;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.scim.model.BulkOperation;
import io.gravitee.am.gateway.handler.scim.model.BulkRequest;
import io.gravitee.am.gateway.handler.scim.model.BulkResponse;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.service.BulkService;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.stream.IntStream;

import static io.gravitee.am.gateway.handler.scim.model.BulkRequest.BULK_REQUEST_SCHEMA;
import static io.gravitee.common.http.HttpMethod.POST;
import static java.lang.String.valueOf;
import static java.util.List.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class BulkEndpointTest extends RxWebTestBase {

    @Mock
    private BulkService bulkService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ObjectWriter objectWriter;

    @Mock
    private Domain domain;

    @Mock
    private SubjectManager subjectManager;

    @InjectMocks
    private BulkEndpoint bulkEndpoint = new BulkEndpoint(bulkService, objectMapper, subjectManager);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.post("/Bulk")
            .handler(BodyHandler.create())
                .handler(bulkEndpoint::execute)
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void should_reject_too_many_request() throws Exception {
        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(IntStream.range(0, BulkEndpoint.BULK_MAX_REQUEST_OPERATIONS + 1).mapToObj(i -> bulkOp(valueOf(i))).toList());
        bulkRequest.setSchemas(of(BULK_REQUEST_SCHEMA));
        testRequest(
                HttpMethod.POST,
                "/Bulk",
                req -> {
                    req.end(Json.encode(bulkRequest));
                },
                413,
                "Request Entity Too Large",
                "{\n" +
                        "  \"status\" : \"413\",\n" +
                        "  \"detail\" : \"The bulk operation exceeds the maximum number of operations ("+BulkEndpoint.BULK_MAX_REQUEST_OPERATIONS+").\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");

        verify(bulkService, never()).processBulkRequest(any(), any(), any(), any(), any());
    }

    @Test
    public void should_reject_duplicated_bulkId() throws Exception {
        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(List.of(bulkOp("id"), bulkOp("id")));
        bulkRequest.setSchemas(of(BULK_REQUEST_SCHEMA));
        testRequest(
                HttpMethod.POST,
                "/Bulk",
                req -> {
                    req.end(Json.encode(bulkRequest));
                },
                400,
                "Bad Request",
                "{\n" +
                        "  \"status\" : \"400\",\n" +
                        "  \"scimType\" : \"invalidValue\",\n" +
                        "  \"detail\" : \"bulkId must be unique across all Operations\",\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:Error\" ]\n" +
                        "}");

        verify(bulkService, never()).processBulkRequest(any(), any(), any(), any(), any());
    }

    @Test
    public void should_execute_bulk_request() throws Exception {
        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(of(new BulkOperation()));
        bulkRequest.setSchemas(of(BULK_REQUEST_SCHEMA));

        when(subjectManager.getPrincipal(any())).thenReturn(Maybe.empty());
        BulkResponse response = new BulkResponse();
        final var operation = new BulkOperation();
        operation.setMethod(POST);
        operation.setBulkId("bulkid123");
        operation.setLocation("https://localhost:8092/domain/Users");
        operation.setStatus(valueOf(HttpStatusCode.CREATED_201));
        response.setOperations(of(operation));
        when(bulkService.processBulkRequest(any(), any(), any(), any(), any())).thenReturn(Single.just(response));

        testRequest(
                HttpMethod.POST,
                "/Bulk",
                req -> {
                    req.end(Json.encode(bulkRequest));
                },
                200,
                "OK",
                "{\n" +
                        "  \"Operations\" : [ {\n" +
                        "    \"method\" : \"POST\",\n" +
                        "    \"bulkId\" : \"bulkid123\",\n" +
                        "    \"version\" : null,\n" +
                        "    \"path\" : null,\n" +
                        "    \"data\" : null,\n" +
                        "    \"location\" : \"https://localhost:8092/domain/Users\",\n" +
                        "    \"response\" : null,\n" +
                        "    \"status\" : \"201\"\n" +
                        "  } ],\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:BulkResponse\" ]\n" +
                        "}");

        verify(bulkService).processBulkRequest(any(), any(), any(), any(), any());
    }

    private static BulkOperation bulkOp(String bulkId){
        BulkOperation operation = new BulkOperation();
        operation.setBulkId(bulkId);
        return operation;
    }

}
