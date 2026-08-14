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
package io.gravitee.am.service.purge;

import io.gravitee.am.repository.common.ExpiredDataSweeper;
import io.gravitee.am.repository.common.ExpiredDataSweeper.Target;
import io.gravitee.am.repository.common.ExpiredDataSweeperProvider;
import io.reactivex.rxjava3.core.Completable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers how the scheduled purge decides what to clean and whether to run at all.
 * <p>
 * On JDBC deployments this service is the only mechanism removing expired records - the TTL-index
 * mechanism is MongoDB-only - so its target selection and scheduling are worth pinning.
 *
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ScheduledPurgeServiceTest {

    private static final String CRON = "0 0 23 * * *";

    private static final List<Target> SUPPORTED_TARGETS =
            List.of(Target.access_tokens, Target.refresh_tokens, Target.authorization_codes);

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ExpiredDataSweeperProvider sweeperProvider;

    private ScheduledPurgeServiceFactory factory;

    @Before
    public void before() {
        factory = new ScheduledPurgeServiceFactory(SUPPORTED_TARGETS);
    }

    @Test
    public void shouldPurgeEverySupportedTargetWhenNothingIsExcluded() {
        ExpiredDataSweeper sweeper = sweeperReturning(Completable.complete());
        when(sweeperProvider.getExpiredDataSweeper(any())).thenReturn(sweeper);

        purgeServiceWithExclusions(List.of()).run();

        SUPPORTED_TARGETS.forEach(target -> verify(sweeperProvider).getExpiredDataSweeper(target));
        verify(sweeper, org.mockito.Mockito.times(SUPPORTED_TARGETS.size())).purgeExpiredData();
    }

    @Test
    public void shouldNotPurgeAnExcludedTarget() {
        ExpiredDataSweeper sweeper = sweeperReturning(Completable.complete());
        when(sweeperProvider.getExpiredDataSweeper(any())).thenReturn(sweeper);

        purgeServiceWithExclusions(List.of(Target.refresh_tokens.name())).run();

        verify(sweeperProvider).getExpiredDataSweeper(Target.access_tokens);
        verify(sweeperProvider).getExpiredDataSweeper(Target.authorization_codes);
        verify(sweeperProvider, never()).getExpiredDataSweeper(Target.refresh_tokens);
    }

    /**
     * A target whose sweeper cannot be resolved - not registered for this deployment, or the
     * provider itself failing - must be skipped without taking the rest of the run with it.
     */
    @Test
    public void shouldSkipATargetWhoseSweeperCannotBeResolved() {
        ExpiredDataSweeper sweeper = sweeperReturning(Completable.complete());
        when(sweeperProvider.getExpiredDataSweeper(Target.access_tokens))
                .thenThrow(new IllegalStateException("no sweeper registered"));
        when(sweeperProvider.getExpiredDataSweeper(Target.refresh_tokens)).thenReturn(null);
        when(sweeperProvider.getExpiredDataSweeper(Target.authorization_codes)).thenReturn(sweeper);

        purgeServiceWithExclusions(List.of()).run();

        verify(sweeper).purgeExpiredData();
    }

    @Test
    public void shouldNotSchedulePurgeWhenDisabled() throws Exception {
        factory.createPurgeService(false, CRON, List.of(), taskScheduler, sweeperProvider).start();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(CronTrigger.class));
    }

    @Test
    public void shouldSchedulePurgeWithTheConfiguredCronWhenEnabled() throws Exception {
        ScheduledPurgeService service =
                factory.createPurgeService(true, CRON, List.of(), taskScheduler, sweeperProvider);

        service.start();

        ArgumentCaptor<CronTrigger> trigger = ArgumentCaptor.forClass(CronTrigger.class);
        verify(taskScheduler).schedule(org.mockito.ArgumentMatchers.eq(service), trigger.capture());
        assertThat(trigger.getValue().getExpression()).isEqualTo(CRON);
    }

    private ScheduledPurgeService purgeServiceWithExclusions(List<String> excluded) {
        return factory.createPurgeService(true, CRON, excluded, taskScheduler, sweeperProvider);
    }

    private static ExpiredDataSweeper sweeperReturning(Completable result) {
        ExpiredDataSweeper sweeper = mock(ExpiredDataSweeper.class);
        when(sweeper.purgeExpiredData()).thenReturn(result);
        return sweeper;
    }
}
