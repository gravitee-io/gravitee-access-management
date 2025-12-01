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
package io.gravitee.am.management.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.IdentityProviderPluginService;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.management.service.exception.IdentityProviderPluginSchemaNotFoundException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.model.AssignPasswordPolicy;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.IdentityProviderAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static io.gravitee.am.management.service.impl.utils.JsonNodeValidator.validateConfiguration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class IdentityProviderServiceProxyImpl extends AbstractSensitiveProxy implements IdentityProviderServiceProxy {

    private static final String KERBEROS_AM_IDP = "kerberos-am-idp";
    private static final String INLINE_AM_IDP = "inline-am-idp";

    private static final String USERNAME_INLINE_KEY = "username";
    private static final String PASSWORD_INLINE_KEY = "password";

    private static final Collector<JsonNode, ?, Map<String, String>> JSON_NODE_MAP_COLLECTOR =
            Collectors.toMap(json -> json.get(USERNAME_INLINE_KEY).asText(), json -> json.get(PASSWORD_INLINE_KEY).asText());
    private static final String USERS_INLINE_CONFIG_FIELD = "users";

    @Autowired
    private IdentityProviderPluginService identityProviderPluginService;

    @Autowired
    private io.gravitee.am.service.IdentityProviderService identityProviderService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Flowable<IdentityProvider> findAll() {
        return identityProviderService.findAll().flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Single<IdentityProvider> findById(ReferenceType referenceType, String referenceId, String id) {
        return identityProviderService.findById(referenceType, referenceId, id).flatMap(this::filterSensitiveData);
    }

    @Override
    public Maybe<IdentityProvider> findById(String id) {
        // As the IDP maybe missing, using the flatMapSingle will rise an Exception
        return identityProviderService.findById(id).flatMap(idp -> this.filterSensitiveData(idp).toMaybe());
    }

    @Override
    public Flowable<IdentityProvider> findAll(ReferenceType referenceType, String referenceId) {
        return identityProviderService.findAll(referenceType, referenceId).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Flowable<IdentityProvider> findAll(ReferenceType referenceType) {
        return identityProviderService.findAll(referenceType).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Flowable<IdentityProvider> findByDomain(String domain) {
        return identityProviderService.findByDomain(domain).flatMapSingle(this::filterSensitiveData);
    }

    @Override
    public Single<IdentityProvider> create(ReferenceType referenceType, String referenceId, NewIdentityProvider newIdentityProvider, User principal, boolean system) {
        return identityProviderService.create(referenceType, referenceId, newIdentityProvider, principal, system)
                .flatMap(this::filterSensitiveData)
                .doOnSuccess(identityProvider1 -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_CREATED).reference(new Reference(referenceType, referenceId)).identityProvider(identityProvider1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_CREATED).reference(new Reference(referenceType, referenceId)).throwable(throwable)));
    }

    @Override
    public Single<IdentityProvider> update(ReferenceType referenceType, String referenceId, String id, UpdateIdentityProvider updateIdentityProvider, User principal, boolean isUpgrader) {

        Supplier<IdentityProviderAuditBuilder> audit = () ->
                AuditBuilder.builder(IdentityProviderAuditBuilder.class)
                        .principal(principal)
                        .type(EventType.IDENTITY_PROVIDER_UPDATED)
                        .reference(new Reference(referenceType, referenceId));

        return identityProviderService.findById(id)
                .switchIfEmpty(Single.error(new IdentityProviderNotFoundException(id)))
                .doOnError(err -> auditService.report(audit.get().throwable(err)))
                .flatMap(oldIdP -> filterSensitiveData(oldIdP)
                        .doOnError(err -> auditService.report(audit.get().throwable(err)))
                        .flatMap(safeOldIdp -> updateSensitiveData(updateIdentityProvider, oldIdP)
                                .flatMap(idpToUpdate -> identityProviderService.update(referenceType, referenceId, id, idpToUpdate, principal, isUpgrader))
                                .flatMap(this::filterSensitiveData)
                                .doOnSuccess(updated -> auditService.report(audit.get().oldValue(safeOldIdp).identityProvider(updated)))
                                .doOnError(err -> auditService.report(audit.get().oldValue(safeOldIdp).throwable(err)))
                        )
                );
    }


    @Override
    public Single<IdentityProvider> create(Domain domain, NewIdentityProvider newIdentityProvider, User principal, boolean system) {
        return identityProviderService.create(domain, newIdentityProvider, principal, system)                .flatMap(this::filterSensitiveData)
                .doOnSuccess(identityProvider1 -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_CREATED).identityProvider(identityProvider1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_CREATED).reference(domain.asReference()).throwable(throwable)));
    }

    @Override
    public Single<IdentityProvider> assignDataPlane(IdentityProvider identityProvider, String dataPlaneId) {
        return identityProviderService.assignDataPlane(identityProvider, dataPlaneId);
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String identityProviderId, User principal) {
        return identityProviderService.delete(referenceType, referenceId, identityProviderId, principal);
    }

    @Override
    public Flowable<IdentityProvider> findWithPasswordPolicy(ReferenceType referenceType, String referenceId, String passwordPolicy) {
        return identityProviderService.findWithPasswordPolicy(referenceType, referenceId, passwordPolicy);
    }

    @Override
    public Single<IdentityProvider> updatePasswordPolicy(String domain, String id, AssignPasswordPolicy assignPasswordPolicy) {
        return identityProviderService.updatePasswordPolicy(domain, id, assignPasswordPolicy);
    }

    @Override
    public Flowable<IdentityProvider> findByCertificate(Reference reference, String id) {
        return identityProviderService.findByCertificate(reference, id);
    }

    private Single<IdentityProvider> filterSensitiveData(IdentityProvider idp) {
        return identityProviderPluginService.getSchema(idp.getType())
                .map(Optional::ofNullable)
                .defaultIfEmpty(Optional.empty())
                .map(schema -> {
                    // Duplicate the object to avoid side effect
                    var filteredEntity = new IdentityProvider(idp);
                    if (schema.isPresent()) {
                        var schemaNode = objectMapper.readTree(schema.get());
                        var configurationNode = objectMapper.readTree(filteredEntity.getConfiguration());
                        if (KERBEROS_AM_IDP.equals(filteredEntity.getType())) {
                            this.filterNestedSensitiveData(schemaNode, configurationNode,
                                    "/properties/ldapConfig",
                                    "/ldapConfig"
                            );
                        }
                        // We enforce sensitive data filtering on Inline User Passwords
                        if (INLINE_AM_IDP.equals(filteredEntity.getType())) {
                            filterSensitiveInlineIdpData(configurationNode);
                        }
                        super.filterSensitiveData(schemaNode, configurationNode, filteredEntity::setConfiguration);
                    } else {
                        // not schema for the IDP, remove all the configuration to avoid sensitive data leak
                        // this case may happen when the plugin zip file has been removed from the plugins directory
                        // (set empty object to avoid NullPointer on the UI)
                        filteredEntity.setConfiguration(DEFAULT_SCHEMA_CONFIG);
                    }
                    return filteredEntity;
                });
    }

    private Single<UpdateIdentityProvider> updateSensitiveData(UpdateIdentityProvider updateIdentityProvider, IdentityProvider oldIdentityProvider) {
        return identityProviderPluginService.getSchema(oldIdentityProvider.getType())
                .switchIfEmpty(Single.error(new IdentityProviderPluginSchemaNotFoundException(oldIdentityProvider.getType())))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateIdentityProvider.getConfiguration());
                    validateConfiguration(updateConfig);
                    var oldConfig = objectMapper.readTree(oldIdentityProvider.getConfiguration());
                    var schemaConfig = objectMapper.readTree(schema);
                    if (KERBEROS_AM_IDP.equals(oldIdentityProvider.getType())) {
                        this.updateNestedSensitiveData(updateConfig, oldConfig, schemaConfig, "/properties/ldapConfig", "/ldapConfig");
                    }
                    // We enforce sensitive data filtering on Inline User Passwords
                    if (INLINE_AM_IDP.equals(oldIdentityProvider.getType())) {
                        handleUpdateInlineIdp(updateConfig, oldConfig);
                    }
                    super.updateSensitiveData(updateConfig, oldConfig, schemaConfig, updateIdentityProvider::setConfiguration);
                    return updateIdentityProvider;
                });
    }

    private void filterSensitiveInlineIdpData(JsonNode idpConfig) {
        if (idpConfig.has(USERS_INLINE_CONFIG_FIELD)) {
            var users = (ArrayNode) idpConfig.get(USERS_INLINE_CONFIG_FIELD);
            users.forEach(jsonNode -> ((ObjectNode) jsonNode).put(PASSWORD_INLINE_KEY, SENSITIVE_VALUE));
        }
    }

    private void handleUpdateInlineIdp(JsonNode updateConfig, JsonNode oldConfig) {
        var updatedUsers = (ArrayNode) updateConfig.get(USERS_INLINE_CONFIG_FIELD);
        var oldConfigUsers = (ArrayNode) oldConfig.get(USERS_INLINE_CONFIG_FIELD);
        if (!areUserRemoved(updatedUsers) && !noPriorUsers(oldConfigUsers)) {
            var oldUserList = new ArrayList<JsonNode>(oldConfig.size());
            oldConfigUsers.forEach(oldUserList::add);
            var oldUsersMap = oldUserList.stream().collect(JSON_NODE_MAP_COLLECTOR);
            updatedUsers.forEach(json -> {
                var username = json.get(USERNAME_INLINE_KEY).asText();
                var password = json.get(PASSWORD_INLINE_KEY).asText();
                var oldPassword = oldUsersMap.get(username);
                //If no oldPassword --> we added a new user, so we leave the value as it is
                if (oldPassword != null && SENSITIVE_VALUE_PATTERN.matcher(password).matches()) {
                    ((ObjectNode) json).put(PASSWORD_INLINE_KEY, oldPassword);
                }
            });
        }
    }

    private boolean noPriorUsers(ArrayNode oldConfigArrayNode) {
        return oldConfigArrayNode == null || oldConfigArrayNode.isEmpty();
    }

    private boolean areUserRemoved(ArrayNode updateArrayNode) {
        return updateArrayNode == null || updateArrayNode.isEmpty();
    }
}
