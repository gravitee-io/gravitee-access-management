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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class ApplicationClientSecretsUpgraderTest {

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private ApplicationClientSecretsUpgrader upgrader;

    @Test
    public void shouldIgnore_IfTaskCompleted() {
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.SUCCESS.name());
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.just(task));

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(any());
        verify(applicationService, never()).fetchAll();
    }

    @Test
    public void shouldUpgrade() {
        stubUpgradeInitialized();

        // App with no settings at all - should get default secret settings and empty secrets list
        final Application appNoSettings = appNoSettings("app1");

        // App with settings but no oauth settings - should get default secret settings and empty secrets list
        final Application appNoOauthSettings = appWithSettingsNoOauth("app2");

        // App with oauth settings but no client secret - should get default secret settings and empty secrets list
        final Application appNoClientSecret = appWithEmptyOauth("app3");

        // App with client secret that needs migration
        final String clientSecret = UUID.randomUUID().toString();
        final Date secretExpiresAt = new Date();
        final Application appWithClientSecret = appWithClientSecret("app4", clientSecret, secretExpiresAt, new Date());

        // App with existing secret settings but still has client secret to migrate
        final String existingClientSecret = UUID.randomUUID().toString();
        final Application appWithSecretSettingsAndSecret = appWithSecretSettingsAndClientSecret("app5", "existing-id", "SHA256", existingClientSecret);

        stubFetchAll(appNoSettings, appNoOauthSettings, appNoClientSecret, appWithClientSecret, appWithSecretSettingsAndSecret);
        stubUpdateEchoInput();
        stubUpdateIfPropagateOperationId();

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(anyString());
        verify(applicationService).fetchAll();

        // Verify apps with no settings or no oauth get updated with default secret settings
        verify(applicationService, atMost(2)).update(argThat(app -> {
            return (app.getId().equals("app1") || app.getId().equals("app2")) 
                    && app.getSecretSettings() != null 
                    && !app.getSecretSettings().isEmpty()
                    && app.getSecretSettings().getFirst().getAlgorithm().equals("NONE")
                    && app.getSecrets() != null
                    && app.getSecrets().isEmpty();
        }));

        // Verify app with no client secret gets default settings
        verify(applicationService, atMost(1)).update(argThat(app -> {
            return app.getId().equals("app3")
                    && app.getSecretSettings() != null 
                    && !app.getSecretSettings().isEmpty()
                    && app.getSecretSettings().getFirst().getAlgorithm().equals("NONE")
                    && app.getSecrets() != null
                    && app.getSecrets().isEmpty();
        }));

        // Verify app with client secret gets migrated
        verify(applicationService, atMost(1)).update(argThat(app -> {
            return app.getId().equals("app4")
                    && app.getSecretSettings() != null 
                    && !app.getSecretSettings().isEmpty()
                    && app.getSecrets() != null
                    && !app.getSecrets().isEmpty()
                    && app.getSecrets().size() == 1
                    && app.getSecrets().getFirst().getSecret().equals(clientSecret)
                    && app.getSecrets().getFirst().getExpiresAt().equals(secretExpiresAt)
                    && app.getSettings().getOauth().getClientSecret() == null
                    && app.getSettings().getOauth().getClientSecretExpiresAt() == null;
        }));

        // Verify app with existing secret settings and client secret gets migrated properly
        verify(applicationService, atMost(1)).update(argThat(app -> {
            return app.getId().equals("app5")
                    && app.getSecretSettings() != null 
                    && app.getSecretSettings().getFirst().getId().equals("existing-id")
                    && app.getSecrets() != null
                    && !app.getSecrets().isEmpty()
                    && app.getSecrets().size() == 1
                    && app.getSecrets().getFirst().getSecret().equals(existingClientSecret)
                    && app.getSecrets().getFirst().getSettingsId().equals("existing-id")
                    && app.getSettings().getOauth().getClientSecret() == null
                    && app.getSettings().getOauth().getClientSecretExpiresAt() == null;
        }));

        verify(systemTaskRepository, times(2)).updateIf(any(), any());
    }

    @Test
    public void shouldUpgradeOngoing() {
        String id = UUID.randomUUID().toString();

        SystemTask ongoingTask = new SystemTask();
        ongoingTask.setOperationId(id);
        ongoingTask.setId(id);
        ongoingTask.setStatus(SystemTaskStatus.ONGOING.name());

        SystemTask finalizedTask = new SystemTask();
        finalizedTask.setOperationId(id);
        finalizedTask.setId(id);
        finalizedTask.setStatus(SystemTaskStatus.SUCCESS.name());

        // first call no task, then ongoing and finally the successful one
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.empty(), Maybe.just(ongoingTask), Maybe.just(finalizedTask));
        when(systemTaskRepository.create(any())).thenReturn(Single.error(new Exception()));

        upgrader.upgrade();

        verify(systemTaskRepository, times(3)).findById(anyString());
        verify(applicationService, never()).fetchAll();

        verify(systemTaskRepository, never()).updateIf(argThat( t -> t.getStatus().equalsIgnoreCase(SystemTaskStatus.SUCCESS.name())), anyString());
    }

    @Test
    public void shouldHandleEmptySecretSettingsList() {
        stubUpgradeInitialized();

        // App with empty secret settings list (not null, but empty) - should create default settings
        final Application appWithEmptySecretSettings = appWithEmptySecretSettings("app-empty-settings");

        stubFetchAll(appWithEmptySecretSettings);
        stubUpdateEchoInput();
        stubUpdateIfPropagateOperationId();

        upgrader.upgrade();

        verify(applicationService, times(1)).update(argThat(app -> {
            // Should have been updated with default secret settings
            return app.getId().equals("app-empty-settings")
                    && app.getSecretSettings() != null
                    && !app.getSecretSettings().isEmpty()
                    && app.getSecretSettings().getFirst().getAlgorithm().equals("NONE")
                    && app.getSecrets() != null
                    && app.getSecrets().isEmpty();
        }));
    }

    @Test
    public void shouldHandleAppWithoutCreatedAt() {
        stubUpgradeInitialized();

        // App without createdAt date but with client secret to migrate
        final String clientSecret = "test-secret";
        final Application appNoCreatedAt = appWithClientSecret("app-no-created-at", clientSecret, null, null);

        stubFetchAll(appNoCreatedAt);
        stubUpdateEchoInput();
        stubUpdateIfPropagateOperationId();

        upgrader.upgrade();

        verify(applicationService, times(1)).update(argThat(app -> {
            return app.getId().equals("app-no-created-at")
                    && app.getSecrets() != null
                    && !app.getSecrets().isEmpty()
                    && app.getSecrets().getFirst().getSecret().equals(clientSecret)
                    && app.getSecrets().getFirst().getCreatedAt() == null; // Should handle null createdAt
        }));
    }

    @Test
    public void shouldNotUpdateAppsAlreadyMigrated() {
        stubUpgradeInitialized();

        // App that has already been migrated - has secret settings, secrets list, and no client secret in oauth
        final Application alreadyMigrated = alreadyMigratedApp("already-migrated");

        stubFetchAll(alreadyMigrated);
        stubUpdateIfPropagateOperationId();

        upgrader.upgrade();

        // Should not update any applications since it's already migrated
        verify(applicationService, never()).update(any());
    }

    @Test
    public void shouldVerifyMigrationDetails() {
        stubUpgradeInitialized();

        final String testSecret = "my-test-secret";
        final Date testCreatedAt = new Date(System.currentTimeMillis() - 100000);
        final Date testExpiresAt = new Date(System.currentTimeMillis() + 100000);

        final Application app = appWithClientSecret("test-app", testSecret, testExpiresAt, testCreatedAt);

        stubFetchAll(app);
        when(applicationService.update(any())).thenAnswer(invocation -> {
            Application updatedApp = invocation.getArgument(0);
            
            // Verify the migration details
            assertNotNull(updatedApp.getSecretSettings());
            assertFalse(updatedApp.getSecretSettings().isEmpty());
            assertEquals("NONE", updatedApp.getSecretSettings().getFirst().getAlgorithm());
            
            assertNotNull(updatedApp.getSecrets());
            assertEquals(1, updatedApp.getSecrets().size());
            
            var migratedSecret = updatedApp.getSecrets().getFirst();
            assertEquals(testSecret, migratedSecret.getSecret());
            assertEquals(testCreatedAt, migratedSecret.getCreatedAt());
            assertEquals(testExpiresAt, migratedSecret.getExpiresAt());
            assertNotNull(migratedSecret.getId());
            assertEquals(updatedApp.getSecretSettings().getFirst().getId(), migratedSecret.getSettingsId());
            
            // Verify old fields are cleared
            assertNull(updatedApp.getSettings().getOauth().getClientSecret());
            assertNull(updatedApp.getSettings().getOauth().getClientSecretExpiresAt());
            
            return Single.just(updatedApp);
        });
        stubUpdateIfPropagateOperationId();

        upgrader.upgrade();

        verify(applicationService, times(1)).update(any());
    }

    @Test
    public void shouldMigrateOnlyAppsNeedingMigration_NONE_Alg() {
        stubUpgradeInitialized();

        // App that needs migration (has client secret)
        final Application needsMigration = needsMigrationApp("needs-migration");

        // App that doesn't need migration (no client secret, already has settings)
        final Application noMigrationNeeded = noMigrationNeededApp("no-migration-needed");

        stubFetchAll(needsMigration, noMigrationNeeded);
        stubUpdateEchoInput();
        stubUpdateIfPropagateOperationId();

        upgrader.upgrade();

        // Should only update the app that needs migration
        verify(applicationService, times(1)).update(argThat(app -> 
            app.getId().equals("needs-migration")
        ));
        
        // Should not update the app that doesn't need migration
        verify(applicationService, never()).update(argThat(app -> 
            app.getId().equals("no-migration-needed")
        ));
    }

    @Test
    public void shouldMigrateOnlyAppsNeedingMigration_SHA512_Alg() {
        stubUpgradeInitialized();

        // App that needs migration (has client secret)
        final Application needsMigration = needsMigrationApp("needs-migration");

        // App that doesn't need migration (no client secret, already has settings)
        final Application noMigrationNeeded = noMigrationNeededAppWithSecretAlg("no-migration-needed");

        stubFetchAll(needsMigration, noMigrationNeeded);
        stubUpdateEchoInput();
        stubUpdateIfPropagateOperationId();

        upgrader.upgrade();

        // Should only update the app that needs migration
        verify(applicationService, times(1)).update(argThat(app ->
            app.getId().equals("needs-migration")
        ));

        // Should not update the app that doesn't need migration
        verify(applicationService, never()).update(argThat(app ->
            app.getId().equals("no-migration-needed")
        ));
    }

    @Test
    public void shouldAssignUniqueDefaultNameWhenAlreadyExists() {
        stubUpgradeInitialized();

        final String originalSecret = "secret-to-migrate";
        final Application app = appWithClientSecret("app-unique-name", originalSecret, null, new Date());
        app.setSecrets(new ArrayList<>());
        ClientSecret existing = new ClientSecret();
        existing.setId(UUID.randomUUID().toString());
        existing.setName("Default");
        app.getSecrets().add(existing);

        stubFetchAll(app);
        stubUpdateEchoInput();
        stubUpdateIfPropagateOperationId();

        upgrader.upgrade();

        verify(applicationService, times(1)).update(argThat(updated -> {
            return updated.getId().equals("app-unique-name")
                    && updated.getSecrets() != null
                    && updated.getSecrets().size() == 2
                    && updated.getSecrets().stream().anyMatch(s -> "Default".equalsIgnoreCase(s.getName()))
                    && updated.getSecrets().stream().anyMatch(s -> "Default (2)".equalsIgnoreCase(s.getName()));
        }));
    }
    // Helpers
    private void stubUpgradeInitialized() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(initializedTask()));
    }

    private SystemTask initializedTask() {
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        return task;
    }

    private void stubUpdateIfPropagateOperationId() {
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });
    }

    private void stubUpdateEchoInput() {
        when(applicationService.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    }

    private void stubFetchAll(Application... applications) {
        when(applicationService.fetchAll()).thenReturn(Single.just(Set.of(applications)));
    }

    private Application baseApp(String id, Date createdAt) {
        final Application app = new Application();
        app.setId(id);
        app.setCreatedAt(createdAt != null ? createdAt : null);
        return app;
    }

    private Application appNoSettings(String id) {
        final Application app = baseApp(id, new Date());
        app.setSettings(null);
        app.setSecretSettings(null);
        app.setSecrets(null);
        return app;
    }

    private Application appWithSettingsNoOauth(String id) {
        final Application app = baseApp(id, new Date());
        app.setSettings(new ApplicationSettings());
        app.setSecretSettings(null);
        app.setSecrets(null);
        return app;
    }

    private Application appWithEmptyOauth(String id) {
        final Application app = baseApp(id, new Date());
        final ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(new ApplicationOAuthSettings());
        app.setSettings(settings);
        app.setSecretSettings(null);
        app.setSecrets(null);
        return app;
    }

    private Application appWithClientSecret(String id, String secret, Date expiresAt, Date createdAt) {
        final Application app = baseApp(id, createdAt);
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientSecret(secret);
        oauth.setClientSecretExpiresAt(expiresAt);
        settings.setOauth(oauth);
        app.setSettings(settings);
        app.setSecretSettings(null);
        app.setSecrets(null);
        return app;
    }

    private Application appWithSecretSettingsAndClientSecret(String id, String settingsId, String algorithm, String secret) {
        final Application app = baseApp(id, new Date());
        app.setSecretSettings(List.of(new ApplicationSecretSettings(settingsId, algorithm, Map.of())));
        app.setSecrets(new ArrayList<>());
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientSecret(secret);
        settings.setOauth(oauth);
        app.setSettings(settings);
        return app;
    }

    private Application appWithEmptySecretSettings(String id) {
        final Application app = baseApp(id, new Date());
        app.setSecretSettings(new ArrayList<>());
        app.setSecrets(null);
        final ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(new ApplicationOAuthSettings());
        app.setSettings(settings);
        return app;
    }

    private Application alreadyMigratedApp(String id) {
        final Application app = baseApp(id, new Date());
        app.setSecretSettings(List.of(new ApplicationSecretSettings("migrated-id", "NONE", Map.of())));
        app.setSecrets(new ArrayList<>());
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientSecret(null);
        oauth.setClientSecretExpiresAt(null);
        settings.setOauth(oauth);
        app.setSettings(settings);
        return app;
    }

    private Application needsMigrationApp(String id) {
        final Application app = baseApp(id, new Date());
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientSecret("secret-to-migrate");
        settings.setOauth(oauth);
        app.setSettings(settings);
        app.setSecretSettings(null);
        app.setSecrets(null);
        return app;
    }

    private Application noMigrationNeededApp(String id) {
        final Application app = baseApp(id, new Date());
        app.setSecretSettings(List.of(new ApplicationSecretSettings("id", "NONE", Map.of())));
        app.setSecrets(new ArrayList<>());
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientSecret(null);
        settings.setOauth(oauth);
        app.setSettings(settings);
        return app;
    }

    private Application noMigrationNeededAppWithSecretAlg(String id) {
        final Application app = baseApp(id, new Date());
        app.setSecretSettings(List.of(new ApplicationSecretSettings("id", "SHA_512", Map.of())));
        app.setSecrets(new ArrayList<>());
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientSecret(null);
        settings.setOauth(oauth);
        app.setSettings(settings);
        return app;
    }
}
