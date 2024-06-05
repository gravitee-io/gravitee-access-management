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
import io.gravitee.am.service.exception.DomainNotFoundException;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.model.NewDictionary;
import io.gravitee.common.http.MediaType;
import io.reactivex.rxjava3.core.Maybe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

import static io.gravitee.am.model.Acl.CREATE;
import static io.gravitee.am.model.Acl.LIST;
import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.gravitee.am.model.permissions.Permission.DOMAIN_I18N_DICTIONARY;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@Tag(name = "dictionary")
public class I18nDictionariesResource extends AbstractDomainResource {

    @Autowired
    private I18nDictionaryService dictionaryService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "listI18nDictionaries",
            summary = "List all i18n dictionaries supported for a security domain",
            description = "User must have the DOMAIN_I18N_DICTIONARY[LIST] permission on the specified domain " +
                    "or DOMAIN_I18N_DICTIONARY[LIST] permission on the specified environment " +
                    "or DOMAIN_I18N_DICTIONARY[LIST] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of i18n dictionaries for a security domain",
                    content = @Content(mediaType =  "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = I18nDictionary.class)))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void list(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Suspended final AsyncResponse response) {
        checkAnyPermission(organizationId, environmentId, domain, DOMAIN_I18N_DICTIONARY, LIST)
                .andThen(domainService.findById(domain)
                                      .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                                      .flatMapPublisher(irrelevant -> dictionaryService.findAll(DOMAIN, domain)))
                .toList()
                .subscribe(response::resume, response::resume);
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "createI18nDictionary",
            summary = "Create a new i18n dictionary for a supported language for a security domain",
            description = "User must have the DOMAIN_I18N_DICTIONARY[CREATE] permission on the specified domain " +
                    "or DOMAIN_I18N_DICTIONARY[CREATE] permission on the specified environment " +
                    "or DOMAIN_I18N_DICTIONARY[CREATE] permission on the specified organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Create a new i18n dictionary for a security domain",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = I18nDictionary.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error")})
    public void create(
            @PathParam("organizationId") String organizationId,
            @PathParam("environmentId") String environmentId,
            @PathParam("domain") String domain,
            @Valid @NotNull NewDictionary dictionary,
            @Suspended final AsyncResponse response) {
        final User authenticatedUser = getAuthenticatedUser();
        checkAnyPermission(organizationId, environmentId, domain, DOMAIN_I18N_DICTIONARY, CREATE)
                .andThen(domainService.findById(domain)
                                      .switchIfEmpty(Maybe.error(new DomainNotFoundException(domain)))
                                      .flatMapSingle(irrelevant -> dictionaryService
                                              .create(DOMAIN, domain, dictionary, authenticatedUser)
                                              .map(i18nDictionary -> Response
                                                      .created(URI.create("/organizations/" + organizationId + "/environments/" + environmentId + "/domains/" + domain + "/dictionaries/" + i18nDictionary.getId()))
                                                      .entity(i18nDictionary)
                                                      .build())))
                .subscribe(response::resume, response::resume);
    }

    @Path("{dictionary}")
    public I18nDictionaryResource getDictionaryResource() {
        return resourceContext.getResource(I18nDictionaryResource.class);
    }
}
