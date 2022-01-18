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
import io.gravitee.am.common.audit.EventType;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.IdentityProviderPluginService;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.management.service.exception.IdentityProviderPluginSchemaNotFoundException;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.gravitee.am.service.model.NewIdentityProvider;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.management.IdentityProviderAuditBuilder;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
    private static final String USERS_INLINE_CONFIG_PATH = "/users";

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
        // do not refactor to identityProviderService.findById(id).flatMap(reporter -> filterSensitiveData(reporter).toMaybe());
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
    public Single<IdentityProvider> create(ReferenceType referenceType, String referenceId, NewIdentityProvider newIdentityProvider, User principal) {
        return identityProviderService.create(referenceType, referenceId, newIdentityProvider, principal)
                .flatMap(this::filterSensitiveData)
                .doOnSuccess(identityProvider1 -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_CREATED).identityProvider(identityProvider1)))
                .doOnError(throwable -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_CREATED).throwable(throwable)));
    }

    @Override
    public Single<IdentityProvider> update(ReferenceType referenceType, String referenceId, String id, UpdateIdentityProvider updateIdentityProvider, User principal, boolean isUpgrader) {
        return identityProviderService.findById(id)
                .switchIfEmpty(Single.error(new IdentityProviderNotFoundException(id)))
                .flatMap(oldIdP -> filterSensitiveData(oldIdP)
                        .flatMap(safeOldIdp -> updateSensitiveData(updateIdentityProvider, oldIdP)
                                .flatMap(idpToUpdate -> identityProviderService.update(referenceType, referenceId, id, idpToUpdate, principal, false))
                                .flatMap(this::filterSensitiveData)
                                .doOnSuccess(identityProvider1 -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_UPDATED).oldValue(safeOldIdp).identityProvider(identityProvider1)))
                                .doOnError(throwable -> auditService.report(AuditBuilder.builder(IdentityProviderAuditBuilder.class).principal(principal).type(EventType.IDENTITY_PROVIDER_UPDATED).throwable(throwable))))
                );
    }

    @Override
    public Completable delete(ReferenceType referenceType, String referenceId, String identityProviderId, User principal) {
        return identityProviderService.delete(referenceType, referenceId, identityProviderId, principal);
    }

    private Single<IdentityProvider> filterSensitiveData(IdentityProvider idp) {
        return identityProviderPluginService.getSchema(idp.getType())
                .switchIfEmpty(Single.error(new IdentityProviderPluginSchemaNotFoundException(idp.getType())))
                .map(schema -> {
                    // Duplicate the object to avoid side effect
                    var filteredEntity = new IdentityProvider(idp);
                    var schemaNode = objectMapper.readTree(schema);
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
                    return filteredEntity;
                });
    }

    private Single<UpdateIdentityProvider> updateSensitiveData(UpdateIdentityProvider updateIdentityProvider, IdentityProvider oldIdentityProvider) {
        return identityProviderPluginService.getSchema(oldIdentityProvider.getType())
                .switchIfEmpty(Single.error(new IdentityProviderPluginSchemaNotFoundException(oldIdentityProvider.getType())))
                .map(schema -> {
                    var updateConfig = objectMapper.readTree(updateIdentityProvider.getConfiguration());
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
        var users = (ArrayNode) idpConfig.at(USERS_INLINE_CONFIG_PATH);
        users.forEach(jsonNode -> ((ObjectNode) jsonNode).put(PASSWORD_INLINE_KEY, SENSITIVE_VALUE));
    }

    private void handleUpdateInlineIdp(JsonNode updateConfig, JsonNode oldConfig) {
        var updatedUsers = (ArrayNode) updateConfig.at(USERS_INLINE_CONFIG_PATH);
        var oldConfigUsers = (ArrayNode) oldConfig.at(USERS_INLINE_CONFIG_PATH);
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
