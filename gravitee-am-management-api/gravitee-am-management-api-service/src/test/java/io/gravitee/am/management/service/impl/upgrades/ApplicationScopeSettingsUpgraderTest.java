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

import io.gravitee.am.model.*;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.common.util.Maps;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ApplicationScopeSettingsUpgraderTest {

    public static final String SCOPE_OPENID = "openid";
    public static final String SCOPE_PROFILE = "profile";
    @InjectMocks
    private ApplicationScopeSettingsUpgrader upgrader = new ApplicationScopeSettingsUpgrader();

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Test
    public void shouldIgnore_IfTaskCompleted() {
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.SUCCESS.name());
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.just(task));

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(any());
        verify(applicationRepository, never()).findAll();
    }

    @Test
    public void shouldUpgrade() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        final Application appNoSettings = new Application();
        appNoSettings.setSettings(null);

        final Application appNoOauthSetings = new Application();
        appNoOauthSetings.setSettings(new ApplicationSettings());

        final Application appNoScopes = new Application();
        final ApplicationSettings settings = new ApplicationSettings();
        settings.setOauth(new ApplicationOAuthSettings());
        appNoScopes.setSettings(settings);

        final Application appScopes = new Application();
        final ApplicationSettings settingsWithScopes = new ApplicationSettings();
        final ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setScopes(Arrays.asList(SCOPE_OPENID, SCOPE_PROFILE));
        settingsWithScopes.setOauth(oauth);
        appScopes.setSettings(settingsWithScopes);

        final Application appScopesWithOptions = new Application();
        final ApplicationSettings settingsWithScopesWithOptions = new ApplicationSettings();
        final ApplicationOAuthSettings oauthWithOptions = new ApplicationOAuthSettings();
        oauthWithOptions.setScopes(Arrays.asList(SCOPE_OPENID, SCOPE_PROFILE));
        oauthWithOptions.setDefaultScopes(Arrays.asList(SCOPE_OPENID));
        oauthWithOptions.setScopeApprovals(Maps.<String, Integer>builder().put(SCOPE_PROFILE, 42).build());
        settingsWithScopes.setOauth(oauthWithOptions);
        appScopesWithOptions.setSettings(settingsWithScopesWithOptions);

        when(applicationRepository.findAll()).thenReturn(Flowable.fromArray(appNoSettings, appNoOauthSetings, appNoScopes, appScopes, appScopesWithOptions));
        when(applicationRepository.update(any())).thenReturn(Single.just(new Application()));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer((args) -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(anyString());
        verify(applicationRepository).findAll();

        verify(applicationRepository, atMost(2)).update(argThat(app -> {
            return app.getSettings() == null || app.getSettings().getOauth() == null;
        }));

        verify(applicationRepository, atMost(1)).update(argThat(app -> {
            return app.getSettings() != null && app.getSettings().getOauth() != null && app.getSettings().getOauth().getScopeSettings() == null;
        }));

        verify(applicationRepository, atMost(1)).update(argThat(app -> {
            final boolean withScopeSettings = app.getSettings() != null && app.getSettings().getOauth() != null && app.getSettings().getOauth().getScopeSettings() != null;
            return withScopeSettings && app.getSettings().getOauth().getScopeSettings().stream().allMatch(a -> {
                return (a.getScope().equalsIgnoreCase(SCOPE_OPENID) || a.getScope().equalsIgnoreCase(SCOPE_PROFILE))
                        && !a.isDefaultScope()
                        && !a.isParameterized()
                        && a.getScopeApproval() == null;
            });
        }));

        verify(applicationRepository, atMost(1)).update(argThat(app -> {
            final boolean withScopeSettings = app.getSettings() != null && app.getSettings().getOauth() != null && app.getSettings().getOauth().getScopeSettings() != null;
            return withScopeSettings && app.getSettings().getOauth().getScopeSettings().stream().allMatch(a -> {
                return (a.getScope().equalsIgnoreCase(SCOPE_OPENID)
                        && a.isDefaultScope()
                        && !a.isParameterized()
                        && a.getScopeApproval() != null && a.getScopeApproval() == 42) || (a.getScope().equalsIgnoreCase(SCOPE_PROFILE)
                        && !a.isDefaultScope()
                        && !a.isParameterized()
                        && a.getScopeApproval() == null);
            });
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
        verify(applicationRepository, never()).findAll();

        verify(systemTaskRepository, never()).updateIf(argThat( t -> t.getStatus().equalsIgnoreCase(SystemTaskStatus.SUCCESS.name())), anyString());
    }
}
