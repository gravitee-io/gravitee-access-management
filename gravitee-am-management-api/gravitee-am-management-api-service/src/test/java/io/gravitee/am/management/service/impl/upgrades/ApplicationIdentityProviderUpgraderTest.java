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
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class ApplicationIdentityProviderUpgraderTest {

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    @InjectMocks
    private ApplicationIdentityProviderUpgrader upgrader;


    @Test
    public void shouldIgnore_IfTaskCompleted() {
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.SUCCESS.name());
        when(systemTaskRepository.findById(any())).thenReturn(Maybe.just(task));

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(any());
        verify(applicationService, never()).findAll();
        verify(identityProviderRepository, never()).findAll();
    }

    @Test
    public void shouldMigrate() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        final SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));

        var domain1 = "domain-1";
        var domain2 = "domain-2";

        var idp1 = new IdentityProvider();
        idp1.setId(UUID.randomUUID().toString());

        var idp2 = new IdentityProvider();
        idp2.setId(UUID.randomUUID().toString());
        idp2.setDomainWhitelist(List.of());

        var idp3 = new IdentityProvider();
        idp3.setId(UUID.randomUUID().toString());
        idp3.setDomainWhitelist(List.of("gmail.com", "graviteesource.com"));

        var idp4 = new IdentityProvider();
        idp4.setId(UUID.randomUUID().toString());
        idp4.setDomainWhitelist(List.of());

        when(identityProviderRepository.findAll(eq(ReferenceType.DOMAIN), eq(domain1))).thenReturn(
                Flowable.just(idp1, idp2, idp3)
        );

        when(identityProviderRepository.findAll(eq(ReferenceType.DOMAIN), eq(domain2))).thenReturn(
                Flowable.just(idp4)
        );


        final Application appNoIdentities = new Application();
        appNoIdentities.setDomain(domain1);
        appNoIdentities.setName("appNoIdentities");

        final Application appWithIdentityNoWhitelist = new Application();
        appWithIdentityNoWhitelist.setDomain(domain1);
        appWithIdentityNoWhitelist.setName("appWithIdentityNoWhitelist");
        appWithIdentityNoWhitelist.setIdentityProviders(getApplicationIdentityProviders(idp1.getId(), idp2.getId()));

        final Application appWithIdentityWithWhitelist = new Application();
        appWithIdentityWithWhitelist.setDomain(domain1);
        appWithIdentityWithWhitelist.setName("appWithIdentityWithWhitelist");
        appWithIdentityWithWhitelist.setIdentityProviders(getApplicationIdentityProviders(idp3.getId()));

        final Application appOtherDomain = new Application();
        appOtherDomain.setDomain(domain2);
        appOtherDomain.setName("appOtherDomain");
        appOtherDomain.setIdentityProviders(getApplicationIdentityProviders(idp4.getId()));


        when(applicationService.findAll()).thenReturn(Single.just(Set.of(
                appNoIdentities,
                appWithIdentityNoWhitelist,
                appWithIdentityWithWhitelist,
                appOtherDomain)
                )
        );
        when(applicationService.update(any())).thenReturn(Single.just(new Application()));

        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer((args) -> {
            SystemTask sysTask = args.getArgument(0);
            sysTask.setOperationId(args.getArgument(1));
            return Single.just(sysTask);
        });

        upgrader.upgrade();

        verify(systemTaskRepository, times(1)).findById(anyString());
        verify(applicationService).findAll();

        verify(applicationService, atMost(1)).update(
                argThat(app -> app.getIdentityProviders().stream().anyMatch(appIdp ->
                        appIdp.getSelectionRule() != null && Objects.equals(
                                appIdp.getSelectionRule(),
                                "{#request.params['usenrame'] matches '.+@gmail.com$' || #request.params['usenrame'] matches '.+@graviteesource.com$'}"
                        )
                ))
        );

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
        verify(applicationService, never()).findAll();

        verify(systemTaskRepository, never()).updateIf(argThat(t -> t.getStatus().equalsIgnoreCase(SystemTaskStatus.SUCCESS.name())), anyString());
    }

    private SortedSet<ApplicationIdentityProvider> getApplicationIdentityProviders(String... identities) {
        var set = new TreeSet<ApplicationIdentityProvider>();
        Arrays.stream(identities).forEach(identity -> {
            var patchAppIdp = new ApplicationIdentityProvider();
            patchAppIdp.setIdentity(identity);
            set.add(patchAppIdp);
        });
        return set;
    }
}
