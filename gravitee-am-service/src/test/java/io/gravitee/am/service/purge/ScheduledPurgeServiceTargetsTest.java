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
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks that one failing purge target does not stop the purge of the other targets.
 * The assertions are on subscription, because the service builds every target's
 * {@link Completable} before it subscribes to any of them.
 *
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class ScheduledPurgeServiceTargetsTest {

    private static final String CRON = "0 0 23 * * *";

    private static final List<Target> TARGETS =
            List.of(Target.access_tokens, Target.authorization_codes, Target.refresh_tokens);

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ExpiredDataSweepers sweepers;

    private final List<Target> subscribed = new ArrayList<>();

    @Test
    void shouldPurgeLaterTargetsWhenFirstTargetFails() {
        givenSweeper(Target.access_tokens, Completable.error(new RuntimeException("access_tokens purge failed")));
        givenSweeper(Target.authorization_codes, Completable.complete());
        givenSweeper(Target.refresh_tokens, Completable.complete());

        purgeService(List.of()).run();

        assertThat(subscribed).containsExactlyElementsOf(TARGETS);
    }

    @Test
    void shouldPurgeTargetsBeforeAndAfterFailingTarget() {
        givenSweeper(Target.access_tokens, Completable.complete());
        givenSweeper(Target.authorization_codes, Completable.error(new RuntimeException("authorization_codes purge failed")));
        givenSweeper(Target.refresh_tokens, Completable.complete());

        purgeService(List.of()).run();

        assertThat(subscribed).containsExactlyElementsOf(TARGETS);
    }

    @Test
    void shouldPurgeLaterTargetsWhenSweeperThrowsSynchronously() {
        ExpiredDataSweeper throwingSweeper = mock(ExpiredDataSweeper.class);
        when(throwingSweeper.purgeExpiredData()).thenThrow(new RuntimeException("access_tokens sweeper failed"));
        when(sweepers.getExpiredDataSweeper(Target.access_tokens)).thenReturn(throwingSweeper);
        givenSweeper(Target.authorization_codes, Completable.complete());
        givenSweeper(Target.refresh_tokens, Completable.complete());

        ScheduledPurgeService purgeService = purgeService(List.of());

        assertThatCode(purgeService::run).doesNotThrowAnyException();
        assertThat(subscribed).containsExactly(Target.authorization_codes, Target.refresh_tokens);
    }

    @Test
    void shouldPurgeEveryTargetOnceInOrderWhenAllSucceed() {
        TARGETS.forEach(target -> givenSweeper(target, Completable.complete()));

        purgeService(List.of()).run();

        assertThat(subscribed).containsExactlyElementsOf(TARGETS);
    }

    private void givenSweeper(Target target, Completable purge) {
        ExpiredDataSweeper sweeper = mock(ExpiredDataSweeper.class);
        when(sweeper.purgeExpiredData()).thenReturn(purge.doOnSubscribe(disposable -> subscribed.add(target)));
        when(sweepers.getExpiredDataSweeper(target)).thenReturn(sweeper);
    }

    private ScheduledPurgeService purgeService(List<String> excludedTargets) {
        return new ScheduledPurgeServiceFactory(TARGETS)
                .createPurgeService(true, CRON, excludedTargets, taskScheduler, sweepers);
    }
}
