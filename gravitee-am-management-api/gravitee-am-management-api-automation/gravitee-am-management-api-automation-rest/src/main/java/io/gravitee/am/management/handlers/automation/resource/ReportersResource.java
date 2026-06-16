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
import io.gravitee.am.management.handlers.automation.mapper.AutomationReporterMapper;
import io.gravitee.am.management.handlers.automation.model.AutomationReporter;
import io.gravitee.am.management.service.ReporterPluginService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.AutomationNewReporter;
import io.reactivex.rxjava3.core.Single;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

/**
 * Reporters managed individually under a domain.
 *
 * @author GraviteeSource Team
 */
@Tag(name = "Reporters")
public class ReportersResource extends AbstractAutomationResource {

    @Context
    private ResourceContext resourceContext;

    @Autowired
    private ReporterService reporterService;

    @Autowired
    private ReporterPluginService reporterPluginService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationListReporters", summary = "List a domain's reporters",
            description = "Returns all reporters managed by the Automation API under the domain. Reporters created " +
                    "outside the Automation API are not returned.")
    @ApiResponse(responseCode = "200", description = "List of reporters",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AutomationReporter.class))))
    @ApiResponse(responseCode = "404", description = "Domain not found, or not managed by the Automation API")
    public void list(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_REPORTER, Acl.LIST)
                .andThen(resolver.resolveDomain(environmentId, AutomationRef.parse(domainKey)))
                .flatMap(domain -> reporterService.findByReference(Reference.domain(domain.getId()))
                        .filter(reporter -> reporter.isManagedBy(ManagedBy.AUTOMATION_API))
                        .sorted((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(
                                nullToEmpty(o1.getAutomationKey()), nullToEmpty(o2.getAutomationKey())))
                        .map(AutomationReporterMapper::toAutomationReporter)
                        .toList())
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "automationCreateOrUpdateReporter",
            summary = "Create or update a reporter",
            description = "Idempotent create-or-update. Uses the key field in the body to identify the reporter " +
                    "within the domain. On first apply the reporter is created; subsequent applies update it. The " +
                    "system flag is immutable; changing it requires deleting and recreating the reporter.")
    @ApiResponse(responseCode = "200", description = "The created or updated reporter",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AutomationReporter.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request: a key conflict, a missing required field " +
            "(name, type, or configuration) for a non-system reporter, an attempt to change the immutable system " +
            "flag, or a second system reporter for the domain")
    @ApiResponse(responseCode = "404", description = "Domain not found, or not managed by the Automation API")
    public void createOrUpdate(
            @PathParam("orgId") String organizationId,
            @PathParam("envId") String environmentId,
            @PathParam("domainKey") String domainKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Desired state of the reporter. For a system reporter, supply only system: true " +
                            "and key.",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AutomationReporter.class),
                            examples = {
                                    @ExampleObject(name = "Reporter", description = "A fully specified reporter",
                                            value = "{\"key\":\"audit-kafka\",\"name\":\"Audit events to Kafka\"," +
                                                    "\"type\":\"reporter-am-kafka\",\"enabled\":true," +
                                                    "\"configuration\":\"{\\\"bootstrapServers\\\":\\\"kafka:9092\\\",\\\"topic\\\":\\\"audit\\\"}\"}"),
                                    @ExampleObject(name = "System reporter", description = "The domain's system reporter; only system and key are needed",
                                            value = "{\"key\":\"default\",\"system\":true}")
                            }))
            @Valid @NotNull AutomationReporter definition,
            @Suspended final AsyncResponse response) {

        final var principal = getAuthenticatedUser();
        final AutomationRef domainRef = AutomationRef.parse(domainKey);
        final AutomationRef reporterRef = AutomationRef.parse(definition.getAutomationKey());
        final String key = reporterRef.raw();

        // An 'id:' body addresses a preexisting reporter directly (update-only)
        if (reporterRef instanceof AutomationRef.IdRef) {
            checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_REPORTER, Acl.UPDATE)
                    .andThen(resolver.resolveDomain(environmentId, domainRef))
                    .flatMap(domain -> resolver.resolveReporter(domain, reporterRef)
                            .flatMap(existing -> updateExisting(domain, existing, definition, key, principal)))
                    .subscribe(response::resume, response::resume);
            return;
        }

        final String domainId = AutomationIds.domainId(environmentId, domainRef);
        final Reference reference = Reference.domain(domainId);
        reporterService.findByReference(reference).toList().flatMap(allExisting -> {
            Optional<Reporter> match = allExisting.stream()
                    .filter(reporter -> reporter.isManagedBy(ManagedBy.AUTOMATION_API))
                    .filter(reporter -> key.equals(reporter.getAutomationKey()))
                    .findFirst();
            // The ACL is chosen from a non-erroring lookup so the permission gate runs before any
            // existence is revealed (domain 404 / conflict 400).
            Acl requiredAcl = match.isPresent() ? Acl.UPDATE : Acl.CREATE;
            return checkAnyPermission(principal, organizationId, environmentId, Permission.DOMAIN_REPORTER, requiredAcl)
                    .andThen(resolver.resolveDomain(environmentId, domainRef))
                    .flatMap(domain -> match.isPresent()
                            ? updateExisting(domain, match.get(), definition, key, principal)
                            : createNew(reference, domain, allExisting, definition, key, principal));
        })
                .subscribe(response::resume, response::resume);
    }

    private Single<AutomationReporter> updateExisting(Domain domain, Reporter existing,
            AutomationReporter definition, String key, User principal) {
        // 'system' is an immutable identity attribute; reject a change.
        if (definition.isSystem() != existing.isSystem()) {
            return Single.error(new InvalidParameterException(
                    "The 'system' flag is immutable for an existing reporter '" + key
                            + "'; delete and recreate it to change it"));
        }
        if (existing.isSystem()) {
            // re-PUT is an idempotent no-op
            return Single.just(AutomationReporterMapper.toAutomationReporter(existing));
        }
        // 'type' is an immutable identity attribute; reject a change early
        if (!isBlank(definition.getType()) && !definition.getType().equals(existing.getType())) {
            return Single.error(new InvalidParameterException(
                    "The 'type' is immutable for an existing reporter '" + key
                            + "'; delete and recreate it to change it"));
        }
        Single<AutomationReporter> rejection = rejectIfMissingReporterFields(definition, key);
        if (rejection != null) {
            return rejection;
        }
        return reporterPluginService.checkPluginDeployment(definition.getType())
                .andThen(reporterService.update(Reference.domain(domain.getId()), existing.getId(),
                        AutomationReporterMapper.toUpdateReporter(definition), principal, false))
                .map(AutomationReporterMapper::toAutomationReporter);
    }

    private Single<AutomationReporter> createNew(Reference reference, Domain domain, List<Reporter> allExisting,
            AutomationReporter definition, String key, User principal) {
        final String reporterId = AutomationIds.reporterId(domain.getId(), key);
        Optional<Reporter> occupant = allExisting.stream()
                .filter(reporter -> reporterId.equals(reporter.getId()))
                .findFirst();
        if (occupant.isPresent()) {
            return Single.error(new InvalidParameterException(
                    "Reporter key '" + key + "' conflicts with an existing reporter"));
        }
        if (definition.isSystem() && allExisting.stream()
                .anyMatch(reporter -> reporter.isManagedBy(ManagedBy.AUTOMATION_API) && reporter.isSystem())) {
            return Single.error(new InvalidParameterException(
                    "The domain already has a system reporter"));
        }
        if (definition.isSystem()) {
            return reporterService.createSystem(reference, reporterId, key, principal)
                    .map(AutomationReporterMapper::toAutomationReporter);
        }
        Single<AutomationReporter> rejection = rejectIfMissingReporterFields(definition, key);
        if (rejection != null) {
            return rejection;
        }
        AutomationNewReporter newReporter = AutomationReporterMapper.toNewReporter(definition);
        newReporter.setId(reporterId);
        return reporterPluginService.checkPluginDeployment(definition.getType())
                .andThen(Single.defer(() -> reporterService.create(reference, newReporter, principal, false)))
                .map(AutomationReporterMapper::toAutomationReporter);
    }

    private static Single<AutomationReporter> rejectIfMissingReporterFields(AutomationReporter definition, String key) {
        if (isBlank(definition.getName())) {
            return Single.error(new InvalidParameterException(
                    "Field 'name' is required for a non-system reporter '" + key + "'"));
        }
        if (isBlank(definition.getType())) {
            return Single.error(new InvalidParameterException(
                    "Field 'type' is required for a non-system reporter '" + key + "'"));
        }
        if (isBlank(definition.getConfiguration())) {
            return Single.error(new InvalidParameterException(
                    "Field 'configuration' is required for a non-system reporter '" + key + "'"));
        }
        return null;
    }

    @Path("/{reporterKey}")
    public ReporterResource getReporterResource() {
        return resourceContext.getResource(ReporterResource.class);
    }
}
