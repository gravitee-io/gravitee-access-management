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

import io.gravitee.am.management.handlers.automation.mapper.AutomationClientSecretMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationClientSecret;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ApplicationSecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A single client secret of an application.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Client Secrets")
public class ClientSecretResource extends AbstractAutomationResource {

    @Autowired
    private ApplicationSecretService applicationSecretService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationGetClientSecret", summary = "Get a client secret",
            description = "Retrieves a client secret managed by the Automation API by its key, or any secret of the " +
                    "application by id:<uuid>. The value is never returned.")
    @ApiResponse(responseCode = "200", description = "The client secret, without its value",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationClientSecret.class),
                    examples = @ExampleObject(name = "CI secret", value = ClientSecretsResource.READ_EXAMPLE)))
    @ApiResponse(responseCode = "404", description = "Domain, application or client secret not found, or not managed by the Automation API")
    public void get(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("applicationKey") String applicationKey,
            @PathParam("secretKey") String secretKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef applicationRef = AutomationRef.parse(applicationKey);
        final AutomationRef secretRef = AutomationRef.parse(secretKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION_OPENID, Acl.READ)
                .andThen(resolver.resolveDomain(environmentId, AutomationRef.parse(domainKey)))
                .flatMap(domain -> resolver.resolveApplication(domain, applicationRef))
                .flatMap(application -> resolver.resolveClientSecret(application, secretRef))
                .map(AutomationClientSecretMapper::toAutomationClientSecret)
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @Operation(operationId = "automationDeleteClientSecret", summary = "Delete a client secret",
            description = "Deletes a client secret managed by the Automation API by its key, or any secret of the " +
                    "application by id:<uuid>. The gateway rejects the secret once it has synced the change. Deleting the last secret " +
                    "of an application is allowed. Deleting a secret that does not exist also returns 204.")
    @ApiResponse(responseCode = "204", description = "Client secret successfully deleted")
    @ApiResponse(responseCode = "409", description = "Concurrent modification of the application; send the same request again")
    public void delete(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @PathParam("applicationKey") String applicationKey,
            @PathParam("secretKey") String secretKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef applicationRef = AutomationRef.parse(applicationKey);
        final AutomationRef secretRef = AutomationRef.parse(secretKey);
        checkAnyPermission(principal, organizationId, environmentId, Permission.APPLICATION_OPENID, Acl.DELETE)
                .andThen(resolver.resolveDomainMaybe(environmentId, AutomationRef.parse(domainKey)))
                .flatMapCompletable(domain -> resolver.resolveApplicationMaybe(domain, applicationRef)
                        .flatMapCompletable(application -> resolver.resolveClientSecretMaybe(application, secretRef)
                                .flatMapCompletable(secret -> applicationSecretService.remove(domain, application, secret.getId(), principal))))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }
}
