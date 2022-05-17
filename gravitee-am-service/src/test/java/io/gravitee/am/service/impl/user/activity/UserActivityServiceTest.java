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

package io.gravitee.am.service.impl.user.activity;

import io.gravitee.am.model.UserActivity;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.repository.management.api.UserActivityRepository;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.am.service.impl.UserActivityServiceImpl;
import io.gravitee.am.service.impl.user.activity.configuration.UserActivityConfiguration;
import io.gravitee.am.service.impl.user.activity.configuration.UserActivityConfiguration.Algorithm;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserActivityServiceTest {

    private UserActivityRepository userActivityRepository;
    private UserActivityService userActivityService;

    @Before
    public void before() {
        var configuration = new UserActivityConfiguration(true,
                Algorithm.NONE,
                null, 3600,
                ChronoUnit.SECONDS,
                0.07,
                0.07);
        userActivityRepository = Mockito.mock(UserActivityRepository.class);
        userActivityService = new UserActivityServiceImpl(configuration, userActivityRepository);
    }

    @Test
    public void must_log_error_and_do_nothing_if_failing() {
        doReturn(Single.error(new IllegalArgumentException("An Error"))).when(userActivityRepository).create(any());
        final TestObserver<Void> testObserver = userActivityService.save("domain", "user-id", Type.LOGIN, Map.of()).test();

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
        assertSame(userActivityService.getRetentionUnit(), ChronoUnit.SECONDS);
    }

    @Test
    public void must_log_info_and_do_nothing_if_success() {
        doReturn(Single.just(new UserActivity())).when(userActivityRepository).create(any());
        final TestObserver<Void> testObserver = userActivityService.save("domain", "user-id", Type.LOGIN, Map.of()).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void must_delete_by_domain() {
        doReturn(Completable.complete()).when(userActivityRepository).deleteByDomain(anyString());
        final TestObserver<Void> testObserver = userActivityService.deleteByDomain("domain").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void must_delete_by_domain_and_key() {
        doReturn(Completable.complete()).when(userActivityRepository).deleteByDomainAndKey(anyString(), anyString());
        final TestObserver<Void> testObserver = userActivityService.deleteByDomainAndUser("domain", "user-id").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void must_throw_error_when_delete_by_domain_and_key() {
        doReturn(Completable.error(new IllegalArgumentException("An Error"))).when(userActivityRepository).deleteByDomainAndKey(anyString(), anyString());;
        final TestObserver<Void> testObserver = userActivityService.deleteByDomainAndUser("domain", "user-id").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    public void must_throw_error_when_delete_by_domain() {
        doReturn(Completable.error(new IllegalArgumentException("An Error"))).when(userActivityRepository).deleteByDomain(anyString());
        final TestObserver<Void> testObserver = userActivityService.deleteByDomain("domain").test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    public void must_find_by_domain_user_type_and_limit() {
        doReturn(Flowable.just(new UserActivity(), new UserActivity(), new UserActivity()))
                .when(userActivityRepository).findByDomainAndTypeAndKeyAndLimit(anyString(), any(), anyString(), anyInt());
        final TestSubscriber<UserActivity> testObserver =
                userActivityService.findByDomainAndTypeAndUserAndLimit("domain", Type.LOGIN, "user-id", 3).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(3);
    }

}
