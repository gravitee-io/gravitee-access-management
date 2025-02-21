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
package io.gravitee.am.gateway.handler.common.service.mfa;

import io.gravitee.am.gateway.handler.common.service.mfa.impl.RateLimiterServiceImpl;
import io.gravitee.am.model.RateLimit;
import io.gravitee.am.repository.gateway.api.RateLimitRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RateLimiterServiceImplTest {

    @InjectMocks
    private RateLimiterService rateLimiterService = new RateLimiterServiceImpl();

    @Mock
    RateLimitRepository repository;

    private static final String USER_ID ="user-id";
    private static final String FACTOR_ID ="factor-d";
    private static final String CLIENT ="client-id";
    private static final String DOMAIN ="domain-id";

    private Calendar calendar;

    @Test
    public void should_create_rateLimit() {
        RateLimit rateLimit = createRateLimit();
        when(repository.findByCriteria(any())).thenReturn(Maybe.empty());
        when(repository.create(any())).thenReturn(Single.just(rateLimit));
        ReflectionTestUtils.setField(rateLimiterService, "limit", 2);
        ReflectionTestUtils.setField(rateLimiterService, "timePeriod", 2);

        TestObserver<Boolean> observer = rateLimiterService.tryConsume(USER_ID, FACTOR_ID, CLIENT,DOMAIN).test();

        observer.assertComplete();
        observer.assertNoErrors();
        verify(repository, times(1)).create(any());
        verify(repository, never()).update(any());
    }

    @Test
    public void should_update_rateLimit() {
        ReflectionTestUtils.setField(rateLimiterService, "limit", 2);
        ReflectionTestUtils.setField(rateLimiterService, "timePeriod", 1);
        ReflectionTestUtils.setField(rateLimiterService, "timeUnit", "Minutes");

        RateLimit rateLimit = createRateLimit();
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(rateLimit));
        when(repository.update(any())).thenReturn(Single.just(rateLimit));

        TestObserver<Boolean> observer = rateLimiterService.tryConsume(USER_ID, FACTOR_ID, CLIENT,DOMAIN).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(allowRequest -> allowRequest);
        verify(repository, times(1)).update(any());
        verify(repository, never()).create(any());
    }

    @Test
    public void should_not_exceed_limit(){
        RateLimit rateLimit = createRateLimit();
        rateLimit.setTokenLeft(0);
        calendar = Calendar.getInstance();
        calendar.setTime(rateLimit.getUpdatedAt());
        calendar.add(Calendar.HOUR, -3);
        rateLimit.setUpdatedAt(calendar.getTime());

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 1,12);
        assertThat("Number of tokens should be 11", rateLimit.getTokenLeft(), is(11L));
        assertThat("Should not allow request", rateLimit.isAllowRequest(), is(true));
    }

    @Test
    public void shouldThrow_TechnicalManagementException_wrong_timeUnit() {
        final String wrongTimeUnit = "minute";
        ReflectionTestUtils.setField(rateLimiterService, "timeUnit", wrongTimeUnit);
        ReflectionTestUtils.setField(rateLimiterService, "timePeriod", 2);
        ReflectionTestUtils.setField(rateLimiterService, "limit", 2);
        RateLimit rateLimit = createRateLimit();
        when(repository.findByCriteria(any())).thenReturn(Maybe.just(rateLimit));

        TestObserver<Boolean> observer = rateLimiterService.tryConsume(USER_ID, FACTOR_ID, CLIENT, DOMAIN).test();
        observer.assertNotComplete();
        observer.assertError(TechnicalManagementException.class)
                .assertError(err -> "An error occurs while trying to add/update rate limit.".equals(err.getMessage()));
    }

    @Test
    public void case1_0_token_same_time_period() {
        RateLimit rateLimit = createRateLimit();
        rateLimit.setTokenLeft(0);
        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 1,1);
        assertThat("Number of tokens should be 0", rateLimit.getTokenLeft(), is(0L));
        assertThat("Should not allow request", rateLimit.isAllowRequest(), is(false));
    }

    @Test
    public void timeUnite_minutes_last_request_1_mns_ago() {
        RateLimit rateLimit = createRateLimit();
        rateLimit.setTokenLeft(0);

        calendar = Calendar.getInstance();
        calendar.setTime(rateLimit.getUpdatedAt());
        calendar.add(Calendar.MINUTE, -1);
        rateLimit.setUpdatedAt(calendar.getTime());

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 1, 1);
        assertThat("Number of tokens should be 0", rateLimit.getTokenLeft(), is(0L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 1, 2);
        assertThat("Number of tokens should be 1", rateLimit.getTokenLeft(), is(1L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));
    }

    @Test
    public void timeUnit_minutes_last_request_12_mns_ago() {
        RateLimit rateLimit = createRateLimit();
        rateLimit.setTokenLeft(0);

        calendar = Calendar.getInstance();
        calendar.setTime(rateLimit.getUpdatedAt());
        calendar.add(Calendar.MINUTE, -12);
        rateLimit.setUpdatedAt(calendar.getTime());

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 1, 12);
        assertThat("Number of tokens should be 0", rateLimit.getTokenLeft(), is(11L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 1, 50);
        assertThat("Number of tokens should be 1", rateLimit.getTokenLeft(), is(49L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));
    }

    @Test
    public void timeUnit_minutes_last_request_x_seconds_ago() {
        RateLimit rateLimit = createRateLimit();
        rateLimit.setTokenLeft(3);

        calendar = Calendar.getInstance();
        calendar.setTime(rateLimit.getUpdatedAt());
        calendar.add(Calendar.SECOND, -12);
        rateLimit.setUpdatedAt(calendar.getTime());


        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 10, 13);
        assertThat("Number of tokens should be 3", rateLimit.getTokenLeft(), is(2L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));

        calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.SECOND, -10);
        rateLimit.setUpdatedAt(calendar.getTime());


        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 10, 13);
        assertThat("Number of tokens should be 0", rateLimit.getTokenLeft(), is(1L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));

        calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.SECOND, -10);
        rateLimit.setUpdatedAt(calendar.getTime());


        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 10, 13);
        assertThat("Number of tokens should be 0", rateLimit.getTokenLeft(), is(0L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));

        calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.SECOND, -10);
        rateLimit.setUpdatedAt(calendar.getTime());


        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Minutes", 10, 13);
        assertThat("Number of tokens should be 0", rateLimit.getTokenLeft(), is(0L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(false));
    }

    @Test
    public void timeUnit_hours_last_request_x_mns_sec_ago() {
        RateLimit rateLimit = createRateLimit();
        rateLimit.setTokenLeft(1);

        calendar = Calendar.getInstance();
        calendar.setTime(rateLimit.getUpdatedAt());
        calendar.add(Calendar.MINUTE, -17);
        rateLimit.setUpdatedAt(calendar.getTime());

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Hours", 1, 10);
        assertThat("Number of tokens should be 3", rateLimit.getTokenLeft(), is(2L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));

        calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE, -5);
        rateLimit.setUpdatedAt(calendar.getTime());

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Hours", 1, 10);
        assertThat("Number of tokens should be 3", rateLimit.getTokenLeft(), is(1L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));

        calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.SECOND, -30);
        rateLimit.setUpdatedAt(calendar.getTime());

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Hours", 1, 10);
        assertThat("Number of tokens should be 3", rateLimit.getTokenLeft(), is(0L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(true));

        calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.MINUTE, -2);
        rateLimit.setUpdatedAt(calendar.getTime());

        rateLimiterService.calculateAndSetTokenLeft(rateLimit, "Hours", 1, 10);
        assertThat("Number of tokens should be 3", rateLimit.getTokenLeft(), is(0L));
        assertThat("Should allow request", rateLimit.isAllowRequest(), is(false));
    }

    @Test
    public void limit_zero_or_negative() {
        ReflectionTestUtils.setField(rateLimiterService, "timePeriod", 1);
        ReflectionTestUtils.setField(rateLimiterService, "limit", 0);

        TestObserver<Boolean> observer = rateLimiterService.tryConsume("any-id", "any-factor-id", "any-application-id", "any").test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(allowRequest -> !allowRequest);

        ReflectionTestUtils.setField(rateLimiterService, "limit", -1);

        TestObserver<Boolean> observer2 = rateLimiterService.tryConsume(USER_ID, FACTOR_ID, CLIENT,DOMAIN).test();

        observer2.assertComplete();
        observer2.assertNoErrors();
        observer2.assertValue(allowRequest -> !allowRequest);
    }

    @Test
    public void timePeriod_zero_or_negative() {
        ReflectionTestUtils.setField(rateLimiterService, "limit", 1);
        ReflectionTestUtils.setField(rateLimiterService, "timePeriod", 0);

        TestObserver<Boolean> observer = rateLimiterService.tryConsume(USER_ID, FACTOR_ID, CLIENT,DOMAIN).test();

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(allowRequest -> !allowRequest);

        ReflectionTestUtils.setField(rateLimiterService, "timePeriod", -1);

        TestObserver<Boolean> observer2 = rateLimiterService.tryConsume(USER_ID, FACTOR_ID, CLIENT,DOMAIN).test();

        observer2.assertComplete();
        observer2.assertNoErrors();
        observer2.assertValue(allowRequest -> !allowRequest);
    }

    private RateLimit createRateLimit() {
        final RateLimit rateLimit = new RateLimit();
        final String random = UUID.randomUUID().toString();
        rateLimit.setClient("client-id" + random);
        rateLimit.setUserId("user-id" + random);
        rateLimit.setFactorId("factor-id" + random);
        rateLimit.setTokenLeft(10);
        final Date date = new Date();
        rateLimit.setCreatedAt(date);
        rateLimit.setUpdatedAt(date);

        return rateLimit;
    }

}
