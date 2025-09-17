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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.OpenFGAService;
import io.gravitee.am.service.model.CreateOpenFGAStoreRequest;
import io.gravitee.am.service.model.OpenFGAStoreEntity;
import io.gravitee.am.service.model.OpenFGATuple;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author GraviteeSource Team
 */
@Path("openfga")
public class OpenFGAResource extends AbstractDomainResource {

    @Autowired
    private OpenFGAService openFGAService;

    // Mock data storage
    private static final Map<String, String> connectionUrls = new HashMap<>();
    private static final Map<String, List<OpenFGAStoreEntity>> domainStores = new HashMap<>();
    private static final Map<String, String> authorizationModels = new HashMap<>();
    private static final Map<String, List<Map<String, String>>> domainTuples = new HashMap<>();

    static {
        // Initialize mock stores
        List<OpenFGAStoreEntity> mockStores = Arrays.asList(
                createMockStore("01ARZ3NDEKTSV4RRFFQ69G5FAV", "Default Store",
                    new Date(System.currentTimeMillis() - 86400000)), // 1 day ago
                createMockStore("01ARZ3NDEKTSV4RRFFQ69G5FAW", "Production Store",
                    new Date(System.currentTimeMillis() - 172800000)) // 2 days ago
        );

        // Initialize mock tuples
        List<Map<String, String>> mockTuples = Arrays.asList(
            createTuple("alice", "owner", "document:1"),
            createTuple("bob", "reader", "document:1")
        );

        // Default mock authorization model
        String mockModel = "type user\n\ntype document\n  relations\n    define owner: [user]\n    define reader: [user] or owner\n    define writer: [user] or owner";
    }

