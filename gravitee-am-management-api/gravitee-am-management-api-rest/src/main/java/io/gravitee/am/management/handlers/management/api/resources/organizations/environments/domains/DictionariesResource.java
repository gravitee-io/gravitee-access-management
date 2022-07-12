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

import io.gravitee.am.model.Dictionary;
import io.gravitee.am.service.model.NewDictionary;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"dictionary"})
public class DictionariesResource extends AbstractDomainResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "listDictionaries",
            value = "List all i18n dictionaries supported for a security domain",
            notes = "User must have the DOMAIN_DICTIONARIES[LIST] permission on the specified domain " +
                    "or DOMAIN_DICTIONARIES[LIST] permission on the specified environment " +
                    "or DOMAIN_DICTIONARIES[LIST] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List of i18n dictionaries for a security domain", response = Dictionary.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {

        //checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DICTIONARIES, Acl.LIST);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "createDictionary",
            value = "Create a new i18n dictionary for a supported language for a security domain",
            notes = "User must have the DOMAIN_DICTIONARIES[CREATE] permission on the specified domain " +
                    "or DOMAIN_DICTIONARIES[CREATE] permission on the specified environment " +
                    "or DOMAIN_DICTIONARIES[CREATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Create a new i18n dictionary for a security domain", response = Dictionary.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Valid @NotNull NewDictionary dictionary,
            @Suspended final AsyncResponse response) {

        //checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DICTIONARIES, Acl.CREATE);
    }

    @Path("{dictionary}")
    public DictionaryResource getDictionaryResource() {
        return resourceContext.getResource(DictionaryResource.class);
    }
}
