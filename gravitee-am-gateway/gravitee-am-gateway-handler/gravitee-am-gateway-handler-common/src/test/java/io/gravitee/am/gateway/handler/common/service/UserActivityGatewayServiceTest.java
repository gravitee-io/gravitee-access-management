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

package io.gravitee.am.gateway.handler.common.service;

import io.gravitee.am.dataplane.api.repository.UserActivityRepository;
import io.gravitee.am.gateway.handler.common.service.impl.UserActivityGatewayServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.dataplane.user.activity.configuration.UserActivityConfiguration;
import io.gravitee.am.service.dataplane.user.activity.configuration.UserActivityConfiguration.Algorithm;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserActivityGatewayServiceTest {

    private UserActivityRepository userActivityRepository;
    private DataPlaneRegistry dataPlaneRegistry;
    private UserActivityGatewayService userActivityService;
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
        when(dataPlaneRegistry.getUserActivityRepository(any())).thenReturn(userActivityRepository);
        userActivityService = new UserActivityGatewayServiceImpl(configuration, dataPlaneRegistry);
    }

    @Test
    public void must_log_error_and_do_nothing_if_failing() {
        doReturn(Single.error(new IllegalArgumentException("An Error"))).when(userActivityRepository).create(any());
        final TestObserver<Void> testObserver = userActivityService.save(domain, "user-id", Type.LOGIN, Map.of()).test();

        testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    public void must_check_if_can_save() {
        assertTrue(userActivityService.canSaveUserActivity());
    }

    @Test
    public void must_get_retention_time() {
        assertEquals(3600L, userActivityService.getRetentionTime());
    }

    @Test
    public void must_get_retention_unit() {
        assertSame(ChronoUnit.SECONDS, userActivityService.getRetentionUnit());
    }

    @Test
    public void must_log_info_and_do_nothing_if_success() {
        doReturn(Single.just(new UserActivity())).when(userActivityRepository).create(any());
        final TestObserver<Void> testObserver = userActivityService.save(domain, "user-id", Type.LOGIN, Map.of()).test();

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
    public void must_find_by_domain_user_type_and_limit() {
        doReturn(Flowable.just(new UserActivity(), new UserActivity(), new UserActivity()))
                .when(userActivityRepository).findByDomainAndTypeAndKeyAndLimit(anyString(), any(), anyString(), anyInt());
        final TestSubscriber<UserActivity> testObserver =
                userActivityService.findByDomainAndTypeAndUserAndLimit(domain, Type.LOGIN, "user-id", 3).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(3);
    }

}
