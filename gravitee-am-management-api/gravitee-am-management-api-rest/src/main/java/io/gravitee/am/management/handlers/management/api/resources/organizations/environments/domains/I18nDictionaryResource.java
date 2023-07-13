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
import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.exception.DictionaryNotFoundException;
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.model.UpdateI18nDictionary;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import java.util.SortedMap;

import static io.gravitee.am.model.Acl.READ;
import static io.gravitee.am.model.Acl.UPDATE;
import static io.gravitee.am.model.Acl.DELETE;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.model.permissions.Permission.DOMAIN_I18N_DICTIONARY;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@Api(tags = {"dictionary"})
public class I18nDictionaryResource extends AbstractDomainResource {

    @Autowired
    private I18nDictionaryService service;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "getI18nDictionary",
            value = "Get a i18n dictionary",
            notes = "User must have the DOMAIN_I18N_DICTIONARY[READ] permission on the specified domain " +
                    "or DOMAIN_I18N_DICTIONARY[READ] permission on the specified environment " +
                    "or DOMAIN_I18N_DICTIONARY[READ] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Get the i18n dictionary", response = I18nDictionary.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void get(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("dictionary") String dictionary,
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domain, DOMAIN_I18N_DICTIONARY, READ)
                .andThen(service.findById(DOMAIN, domain, dictionary)
                                    .switchIfEmpty(Maybe.error(new DictionaryNotFoundException(dictionary))))
                .subscribe(response::resume, response::resume);
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "putI18nDictionary",
            value = "Update a i18n dictionary description",
            notes = "User must have the DOMAIN_I18N_DICTIONARY[UPDATE] permission on the specified domain " +
                    "or DOMAIN_I18N_DICTIONARY[UPDATE] permission on the specified environment " +
                    "or DOMAIN_I18N_DICTIONARY[UPDATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Update the i18n dictionary description", response = I18nDictionary.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("dictionary") String dictionary,
            @Valid @NotNull UpdateI18nDictionary updatedDictionary,
            @Suspended final AsyncResponse response) {
        User principal = getAuthenticatedUser();
        checkAnyPermission(organizationId, environmentId, domain, DOMAIN_I18N_DICTIONARY, UPDATE)
                .andThen(domainService.findById(domain)
                                      .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                                      .flatMapSingle(foundDomain -> service.update(DOMAIN, domain, dictionary, updatedDictionary, principal)))
                .subscribe(response::resume, response::resume);
    }

    @DELETE
    @ApiOperation(
            nickname = "deleteI18nDictionary",
            value = "Delete a i18n dictionary",
            notes = "User must have the DOMAIN_I18N_DICTIONARY[DELETE] permission on the specified domain " +
                    "or DOMAIN_I18N_DICTIONARY[DELETE] permission on the specified environment " +
                    "or DOMAIN_I18N_DICTIONARY[DELETE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Delete a i18n dictionary from a security domain"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void delete(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("dictionary") String dictionary,
            @Suspended final AsyncResponse response) {
        User principal = getAuthenticatedUser();
        checkAnyPermission(organizationId, environmentId, domain, DOMAIN_I18N_DICTIONARY, DELETE)
                .andThen(domainService.findById(domain)
                                      .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                                      .flatMapCompletable(irrelevant -> service.delete(ReferenceType.DOMAIN, domain, dictionary, principal)))
                .subscribe(() -> response.resume(Response.noContent().build()), response::resume);
    }


    @PUT
    @Path("/entries")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            nickname = "replaceI18nDictionaryEntries",
            value = "Update all the entries for a i18n dictionary description",
            notes = "User must have the DOMAIN_I18N_DICTIONARY[UPDATE] permission on the specified domain " +
                    "or DOMAIN_I18N_DICTIONARY[UPDATE] permission on the specified environment " +
                    "or DOMAIN_I18N_DICTIONARY[UPDATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Update the i18n entries for the given dictionary", response = I18nDictionary.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    public void update(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @PathParam("dictionary") String dictionary,
            @Valid @NotNull SortedMap<String, String> entries,
            @Suspended final AsyncResponse response) {
        User principal = getAuthenticatedUser();
        checkAnyPermission(organizationId, environmentId, domain, DOMAIN_I18N_DICTIONARY, UPDATE)
                .andThen(domainService.findById(domain)
                                      .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                                      .flatMapSingle(foundDomain -> service.updateEntries(DOMAIN, domain, dictionary, entries, principal)))
                .subscribe(response::resume, response::resume);
    }
}
