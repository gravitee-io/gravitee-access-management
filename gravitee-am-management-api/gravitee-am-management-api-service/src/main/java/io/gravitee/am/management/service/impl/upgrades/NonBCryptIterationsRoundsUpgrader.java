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
package io.gravitee.am.management.service.impl.upgrades;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.model.UpdateIdentityProvider;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.DOMAIN_IDP_UPGRADER;
import static java.util.Optional.ofNullable;

@Component
public class NonBCryptIterationsRoundsUpgrader extends SystemTaskUpgrader {
    private static final Logger log = LoggerFactory.getLogger(NonBCryptIterationsRoundsUpgrader.class);

    private static final String TASK_ID = "non_bcrypt_iterations_rounds_remove_migration";

    @Autowired
    private IdentityProviderService idpService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String previousOperationId) {
        return updateSystemTask(task, (SystemTaskStatus.ONGOING), previousOperationId)
                .flatMap(updatedTask -> {
                    if (updatedTask.getOperationId().equals(instanceOperationId)) {
                        return migrate(updatedTask)
                                .toSingleDefault(true)
                                .onErrorResumeNext(err -> {
                                    log.error("Unable to upgrade non-BCrypt password encoders (task: {}): {}", TASK_ID, err.getMessage());
                                    return Single.just(false);
                                });
                    } else {
                        return Single.error(new IllegalStateException("Task " + getTaskId() + " already processed by another instance : trigger a retry"));
                    }
                });
    }

    private Completable migrate(SystemTask task) {
        return idpService.findAll()
                .map(idp -> new IdpData(idp, objectMapper))
                .filter(idp -> !idp.cfg.hasValue("passwordEncoder", "BCrypt"))
                .flatMapSingle(this::update)
                .ignoreElements()
                .doOnError(err -> updateSystemTask(task, (SystemTaskStatus.FAILURE), task.getOperationId()).subscribe())
                .andThen(updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId()).ignoreElement());
    }

    @SneakyThrows
    private Single<IdentityProvider> update(IdpData idpData) {
        UpdateIdentityProvider updateObject = createUpdateObject(idpData);
        return idpService
                .update(idpData.idp.getReferenceType(), idpData.idp.getReferenceId(), idpData.idp.getId(), updateObject, null, true)
                .doOnSuccess(updated -> log.info("Removed passwordEncoderOptions for idp={} as a migration process.", updated.getId()));
    }

    private UpdateIdentityProvider createUpdateObject(IdpData idpData) {
        var idp = idpData.idp;
        idpData.cfg.removeProperty("passwordEncoderOptions");

        UpdateIdentityProvider updateIdentityProvider = new UpdateIdentityProvider();
        updateIdentityProvider.setConfiguration(idpData.cfg.toJson(objectMapper));
        updateIdentityProvider.setMappers(idp.getMappers());
        updateIdentityProvider.setName(idp.getName());
        updateIdentityProvider.setPasswordPolicy(idp.getPasswordPolicy());
        updateIdentityProvider.setRoleMapper(idp.getRoleMapper());
        updateIdentityProvider.setDomainWhitelist(idp.getDomainWhitelist());
        return updateIdentityProvider;
    }

    @Override
    protected IllegalStateException getIllegalStateException() {
        return new IllegalStateException("Couldn't remove iteration rounds for non-BCrypt password encoders");
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }

    @Override
    public int getOrder() {
        return DOMAIN_IDP_UPGRADER;
    }

    private static class IdpData {
        IdentityProvider idp;
        IdpJsonConfiguration cfg;

        IdpData(IdentityProvider idp, ObjectMapper objectMapper) {
            this.idp = idp;
            this.cfg = new IdpJsonConfiguration();
            this.cfg.load(objectMapper, idp.getConfiguration());
        }
    }

    @RequiredArgsConstructor
    static final class IdpJsonConfiguration {
        private Map<String, Object> configuration;

        IdpJsonConfiguration load(ObjectMapper objectMapper, String json) {
            try {
                this.configuration = objectMapper.readValue(json, Map.class);
            } catch (Exception e) {
                this.configuration = new HashMap<>();
            }
            return this;
        }

        @SneakyThrows
        String toJson(ObjectMapper objectMapper) {
            return objectMapper.writeValueAsString(configuration);
        }

        void removeProperty(String propertyName) {
            configuration.remove(propertyName);
        }

        boolean hasValue(String propertyName, String value) {
            return ofNullable(configuration.get(propertyName))
                    .map(Object::toString)
                    .map(encoder -> encoder.equals(value))
                    .orElse(false);
        }
    }
}
