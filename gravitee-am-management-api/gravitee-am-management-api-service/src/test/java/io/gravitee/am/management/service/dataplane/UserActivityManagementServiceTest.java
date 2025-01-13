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

package io.gravitee.am.management.service.dataplane;

import io.gravitee.am.dataplane.api.repository.UserActivityRepository;
import io.gravitee.am.management.service.dataplane.impl.UserActivityManagementServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.dataplane.user.activity.configuration.UserActivityConfiguration;
import io.gravitee.am.service.dataplane.user.activity.configuration.UserActivityConfiguration.Algorithm;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserActivityManagementServiceTest {

    private UserActivityRepository userActivityRepository;
    private DataPlaneRegistry dataPlaneRegistry;
    private UserActivityManagementService userActivityService;
    private final Domain domain = new Domain();

    @Before
    public void before() {
        domain.setId("domain");
        var configuration = new UserActivityConfiguration(true,
                Algorithm.NONE,
                null, 3600,
                ChronoUnit.SECONDS,
                0.07,
                0.07);
        userActivityRepository = Mockito.mock(UserActivityRepository.class);
        dataPlaneRegistry = Mockito.mock(DataPlaneRegistry.class);
        when(dataPlaneRegistry.getUserActivityRepository(any())).thenReturn(Single.just(userActivityRepository));
        userActivityService = new UserActivityManagementServiceImpl(configuration, dataPlaneRegistry);
    }

    @Test
    public void must_delete_by_domain() {
        doReturn(Completable.complete()).when(userActivityRepository).deleteByDomain(anyString());
        final TestObserver<Void> testObserver = userActivityService.deleteByDomain(domain).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void must_delete_by_domain_and_key() {
        doReturn(Completable.complete()).when(userActivityRepository).deleteByDomainAndKey(anyString(), anyString());
        final TestObserver<Void> testObserver = userActivityService.deleteByDomainAndUser(domain, "user-id").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void must_throw_error_when_delete_by_domain_and_key() {
        doReturn(Completable.error(new IllegalArgumentException("An Error"))).when(userActivityRepository).deleteByDomainAndKey(anyString(), anyString());;
        final TestObserver<Void> testObserver = userActivityService.deleteByDomainAndUser(domain, "user-id").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    public void must_throw_error_when_delete_by_domain() {
        doReturn(Completable.error(new IllegalArgumentException("An Error"))).when(userActivityRepository).deleteByDomain(anyString());
        final TestObserver<Void> testObserver = userActivityService.deleteByDomain(domain).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(IllegalArgumentException.class);
    }
}
