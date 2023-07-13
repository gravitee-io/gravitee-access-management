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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.service.AlertTriggerService;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.repository.management.api.search.AlertTriggerCriteria;
import io.gravitee.am.service.model.PatchAlertTrigger;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Flowable;
import io.swagger.annotations.*;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;

import java.util.Comparator;
import java.util.List;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = "alerts")
public class AlertTriggersResource extends AbstractResource {

    @Inject
    private AlertTriggerService alertTriggerService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "List alert alert triggers",
            notes = "List all the alert triggers of the domain accessible to the current user. " +
                    "User must have DOMAIN_ALERT[LIST] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List alert triggers for current user", response = AlertTrigger.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @Suspended final AsyncResponse response) {

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN_ALERT, Acl.LIST)
                .andThen(alertTriggerService.findByDomainAndCriteria(domainId, new AlertTriggerCriteria()))
                .sorted(Comparator.comparingInt(o -> o.getType().getOrder()))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Update multiple alert triggers",
            notes = "Update multiple alert triggers in the same time" +
                    "User must have DOMAIN_ALERT[UPDATE] permission on the specified domain, environment or organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Alert triggers successfully updated", response = AlertTrigger.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domainId,
            @ApiParam(name = "alertTriggers", required = true) @Valid @NotNull List<PatchAlertTrigger> patchAlertTriggers,
            @Suspended final AsyncResponse response) {

        final User authenticatedUser = this.getAuthenticatedUser();

        checkAnyPermission(organizationId, environmentId, Permission.DOMAIN_ALERT, Acl.UPDATE)
                .andThen(Flowable.fromIterable(patchAlertTriggers))
                .flatMapSingle(patchAlertTrigger -> alertTriggerService.createOrUpdate(ReferenceType.DOMAIN, domainId, patchAlertTrigger, authenticatedUser))
                .toList()
                .subscribe(response::resume, response::resume);
    }
}
