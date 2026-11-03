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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.automation.mapper.AutomationClientSecretMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationClientSecret;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationSecretService;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;

/**
 * Client secrets of an application.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Client Secrets")
public class ClientSecretsResource extends AbstractAutomationResource {

    /** A secret as returned: the value is write-only, so the request example cannot be reused. */
    static final String READ_EXAMPLE = "{\"key\":\"ci-2026-10\",\"expiresAt\":\"2027-04-27T09:30:00.000Z\"}";

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ApplicationSecretService applicationSecretService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationListClientSecrets", summary = "List an application's client secrets",
            description = "Returns the client secrets managed by the Automation API, sorted by key. Secrets created " +
                    "outside the Automation API (for example in the Console) are not returned; they can still be " +
                    "read and deleted by id:<uuid>. Secret values are never returned.")
    @ApiResponse(responseCode = "200", description = "List of client secrets",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AutomationClientSecret.class)),
                    examples = @ExampleObject(name = "ClientSecretList", value = "[" + READ_EXAMPLE + "]")))
    @ApiResponse(responseCode = "404", description = "Domain or application not found, or not managed by the Automation API")
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("applicationKey") String applicationKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef applicationRef = AutomationRef.parse(applicationKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION_OPENID, Acl.LIST)
                .andThen(resolver.resolveDomain(environmentId, AutomationRef.parse(domainKey)))
                .flatMap(domain -> resolver.resolveApplication(domain, applicationRef))
                .map(application -> application.getSecrets().stream()
                        .filter(secret -> secret.isManagedBy(ManagedBy.AUTOMATION_API))
                        .sorted(Comparator.comparing(ClientSecret::getAutomationKey))
                        .map(AutomationClientSecretMapper::toAutomationClientSecret)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateClientSecret",
            summary = "Create or keep a client secret",
            description = "Idempotent create. Uses the key field in the body to identify the secret within the " +
                    "application. On first apply the secret is created with the given value, and the gateway " +
                    "accepts it once it has synced the change. A secret is immutable: applying it again with the same value changes " +
                    "nothing, a different value is rejected with 400. To rotate a secret, create one with a new " +
                    "key, move the clients to it, then delete the old one. The value is never returned. " +
                    "The number of secrets of an application is limited (applications.secretsMax, 10 by default), " +
                    "secrets created in the Console included. The key is at most 64 characters. With BCrypt hashing " +
                    "the value is at most 72 bytes, and an unhashed value at most 374 characters. An application using client_secret_jwt stores its secrets unhashed. " +
                    "A secret created outside the Automation API can be addressed with key id:<uuid>: it is never " +
                    "created that way, only checked against the given value. " +
                    "When dryRun is true, the endpoint validates the payload without persisting; the returned " +
                    "secret carries a dryRunErrors list (absent on success, populated with validation errors " +
                    "otherwise).")
    @ApiResponse(responseCode = "200", description = "The created or unchanged client secret, without its value. When " +
            "dryRun is true the secret includes a dryRunErrors field with any validation errors.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationClientSecret.class),
                    examples = @ExampleObject(name = "CI secret", value = READ_EXAMPLE)))
    @ApiResponse(responseCode = "400", description = "Invalid request: a value too short or too long, a different " +
            "value for an existing secret, a key conflict, or too many secrets")
    @ApiResponse(responseCode = "404", description = "Domain or application not found, or not managed by the " +
            "Automation API; or an id:<uuid> secret that does not exist")
    @ApiResponse(responseCode = "409", description = "Concurrent modification of the application; send the same request again")
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("applicationKey") String applicationKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Desired client secret.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutomationClientSecret.class),
                            examples = @ExampleObject(name = "CI secret",
                                    description = "A secret for a CI pipeline, generated from 32 random bytes",
                                    value = "{\"key\":\"ci-2026-10\",\"value\":\"q8Zk3vN2xT7pR1mW9yB4cD6fH0jL5sA8eG2iK7oU3wQ\"}")))
            @Valid @NotNull AutomationClientSecret definition,
            @Parameter(description = "When true, validates the payload without persisting. The returned secret includes a dryRunErrors field.")
            @QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef applicationRef = AutomationRef.parse(applicationKey);
        final AutomationRef secretRef = AutomationRef.parse(definition.getAutomationKey());
        final String rawSecret = definition.getValue();
        definition.setValue(null);

        // The ACL is chosen from a non-erroring lookup so the permission gate runs before any existence is revealed
        Single<AutomationClientSecret> result = resolver.resolveDomainMaybe(environmentId, domainRef)
                .flatMap(domain -> resolver.resolveApplicationMaybe(domain, applicationRef))
                .flatMap(application -> resolver.resolveClientSecretMaybe(application, secretRef))
                .isEmpty()
                .flatMap(missing -> checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION_OPENID,
                        missing ? Acl.CREATE : Acl.UPDATE)
                        .andThen(resolver.resolveDomain(environmentId, domainRef))
                        .flatMap(domain -> resolver.resolveApplication(domain, applicationRef)
                                .flatMap(application -> apply(domain, application, secretRef, rawSecret, principal, dryRun))))
                .map(AutomationClientSecretMapper::toAutomationClientSecret);
        if (dryRun) {
            result = result.onErrorResumeNext(ex -> Single.just(withErrors(definition, ex)));
        }
        result.subscribe(response::resume, response::resume);
    }

    private Single<ClientSecret> apply(Domain domain, Application application, AutomationRef secretRef, String rawSecret,
            User principal, boolean dryRun) {
        ClientSecret desired = new ClientSecret();
        if (secretRef instanceof AutomationRef.KeyRef(String key)) {
            desired.setId(AutomationIds.clientSecretId(application.getId(), key));
            desired.setName(key);
            desired.setAutomationKey(key);
            desired.setManagedBy(ManagedBy.AUTOMATION_API);
            boolean conflict = application.getSecrets().stream()
                    .anyMatch(secret -> desired.getId().equals(secret.getId())
                            && !(secret.isManagedBy(ManagedBy.AUTOMATION_API) && key.equals(secret.getAutomationKey())));
            if (conflict) {
                return Single.error(new InvalidParameterException("Client secret key '" + key + "' conflicts with an existing secret"));
            }
        } else {
            // an 'id:' body addresses a preexisting secret directly (no create)
            desired.setId(((AutomationRef.IdRef) secretRef).id());
        }
        return dryRun
                ? applicationSecretService.validateApply(domain, application, desired, rawSecret)
                : applicationSecretService.apply(domain, application, desired, rawSecret, principal);
    }

    @Path("/{secretKey}")
    public ClientSecretResource getClientSecretResource() {
        return resourceContext.getResource(ClientSecretResource.class);
    }
}
