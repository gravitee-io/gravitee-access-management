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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.scope.ManagementRepositoryScope;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.exception.ApplicationSecretConfigurationException;
import io.gravitee.am.service.spring.application.SecretHashAlgorithm;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.gravitee.am.management.service.impl.upgrades.UpgraderOrder.APPLICATION_CLIENT_SECRETS_UPGRADER;

/**
 * @author GraviteeSource Team
 */
@Component
@ManagementRepositoryScope
public class ApplicationClientSecretsUpgrader extends SystemTaskUpgrader {
    private static final String TASK_ID = "client_secrets_migration";
    private static final String UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE =
            "Application client secrets can't be upgraded, other instance may process them or an upgrader has failed previously";
    private static final SecretHashAlgorithm noneAlg = SecretHashAlgorithm.NONE;

    private final Logger logger = LoggerFactory.getLogger(ApplicationClientSecretsUpgrader.class);

    private final ObjectMapper om = new ObjectMapper();
    private final Map<String, Object> noProperties = Map.of();

    private final ApplicationService applicationService;

    public ApplicationClientSecretsUpgrader(@Lazy SystemTaskRepository systemTaskRepository, ApplicationService applicationService) {
        super(systemTaskRepository);
        this.applicationService = applicationService;
    }

    @Override
    protected Single<Boolean> processUpgrade(String instanceOperationId, SystemTask task, String conditionalOperationId) {
        return updateSystemTask(task, (SystemTaskStatus.ONGOING), conditionalOperationId)
                .flatMap(updatedTask -> {
                    if (updatedTask.getOperationId().equals(instanceOperationId)) {
                        return migrateClientSecret(updatedTask);
                    } else {
                        return Single.error(new IllegalStateException("Task " + getTaskId() + " already processed by another instance : trigger a retry"));
                    }
                })
                .map(b -> true);
    }

    @Override
    protected IllegalStateException getIllegalStateException() {
        return new IllegalStateException(UPGRADE_NOT_SUCCESSFUL_ERROR_MESSAGE);
    }

    private Single<Boolean> migrateClientSecret(SystemTask task) {
        return applicationService.fetchAll()
                .flatMapPublisher(Flowable::fromIterable)
                .flatMapSingle(app -> {
                    logger.debug("Process application '{}' for client secret migration", app.getId());

                    String settingsId = null;
                    boolean updateRequired = false;

                    // First, ensure secret settings exist (handle null or empty list)
                    if (app.getSecretSettings() != null) {
                        settingsId = app.getSecretSettings()
                                .stream()
                                .filter(s -> Objects.equals(s.getAlgorithm(), noneAlg.name()))
                                .map(ApplicationSecretSettings::getId)
                                .findFirst()
                                .orElse(null);
                    }

                    // If no secret settings exist, create a default one
                    if (settingsId == null) {
                        var defaultSecretSettings = buildNoneSecretSettings();
                        logger.debug("Create default application secret settings for application '{}' ({})", app.getId(), defaultSecretSettings.getId());
                        app.setSecretSettings(List.of(defaultSecretSettings));
                        settingsId = defaultSecretSettings.getId();
                        updateRequired = true;
                    }

                    if (app.getSecrets() == null) {
                        app.setSecrets(new ArrayList<>());
                        updateRequired = true;
                    }

                    if (app.getSettings() != null && app.getSettings().getOauth() != null) {
                        final ApplicationOAuthSettings oauthSettings = app.getSettings().getOauth();
                        var clientSecret = oauthSettings.getClientSecret();

                        // If there is not a historical secret, we can skip this application
                        if (clientSecret != null) {
                            logger.debug("Migrating client secret for application '{}'", app.getId());
                            // migrate the client secret into the new list of secrets
                            ClientSecret newSecret = new ClientSecret();
                            newSecret.setId(UUID.randomUUID().toString());
                            newSecret.setSettingsId(settingsId);
                            newSecret.setSecret(clientSecret);
                            newSecret.setCreatedAt(app.getCreatedAt());
                            newSecret.setExpiresAt(oauthSettings.getClientSecretExpiresAt());

                            // Add the new secret to the application
                            app.getSecrets().add(newSecret);

                            // Remove the client secret from the application settings
                            logger.debug("Removing client secret from application oauth settings for application '{}'", app.getId());
                            oauthSettings.setClientSecret(null);
                            oauthSettings.setClientSecretExpiresAt(null);
                            updateRequired = true;
                        } else {
                            logger.debug("No client secret to migrate for application '{}'", app.getId());
                        }
                    }

                    if (updateRequired) {
                        logger.debug("Update client secret settings for application '{}'", app.getId());
                        return applicationService.update(app);
                    }

                    return Single.just(app);
                }).ignoreElements()
                .doOnError(err -> updateSystemTask(task, (SystemTaskStatus.FAILURE), task.getOperationId()).subscribe())
                .andThen(Single.defer(() ->
                        updateSystemTask(task, SystemTaskStatus.SUCCESS, task.getOperationId())
                                .map(b -> true)
                                .onErrorReturnItem(false)
                ))
                .onErrorResumeNext(err -> {
                    logger.error("Unable to migrate client secret for applications: {}", err.getMessage());
                    return Single.just(false);
                });
    }

    @Override
    public int getOrder() {
        return APPLICATION_CLIENT_SECRETS_UPGRADER;
    }

    @Override
    protected String getTaskId() {
        return TASK_ID;
    }

    private ApplicationSecretSettings buildNoneSecretSettings() {
        try {
            final var serializedConfig = om.writeValueAsString(List.of(noneAlg, noProperties));
            final var id = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(serializedConfig.getBytes())
            );
            return new ApplicationSecretSettings(id, noneAlg.name(), noProperties);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new ApplicationSecretConfigurationException(e);
        }
    }
}
