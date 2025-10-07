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
 * @author Eric LELEU (eric.leleu at graviteesource.com)
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
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        // App with no settings at all - should get default secret settings and empty secrets list
        final Application appNoSettings = new Application();
        appNoSettings.setId("app1");
        appNoSettings.setCreatedAt(new Date());
        appNoSettings.setSettings(null);
        appNoSettings.setSecretSettings(null);
        appNoSettings.setSecrets(null);

        // App with settings but no oauth settings - should get default secret settings and empty secrets list
        final Application appNoOauthSettings = new Application();
        appNoOauthSettings.setId("app2");
        appNoOauthSettings.setCreatedAt(new Date());
        appNoOauthSettings.setSettings(new ApplicationSettings());
        appNoOauthSettings.setSecretSettings(null);
        appNoOauthSettings.setSecrets(null);

        // App with oauth settings but no client secret - should get default secret settings and empty secrets list
        final Application appNoClientSecret = new Application();
        appNoClientSecret.setId("app3");
        appNoClientSecret.setCreatedAt(new Date());
        final ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(new ApplicationOAuthSettings());
        appNoClientSecret.setSettings(settings);
        appNoClientSecret.setSecretSettings(null);
        appNoClientSecret.setSecrets(null);

        // App with client secret that needs migration
        final Application appWithClientSecret = new Application();
        appWithClientSecret.setId("app4");
        appWithClientSecret.setCreatedAt(new Date());
        final ApplicationSettings settingsWithSecret = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        final String clientSecret = UUID.randomUUID().toString();
        final Date secretExpiresAt = new Date();
        oauth.setClientSecret(clientSecret);
        oauth.setClientSecretExpiresAt(secretExpiresAt);
        settingsWithSecret.setOauth(oauth);
        appWithClientSecret.setSettings(settingsWithSecret);
        appWithClientSecret.setSecretSettings(null);
        appWithClientSecret.setSecrets(null);

        // App with existing secret settings but still has client secret to migrate
        final Application appWithSecretSettingsAndSecret = new Application();
        appWithSecretSettingsAndSecret.setId("app5");
        appWithSecretSettingsAndSecret.setCreatedAt(new Date());
        appWithSecretSettingsAndSecret.setSecretSettings(
                List.of(new ApplicationSecretSettings("existing-id", "SHA256", Map.of()))
        );
        appWithSecretSettingsAndSecret.setSecrets(new ArrayList<>());
        final ApplicationSettings settingsWithSecretAndSettings = new ApplicationSettings();
        final ApplicationOAuthSettings oauthWithSecret = new ApplicationOAuthSettings();
        final String existingClientSecret = UUID.randomUUID().toString();
        oauthWithSecret.setClientSecret(existingClientSecret);
        settingsWithSecretAndSettings.setOauth(oauthWithSecret);
        appWithSecretSettingsAndSecret.setSettings(settingsWithSecretAndSettings);

        when(applicationService.fetchAll()).thenReturn(Single.just(Set.of(
                appNoSettings, 
                appNoOauthSettings, 
                appNoClientSecret, 
                appWithClientSecret, 
                appWithSecretSettingsAndSecret
        )));
        when(applicationService.update(any())).thenReturn(Single.just(new Application()));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

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
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        // App with empty secret settings list (not null, but empty) - should create default settings
        final Application appWithEmptySecretSettings = new Application();
        appWithEmptySecretSettings.setId("app-empty-settings");
        appWithEmptySecretSettings.setCreatedAt(new Date());
        appWithEmptySecretSettings.setSecretSettings(new ArrayList<>());
        appWithEmptySecretSettings.setSecrets(null);
        final ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(new ApplicationOAuthSettings());
        appWithEmptySecretSettings.setSettings(settings);

        when(applicationService.fetchAll()).thenReturn(Single.just(Set.of(appWithEmptySecretSettings)));
        when(applicationService.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

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
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        // App without createdAt date but with client secret to migrate
        final Application appNoCreatedAt = new Application();
        appNoCreatedAt.setId("app-no-created-at");
        appNoCreatedAt.setCreatedAt(null);
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        final String clientSecret = "test-secret";
        oauth.setClientSecret(clientSecret);
        settings.setOauth(oauth);
        appNoCreatedAt.setSettings(settings);
        appNoCreatedAt.setSecretSettings(null);
        appNoCreatedAt.setSecrets(null);

        when(applicationService.fetchAll()).thenReturn(Single.just(Set.of(appNoCreatedAt)));
        when(applicationService.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

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
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        // App that has already been migrated - has secret settings, secrets list, and no client secret in oauth
        final Application alreadyMigrated = new Application();
        alreadyMigrated.setId("already-migrated");
        alreadyMigrated.setCreatedAt(new Date());
        alreadyMigrated.setSecretSettings(
                List.of(new ApplicationSecretSettings("migrated-id", "NONE", Map.of()))
        );
        alreadyMigrated.setSecrets(new ArrayList<>());
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientSecret(null); // Already migrated, no client secret
        oauth.setClientSecretExpiresAt(null);
        settings.setOauth(oauth);
        alreadyMigrated.setSettings(settings);

        when(applicationService.fetchAll()).thenReturn(Single.just(Set.of(alreadyMigrated)));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

        upgrader.upgrade();

        // Should not update any applications since it's already migrated
        verify(applicationService, never()).update(any());
    }

    @Test
    public void shouldVerifyMigrationDetails() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        final String testSecret = "my-test-secret";
        final Date testCreatedAt = new Date(System.currentTimeMillis() - 100000);
        final Date testExpiresAt = new Date(System.currentTimeMillis() + 100000);

        final Application app = new Application();
        app.setId("test-app");
        app.setCreatedAt(testCreatedAt);
        final ApplicationSettings settings = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientSecret(testSecret);
        oauth.setClientSecretExpiresAt(testExpiresAt);
        settings.setOauth(oauth);
        app.setSettings(settings);
        app.setSecretSettings(null);
        app.setSecrets(null);

        when(applicationService.fetchAll()).thenReturn(Single.just(Set.of(app)));
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
        
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

        upgrader.upgrade();

        verify(applicationService, times(1)).update(any());
    }

    @Test
    public void shouldMigrateOnlyAppsNeedingMigration() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        // App that needs migration (has client secret)
        final Application needsMigration = new Application();
        needsMigration.setId("needs-migration");
        needsMigration.setCreatedAt(new Date());
        final ApplicationSettings settings1 = new ApplicationSettings();
        final ApplicationOAuthSettings oauth1 = new ApplicationOAuthSettings();
        oauth1.setClientSecret("secret-to-migrate");
        settings1.setOauth(oauth1);
        needsMigration.setSettings(settings1);
        needsMigration.setSecretSettings(null);
        needsMigration.setSecrets(null);

        // App that doesn't need migration (no client secret, already has settings)
        final Application noMigrationNeeded = new Application();
        noMigrationNeeded.setId("no-migration-needed");
        noMigrationNeeded.setCreatedAt(new Date());
        noMigrationNeeded.setSecretSettings(List.of(new ApplicationSecretSettings("id", "NONE", Map.of())));
        noMigrationNeeded.setSecrets(new ArrayList<>());
        final ApplicationSettings settings2 = new ApplicationSettings();
        final ApplicationOAuthSettings oauth2 = new ApplicationOAuthSettings();
        oauth2.setClientSecret(null);
        settings2.setOauth(oauth2);
        noMigrationNeeded.setSettings(settings2);

        when(applicationService.fetchAll()).thenReturn(Single.just(Set.of(needsMigration, noMigrationNeeded)));
        when(applicationService.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

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
}
