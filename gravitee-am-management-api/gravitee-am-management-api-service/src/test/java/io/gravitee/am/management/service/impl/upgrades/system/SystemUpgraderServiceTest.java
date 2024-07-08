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

package io.gravitee.am.management.service.impl.upgrades.system;


import io.gravitee.am.management.service.impl.upgrades.system.upgraders.SystemUpgrader;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.node.api.Node;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SystemUpgraderServiceTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private Node node;

    @Mock
    private SystemUpgrader upgrader;

    private SystemUpgraderService cut;

    @BeforeEach
    public void init() {
        cut = new TestableSystemUpgraderService();
    }

    @Test
    public void should_upgrade() throws Exception {
        when(upgrader.upgrade()).thenReturn(Completable.complete());

        Map<String, SystemUpgrader> beans = new HashMap<>();
        beans.put(upgrader.getClass().getName(), upgrader);
        cut.setApplicationContext(applicationContext);

        when(applicationContext.getBeansOfType(SystemUpgrader.class)).thenReturn(beans);

        cut.start();

        verify(node, never()).stop();
    }

    @Test
    public void should_strop_after_failing_upgrade() throws Exception {
        final var nbOfExitBefore = ((TestableSystemUpgraderService)cut).getNumberOfExit();
        when(applicationContext.getBean(Node.class)).thenReturn(node);
        when(upgrader.upgrade()).thenReturn(Completable.error(new TechnicalException()));

        Map<String, SystemUpgrader> beans = new HashMap<>();
        beans.put(upgrader.getClass().getName(), upgrader);
        cut.setApplicationContext(applicationContext);

        when(applicationContext.getBeansOfType(SystemUpgrader.class)).thenReturn(beans);

        cut.start();

        final var nbOfExitAfter = ((TestableSystemUpgraderService)cut).getNumberOfExit();

        verify(node).stop();
        Assertions.assertEquals(nbOfExitBefore + 1, nbOfExitAfter);
    }

    public static final class TestableSystemUpgraderService extends SystemUpgraderService {
        private int numberOfExit = 0;
        @Override
        protected void exitOnError() {
            ++numberOfExit;
        }

        public int getNumberOfExit() {
            return numberOfExit;
        }
    }
}