    @POST
    @Path("connect")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Connect to OpenFGA server")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connection successful"),
            @ApiResponse(responseCode = "400", description = "Connection failed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void connect(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "connectionRequest") @Valid @NotNull final Map<String, String> connectionRequest,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.READ)
                .andThen(Single.defer(() -> {
                    String url = connectionRequest.get("serverUrl");
                    if (url == null || url.trim().isEmpty()) {
                        return Single.just(Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\": \"Server URL is required\"}")
                                .build());
                    }

                    // Test real connection to OpenFGA
                    return openFGAService.connect(url)
                            .map(resp -> {
                                if (resp.getStatus()) {
                                    return Response.ok(resp).build();
                                } else {
                                    return Response.status(Response.Status.BAD_REQUEST)
                                            .entity(resp)
                                            .build();
                                }
                            });
                }))
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("stores")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List OpenFGA stores")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of stores",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            array = @ArraySchema(schema = @Schema(implementation = OpenFGAStoreEntity.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void listStores(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.READ)
                .andThen(openFGAService.getStores().map(s -> Response.ok(s).build()))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("stores")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create OpenFGA store")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Store created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = OpenFGAStoreEntity.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void createStore(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Valid CreateOpenFGAStoreRequest request,
            @Suspended final AsyncResponse response) throws FgaInvalidParameterException {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.CREATE)
                .andThen(openFGAService.createStore(request.getName()).map(s -> Response.ok(s).build()))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Path("stores/{storeId}")
    @Operation(summary = "Delete OpenFGA store")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Store deleted"),
            @ApiResponse(responseCode = "404", description = "Store not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void deleteStore(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("storeId") String storeId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.DELETE)
                .andThen(Single.fromCallable(() -> {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Path("{storeId}/authorization-model")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update authorization model")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model updated"),
            @ApiResponse(responseCode = "400", description = "Invalid model"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void updateAuthorizationModel(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("storeId") String storeId,
            @Parameter(name = "modelRequest") @Valid @NotNull final Map<String, String> modelRequest,
            @Suspended final AsyncResponse response) throws FgaInvalidParameterException, JsonProcessingException {
        String model = modelRequest.get("authorizationModel");
        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.UPDATE)
                .andThen(openFGAService.setAuthenticationModel(storeId,model).map(m -> Response.ok(m).build()))
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("{storeId}/authorization-model")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get authorization model")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization model retrieved"),
            @ApiResponse(responseCode = "404", description = "Store not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void getAuthorizationModel(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("storeId") String storeId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.READ)
                .andThen(Single.defer(() -> {
                    try {
                        return openFGAService.getAuthorizationModel(storeId)
                                .map(model -> Response.ok("{\"authorizationModel\": " + model + "}").build());
                    } catch (FgaInvalidParameterException e) {
                        return Single.just(Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\": \"Invalid store parameters: " + e.getMessage() + "\"}")
                                .build());
                    }
                }))
                .subscribe(response::resume, response::resume);
    }

    @GET
    @Path("{storeId}/tuples")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List relationship tuples")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of tuples"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void listTuples(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("storeId") String storeId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.READ)
                .andThen(Single.defer(() -> {
                    try {
                        return openFGAService.getTuples(storeId).toList().map(tuples -> Response.ok(tuples).build());
                    } catch (FgaInvalidParameterException e) {
                        return Single.just(Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\": \"Invalid store parameters: " + e.getMessage() + "\"}")
                                .build());
                    }
                }))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("{storeId}/tuples")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add relationship tuple")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tuple added"),
            @ApiResponse(responseCode = "400", description = "Invalid tuple"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void addTuple(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("storeId") String storeId,
            @Parameter(name = "tuple") @Valid @NotNull final Map<String, String> tuple,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.CREATE)
                .andThen(Single.defer(() -> {
                    String user = tuple.get("user");
                    String relation = tuple.get("relation");
                    String object = tuple.get("object");

                    if (user == null || relation == null || object == null) {
                        return Single.just(Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\": \"User, relation, and object are required\"}")
                                .build());
                    }

                    try {
                        return openFGAService.createTuple(storeId, new OpenFGATuple(user, relation, object))
                                .map(t -> Response.status(Response.Status.CREATED).entity(t).build());
                    } catch (FgaInvalidParameterException e) {
                        return Single.just(Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\": \"Invalid tuple parameters: " + e.getMessage() + "\"}")
                                .build());
                    }
                }))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("{storeId}/tuples/upload")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload multiple tuples")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tuples uploaded"),
            @ApiResponse(responseCode = "400", description = "Invalid tuples"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void uploadTuples(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @PathParam("storeId") String storeId,
            @Parameter(name = "tuples") @Valid @NotNull final List<Map<String, String>> tuples,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.CREATE)
                .andThen(Single.defer(() -> {
                    if (tuples.isEmpty()) {
                        return Single.just(Response.status(Response.Status.BAD_REQUEST)
                                .entity("{\"error\": \"No tuples provided\"}")
                                .build());
                    }

                    // Convert maps to OpenFGATuple objects and create them one by one
                    return Single.fromCallable(() -> {
                        try {
                            for (Map<String, String> tupleMap : tuples) {
                                String user = tupleMap.get("user");
                                String relation = tupleMap.get("relation");
                                String object = tupleMap.get("object");

                                if (user == null || relation == null || object == null) {
                                    throw new IllegalArgumentException("Invalid tuple: user, relation, and object are required");
                                }

                                openFGAService.createTuple(storeId, new OpenFGATuple(user, relation, object)).blockingGet();
                            }
                            return Response.ok("{\"status\": \"success\", \"uploaded\": " + tuples.size() + "}").build();
                        } catch (Exception e) {
                            return Response.status(Response.Status.BAD_REQUEST)
                                    .entity("{\"error\": \"Failed to upload tuples: " + e.getMessage() + "\"}")
                                    .build();
                        }
                    });
                }))
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Path("check-permission")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Check permission")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permission check result"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public void checkPermission(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Parameter(name = "permissionRequest") @Valid @NotNull final Map<String, String> permissionRequest,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, domainId, Permission.DOMAIN_SETTINGS, Acl.READ)
                .andThen(Single.fromCallable(() -> {
                    // Mock permission check with random result
                    boolean allowed = new Random().nextBoolean();
                    String result = allowed ? "allowed" : "denied";

                    return Response.ok("{\"result\": \"" + result + "\", \"allowed\": " + allowed + "}").build();
                }))
                .subscribe(response::resume, response::resume);
    }

    // Helper methods
    private static OpenFGAStoreEntity createMockStore(String id, String name, Date timestamp) {
        OpenFGAStoreEntity store = new OpenFGAStoreEntity();
        store.setId(id);
        store.setName(name);
        store.setCreatedAt(timestamp);
        store.setUpdatedAt(timestamp);
        return store;
    }

    private static Map<String, String> createTuple(String user, String relation, String object) {
        Map<String, String> tuple = new HashMap<>();
        tuple.put("user", user);
        tuple.put("relation", relation);
        tuple.put("object", object);
        return tuple;
    }
}