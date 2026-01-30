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

package io.gravitee.am.gateway.handler.scim.service;


import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.BulkOperation;
import io.gravitee.am.gateway.handler.scim.model.BulkRequest;
import io.gravitee.am.gateway.handler.scim.model.Error;
import io.gravitee.am.gateway.handler.scim.model.Meta;
import io.gravitee.am.gateway.handler.scim.model.User;
import io.gravitee.am.gateway.handler.scim.service.impl.BulkServiceImpl;
import io.gravitee.am.gateway.handler.scim.spring.SCIMConfiguration;
import io.gravitee.am.identityprovider.api.DummyAuthenticationContext;
import io.gravitee.am.identityprovider.api.DummyRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.lang.String.valueOf;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.util.StringUtils.hasLength;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class BulkServiceTest {
    private static final String BASE_BULK_URL = "http://localhost:8092/mydomain/Bulk";
    private static final String META_LOCATION_URL = "http://localhost:8092/mydomain/Users/xyz";

    private BulkService bulkService;

    @Mock
    public Domain domain;

    @Mock
    public ProvisioningUserService userService;

    public DummyAuthenticationContext authenticationContext;

    public DummyRequest request;

    public Client client;

    @BeforeEach
    public void init() {
        this.bulkService = new BulkServiceImpl(userService, domain, 1);
        this.client = new Client();
        this.request = new DummyRequest();
        this.authenticationContext = new DummyAuthenticationContext(new HashMap<>(), request);
    }

    @Test
    public void should_fail_due_to_invalid_operation_missing_path() throws Exception {
        final var bulkId = UUID.randomUUID().toString();
        final var method = HttpMethod.POST;

        final var bulkRequest = new BulkRequest();
        final var operation = new BulkOperation();
        operation.setBulkId(bulkId);
        operation.setMethod(method);
        operation.setPath(null);
        operation.setData(Map.of());
        bulkRequest.setOperations(of(operation));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    assertError((Error) bulkResponse.getOperations().get(0).getResponse(), "Bulk operation requires path starting with /Users", HttpStatusCode.BAD_REQUEST_400);

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertEquals(bulkId, processedOperation.getBulkId());
                    assertEquals(method, processedOperation.getMethod());
                    assertEquals(valueOf(HttpStatusCode.BAD_REQUEST_400), processedOperation.getStatus());
                    assertNull(processedOperation.getPath());
                    assertNull(processedOperation.getData());
                    return true;
                });
    }

    @Test
    public void should_fail_due_to_invalid_operation_invalid_path() throws Exception {
        final var bulkId = UUID.randomUUID().toString();
        final var method = HttpMethod.POST;

        final var bulkRequest = new BulkRequest();
        final var operation = new BulkOperation();
        operation.setBulkId(bulkId);
        operation.setMethod(method);
        operation.setPath("/Groups");
        operation.setData(Map.of());
        bulkRequest.setOperations(of(operation));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    assertError((Error) bulkResponse.getOperations().get(0).getResponse(), "Bulk operation requires path starting with /Users", HttpStatusCode.BAD_REQUEST_400);

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertEquals(bulkId, processedOperation.getBulkId());
                    assertEquals(method, processedOperation.getMethod());
                    assertEquals(valueOf(HttpStatusCode.BAD_REQUEST_400), processedOperation.getStatus());
                    assertNull(processedOperation.getPath());
                    assertNull(processedOperation.getData());
                    return true;
                });
    }

    @Test
    public void should_fail_due_to_invalid_operation_invalid_method() throws Exception {
        final var bulkId = UUID.randomUUID().toString();
        final var method = HttpMethod.GET;

        final var bulkRequest = new BulkRequest();
        final var operation = new BulkOperation();
        operation.setBulkId(bulkId);
        operation.setMethod(method);
        operation.setPath("/Users");
        operation.setData(Map.of());
        bulkRequest.setOperations(of(operation));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    assertError((Error) bulkResponse.getOperations().get(0).getResponse(), "Bulk operation doesn't support method GET", HttpStatusCode.BAD_REQUEST_400);

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertEquals(bulkId, processedOperation.getBulkId());
                    assertEquals(method, processedOperation.getMethod());
                    assertEquals(valueOf(HttpStatusCode.BAD_REQUEST_400), processedOperation.getStatus());
                    assertNull(processedOperation.getPath());
                    assertNull(processedOperation.getData());
                    return true;
                });
    }

    @Test
    public void should_fail_due_to_invalid_operation_POST_without_bulkId() throws Exception {
        final var method = HttpMethod.POST;

        final var bulkRequest = new BulkRequest();
        final var operation = new BulkOperation();
        operation.setMethod(method);
        operation.setPath("/Users");
        operation.setData(asMap(new User()));
        bulkRequest.setOperations(of(operation));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    assertError((Error) bulkResponse.getOperations().get(0).getResponse(), "Bulk operation requires bulkId with method POST", HttpStatusCode.BAD_REQUEST_400);

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertNull(processedOperation.getBulkId());
                    assertEquals(method, processedOperation.getMethod());
                    assertEquals(valueOf(HttpStatusCode.BAD_REQUEST_400), processedOperation.getStatus());
                    assertNull(processedOperation.getPath());
                    assertNull(processedOperation.getData());
                    return true;
                });
    }

    @Test
    public void should_fail_due_to_invalid_path_with_PUT_operation() throws Exception {
        final var method = HttpMethod.PUT;

        final var bulkRequest = new BulkRequest();
        final var operation = new BulkOperation();
        operation.setMethod(method);
        operation.setPath("/Users");
        operation.setData(asMap(new User()));
        bulkRequest.setOperations(of(operation));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    assertError((Error) bulkResponse.getOperations().get(0).getResponse(), "Bulk operation with PUT or PATCH method requires path with userId", HttpStatusCode.BAD_REQUEST_400);

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertNull(processedOperation.getBulkId());
                    assertEquals(method, processedOperation.getMethod());
                    assertEquals(valueOf(HttpStatusCode.BAD_REQUEST_400), processedOperation.getStatus());
                    assertNull(processedOperation.getPath());
                    assertNull(processedOperation.getData());
                    return true;
                });
    }

    @Test
    public void should_create_user() throws Exception {
        final var method = HttpMethod.POST;
        final var bulkId = UUID.randomUUID().toString();

        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(of(validCreateUserOperation(method, bulkId)));


        User createdUser = getCreatedUser();
        when(userService.create(any(), any(), any(), any(), any())).thenReturn(Single.just(createdUser));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertUserCreationSuccessful(processedOperation, method);
                    return true;
                });
    }

    @Test
    public void should_create_users_even_if_one_operation_is_invalid() throws Exception {
        final var method = HttpMethod.POST;
        final var bulkId = UUID.randomUUID().toString();

        final var bulkRequest = new BulkRequest();;
        BulkOperation validOperation1 = validCreateUserOperation(method, bulkId);
        BulkOperation validOperationUniquenessError = validCreateUserOperation(method, bulkId);
        BulkOperation validOperation2 = validCreateUserOperation(method, bulkId);
        BulkOperation invalidOperation = validCreateUserOperation(method, null);
        BulkOperation invalidOperationBadUserPayload = validCreateUserOperation(method, bulkId);
        invalidOperationBadUserPayload.getData().put("UnknownField", "which should make the profile invalid");
        // First operation and last one are valid, second is missing the bulkId, third will reject the user creation due to username uniqueness
        bulkRequest.setOperations(of(validOperation1, invalidOperation, validOperationUniquenessError, validOperation2, invalidOperationBadUserPayload));

        User createdUser = getCreatedUser();
        when(userService.create(any(), any(), any(), any(), any())).thenReturn(
                Single.just(createdUser),
                Single.error(() -> new UniquenessException("User with username [username] already exists")),
                Single.just(createdUser));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(5, bulkResponse.getOperations().size());

                    assertUserCreationSuccessful(bulkResponse.getOperations().get(0), method);
                    assertError((Error)bulkResponse.getOperations().get(1).getResponse(), "Bulk operation requires bulkId with method POST", HttpStatusCode.BAD_REQUEST_400);
                    assertError((Error)bulkResponse.getOperations().get(2).getResponse(), "User with username [username] already exists", HttpStatusCode.CONFLICT_409);
                    assertError((Error)bulkResponse.getOperations().get(4).getResponse(), null, HttpStatusCode.BAD_REQUEST_400);
                    assertUserCreationSuccessful(bulkResponse.getOperations().get(3), method);

                    return true;
                });
    }

    @Test
    public void should_update_user() throws Exception {
        final var method = HttpMethod.PUT;
        final var bulkId = UUID.randomUUID().toString();

        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(of(validUpdateUserOperation(method, bulkId)));


        User updatedUser = getUser(bulkId);
        when(userService.update(any(), any(), any(), any(), any(), any())).thenReturn(Single.just(updatedUser));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertUserUpdateSuccessful(processedOperation, method, bulkId);
                    return true;
                });
    }

    @Test
    public void should_patch_user() throws Exception {
        final var method = HttpMethod.PATCH;
        final var bulkId = UUID.randomUUID().toString();

        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(of(patchUserOperation(method, bulkId, "add")));


        User updatedUser = getUser(bulkId);
        when(userService.patch(any(), any(), any(), any(), any(), any())).thenReturn(Single.just(updatedUser));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertUserUpdateSuccessful(processedOperation, method, bulkId);
                    return true;
                });
    }
    @Test
    public void should_not_patch_user_invalid_data() throws Exception {
        final var method = HttpMethod.PATCH;
        final var bulkId = UUID.randomUUID().toString();

        final var bulkRequest = new BulkRequest();
        BulkOperation patchOp = patchUserOperation(method, bulkId, "add");
        patchOp.getData().put("unknownKey", "useless value");
        bulkRequest.setOperations(of(patchOp));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertError((Error)processedOperation.getResponse(), null, HttpStatusCode.BAD_REQUEST_400);
                    return true;
                });
    }

    @Test
    public void should_not_patch_user_missing_patch_operation() throws Exception {
        final var method = HttpMethod.PATCH;
        final var bulkId = UUID.randomUUID().toString();

        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(of(patchUserOperation(method, bulkId, null)));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    final var processedOperation = bulkResponse.getOperations().get(0);
                    assertEquals(valueOf(HttpStatusCode.BAD_REQUEST_400) ,processedOperation.getStatus());
                    assertError((Error)processedOperation.getResponse(), "Field [Operations] is required", HttpStatusCode.BAD_REQUEST_400);
                    return true;
                });
    }

    @Test
    public void should_delete_user() throws Exception {
        final var method = HttpMethod.DELETE;
        final var bulkId = UUID.randomUUID().toString();

        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(of(validDeleteUserOperation(method, bulkId)));


        when(userService.delete(any(), any())).thenReturn(Completable.complete());

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    final var processedOperation = bulkResponse.getOperations().get(0);

                    assertNotNull(processedOperation.getBulkId());
                    assertEquals(method, processedOperation.getMethod());
                    assertEquals(valueOf(HttpStatusCode.NO_CONTENT_204), processedOperation.getStatus());
                    assertNull(processedOperation.getPath());
                    assertNull(processedOperation.getData());

                    assertEquals(adaptLocation(bulkId), processedOperation.getLocation());
                    return true;
                });
    }

    @Test
    public void should_return_error_on_delete_unknown_user() throws Exception {
        final var method = HttpMethod.DELETE;
        final var bulkId = UUID.randomUUID().toString();

        final var bulkRequest = new BulkRequest();
        bulkRequest.setOperations(of(validDeleteUserOperation(method, bulkId)));


        when(userService.delete(any(), any())).thenReturn(Completable.error(new UserNotFoundException()));

        final var subscriber = bulkService.processBulkRequest(bulkRequest, this.authenticationContext, BASE_BULK_URL, client, null).test();

        subscriber.await(10, TimeUnit.SECONDS);
        subscriber
                .assertNoErrors()
                .assertValue(bulkResponse -> {
                    assertNotNull(bulkResponse.getSchemas());
                    assertEquals(1, bulkResponse.getSchemas().size());

                    assertNotNull(bulkResponse.getOperations());
                    assertEquals(1, bulkResponse.getOperations().size());

                    final var processedOperation = bulkResponse.getOperations().get(0);

                    assertNotNull(processedOperation.getBulkId());
                    assertEquals(method, processedOperation.getMethod());
                    assertEquals(valueOf(HttpStatusCode.NOT_FOUND_404), processedOperation.getStatus());
                    assertNull(processedOperation.getPath());
                    assertNull(processedOperation.getData());

                    assertError((Error)processedOperation.getResponse(), "No user found", HttpStatusCode.NOT_FOUND_404);
                    return true;
                });
    }

    private static void assertUserCreationSuccessful(BulkOperation processedOperation, HttpMethod method) {
        assertNotNull(processedOperation.getBulkId());
        assertEquals(method, processedOperation.getMethod());
        assertEquals(valueOf(HttpStatusCode.CREATED_201), processedOperation.getStatus());
        assertNull(processedOperation.getPath());
        assertNull(processedOperation.getData());

        assertEquals(META_LOCATION_URL, processedOperation.getLocation());
        assertNull(processedOperation.getResponse());
    }

    private static void assertUserUpdateSuccessful(BulkOperation processedOperation, HttpMethod method, String bulkId) {
        assertNotNull(processedOperation.getBulkId());
        assertEquals(method, processedOperation.getMethod());
        assertEquals(valueOf(HttpStatusCode.OK_200), processedOperation.getStatus());
        assertNull(processedOperation.getPath());
        assertNull(processedOperation.getData());

        assertEquals(adaptLocation(bulkId), processedOperation.getLocation());
        assertNull(processedOperation.getResponse());
    }

    private static String adaptLocation(String bulkId) {
        return META_LOCATION_URL.replace("xyz", "user-" + bulkId);
    }

    private static User getCreatedUser() {
        return getUser(null);
    }

    private static User getUser(String userId) {
        User createdUser = new User();
        createdUser.setSchemas(of(Schema.SCHEMA_URI_USER));
        createdUser.setUserName("username");
        Meta meta = new Meta();
        meta.setLocation(userId != null ? adaptLocation(userId) : META_LOCATION_URL);
        createdUser.setMeta(meta);
        return createdUser;
    }

    private static Map<String, Object> generatePatchUser(String op) {
        if (hasLength(op)) {
            return Json.decodeValue("""
                    { "schemas":
                           ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                         "Operations":[
                           {
                            "op":"%s",
                            "path":"given_name",
                            "value":"Test"
                           }
                         ]
                       }
                    """.formatted(op), Map.class);
        } else {
            return Json.decodeValue("""
                    { "schemas":
                           ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                         "Operations":[]
                       }
                    """, Map.class);
        }
    }

    private BulkOperation validCreateUserOperation(HttpMethod method, String bulkId) {
        final var validOperation1 = new BulkOperation();
        validOperation1.setMethod(method);
        validOperation1.setBulkId(bulkId);
        validOperation1.setPath("/Users");
        User user = new User();
        user.setSchemas(of(Schema.SCHEMA_URI_USER));
        user.setUserName("username");
        validOperation1.setData(asMap(user));
        return validOperation1;
    }

    private BulkOperation validUpdateUserOperation(HttpMethod method, String bulkId) {
        final var validOperation1 = new BulkOperation();
        validOperation1.setMethod(method);
        validOperation1.setBulkId(bulkId);
        validOperation1.setPath("/Users/user-"+bulkId);
        User user = new User();
        user.setSchemas(of(Schema.SCHEMA_URI_USER));
        user.setUserName("username");
        validOperation1.setData(asMap(user));
        return validOperation1;
    }

    private BulkOperation validDeleteUserOperation(HttpMethod method, String bulkId) {
        final var validOperation1 = new BulkOperation();
        validOperation1.setMethod(method);
        validOperation1.setBulkId(bulkId);
        validOperation1.setPath("/Users/user-"+bulkId);
        return validOperation1;
    }

    private BulkOperation patchUserOperation(HttpMethod method, String bulkId, String op) {
        final var validOperation1 = new BulkOperation();
        validOperation1.setMethod(method);
        validOperation1.setBulkId(bulkId);
        validOperation1.setPath("/Users/user-"+bulkId);
        validOperation1.setData(generatePatchUser(op));
        return validOperation1;
    }

    private void assertError(Error error, String expectedMsg, int expectedCode) {
        if (expectedMsg != null) {
            assertEquals(expectedMsg, error.getDetail());
        }
        assertEquals(valueOf(expectedCode), error.getStatus());
    }

    private <T> Map asMap(T data) {
        return Json.decodeValue(Json.encode(data), Map.class);
    }
}
