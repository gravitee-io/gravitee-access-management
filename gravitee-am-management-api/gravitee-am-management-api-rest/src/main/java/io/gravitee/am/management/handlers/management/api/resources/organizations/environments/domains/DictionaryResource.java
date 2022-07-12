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
import io.gravitee.am.service.model.UpdateDictionary;
import io.gravitee.common.http.MediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.TreeMap;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"dictionary"})
public class DictionaryResource extends AbstractDomainResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "getDictionary",
            value = "Get a i18n dictionary",
            notes = "User must have the DOMAIN_DICTIONARIES[READ] permission on the specified domain " +
                    "or DOMAIN_DICTIONARIES[READ] permission on the specified environment " +
                    "or DOMAIN_DICTIONARIES[READ] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Get the i18n dictionary", response = Dictionary.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("dictionary") String dictionary,
            @Suspended final AsyncResponse response) {

        //checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DICTIONARIES, Acl.READ);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "putDictionary",
            value = "Update a i18n dictionary description",
            notes = "User must have the DOMAIN_DICTIONARIES[UPDATE] permission on the specified domain " +
                    "or DOMAIN_DICTIONARIES[UPDATE] permission on the specified environment " +
                    "or DOMAIN_DICTIONARIES[UPDATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Update the i18n dictionary description", response = Dictionary.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("dictionary") String dictionary,
            @Valid @NotNull UpdateDictionary updatedDictionary,
            @Suspended final AsyncResponse response) {

        //checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DICTIONARIES, Acl.READ);
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "deleteDictionary",
            value = "Delete a i18n dictionary",
            notes = "User must have the DOMAIN_DICTIONARIES[DELETE] permission on the specified domain " +
                    "or DOMAIN_DICTIONARIES[DELETE] permission on the specified environment " +
                    "or DOMAIN_DICTIONARIES[DELETE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Delete a i18n dictionary from a security domain", response = Dictionary.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("dictionary") String dictionary,
            @Suspended final AsyncResponse response) {

        //checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DICTIONARIES, Acl.READ);
    }


    @PUT
    @Path("/entries")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "replaceDictionaryEntries",
            value = "Update all the entries for a i18n dictionary description",
            notes = "User must have the DOMAIN_DICTIONARIES[UPDATE] permission on the specified domain " +
                    "or DOMAIN_DICTIONARIES[UPDATE] permission on the specified environment " +
                    "or DOMAIN_DICTIONARIES[UPDATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Update the i18n entries for the given dictionary", response = Dictionary.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("dictionary") String dictionary,
            @Valid @NotNull TreeMap<String, String> entries,
            @Suspended final AsyncResponse response) {

        //checkAnyPermission(organizationId, environmentId, domain, Permission.DOMAIN_DICTIONARIES, Acl.READ);
    }
}
