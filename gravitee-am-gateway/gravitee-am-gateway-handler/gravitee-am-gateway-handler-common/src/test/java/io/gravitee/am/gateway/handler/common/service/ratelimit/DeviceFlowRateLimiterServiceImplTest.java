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
package io.gravitee.am.gateway.handler.common.service.ratelimit;

import io.gravitee.am.gateway.handler.common.service.ratelimit.impl.DeviceFlowRateLimiterServiceImpl;
import io.gravitee.am.model.RateLimit;
import io.gravitee.am.repository.gateway.api.RateLimitRepository;
import io.gravitee.am.repository.gateway.api.search.RateLimitCriteria;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceFlowRateLimiterServiceImplTest {

    private static final String USER_ID = "user-id";
    private static final String DOMAIN = "domain-id";

    @InjectMocks
    private DeviceFlowRateLimiterService rateLimiterService = new DeviceFlowRateLimiterServiceImpl();

    @Mock
    private RateLimitRepository repository;

    @Before
    public void configure() {
        ReflectionTestUtils.setField(rateLimiterService, "limit", 3);
        ReflectionTestUtils.setField(rateLimiterService, "timePeriod", 15);
        ReflectionTestUtils.setField(rateLimiterService, "timeUnit", "Minutes");
    }

    @Test
    public void shouldBeDisabledByDefault() {
        assertFalse(new DeviceFlowRateLimiterServiceImpl().isRateLimitEnabled());
    }

    @Test
    public void shouldKeepTheDeviceFlowBucketApartFromTheMfaOne() {
        when(repository.findByCriteria(any())).thenReturn(Maybe.empty());
        when(repository.create(any())).thenReturn(Single.just(new RateLimit()));

        rateLimiterService.recordWrongAttempt(USER_ID, DOMAIN).test().assertComplete();

        ArgumentCaptor<RateLimitCriteria> criteria = ArgumentCaptor.captor();
        verify(repository).findByCriteria(criteria.capture());
        assertEquals(DeviceFlowRateLimiterService.PURPOSE, criteria.getValue().purpose());
        assertNull(criteria.getValue().factorId());

        ArgumentCaptor<RateLimit> created = ArgumentCaptor.captor();
        verify(repository).create(created.capture());
        assertEquals(DeviceFlowRateLimiterService.PURPOSE, created.getValue().getPurpose());
        assertNull(created.getValue().getFactorId());
        assertEquals(2L, created.getValue().getTokenLeft(), 0);
    }

    @Test
    public void shouldNotKeyTheBucketOnTheApplication() {
        when(repository.findByCriteria(any())).thenReturn(Maybe.empty());
        when(repository.create(any())).thenReturn(Single.just(new RateLimit()));

        rateLimiterService.recordWrongAttempt(USER_ID, DOMAIN).test().assertComplete();

        ArgumentCaptor<RateLimitCriteria> criteria = ArgumentCaptor.captor();
        verify(repository).findByCriteria(criteria.capture());
        assertEquals(USER_ID, criteria.getValue().userId());
        assertNull(criteria.getValue().client());

        ArgumentCaptor<RateLimit> created = ArgumentCaptor.captor();
        verify(repository).create(created.capture());
        assertNull(created.getValue().getClient());
        assertEquals(DOMAIN, created.getValue().getReferenceId());
    }

    @Test
    public void shouldRefuseAnAttemptOnceTheBucketIsEmpty() {
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(existingBucket(0, 0)));

        TestObserver<Boolean> observer = rateLimiterService.hasAttemptsLeft(USER_ID, DOMAIN).test();

        observer.assertComplete();
        observer.assertValue(allowed -> !allowed);
    }

    @Test
    public void shouldServeAnAttemptWhileTheBucketHoldsTokens() {
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(existingBucket(2, 0)));

        TestObserver<Boolean> observer = rateLimiterService.hasAttemptsLeft(USER_ID, DOMAIN).test();

        observer.assertComplete();
        observer.assertValue(allowed -> allowed);
    }

    @Test
    public void shouldServeAUserWhoNeverGotACodeWrong() {
        when(repository.findByCriteria(any())).thenReturn(Maybe.empty());

        TestObserver<Boolean> observer = rateLimiterService.hasAttemptsLeft(USER_ID, DOMAIN).test();

        observer.assertComplete();
        observer.assertValue(allowed -> allowed);
    }

    @Test
    public void shouldNotSpendAnAttemptWhenOnlyAskingWhetherOneIsLeft() {
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(existingBucket(2, 0)));

        rateLimiterService.hasAttemptsLeft(USER_ID, DOMAIN).test().assertComplete();

        verify(repository, never()).update(any());
        verify(repository, never()).create(any());
    }

    @Test
    public void shouldServeAgainOnceTheWindowRolledOver() {
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(existingBucket(0, -30)));

        TestObserver<Boolean> observer = rateLimiterService.hasAttemptsLeft(USER_ID, DOMAIN).test();

        observer.assertComplete();
        observer.assertValue(allowed -> allowed);
    }

    private RateLimit existingBucket(long tokenLeft, int updatedMinutesAgo) {
        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, updatedMinutesAgo);
        final RateLimit rateLimit = new RateLimit();
        rateLimit.setUserId(USER_ID);
        rateLimit.setPurpose(DeviceFlowRateLimiterService.PURPOSE);
        rateLimit.setReferenceId(DOMAIN);
        rateLimit.setTokenLeft(tokenLeft);
        rateLimit.setCreatedAt(new Date());
        rateLimit.setUpdatedAt(calendar.getTime());
        return rateLimit;
    }
}
