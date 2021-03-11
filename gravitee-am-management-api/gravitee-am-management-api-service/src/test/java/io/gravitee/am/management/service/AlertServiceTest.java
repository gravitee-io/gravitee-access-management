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
package io.gravitee.am.management.service;

import io.gravitee.alert.api.trigger.TriggerProvider;
import io.gravitee.plugin.alert.AlertTriggerProviderManager;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AlertServiceTest {

    @Mock
    private AlertTriggerProviderManager triggerProviderManager;

    private AlertService cut;

    @Before
    public void before() {
        cut = new AlertService(triggerProviderManager);
    }

    @Test
    public void assertAlertIsAvailable() {

        when(triggerProviderManager.findAll()).thenReturn(Collections.singleton(mock(TriggerProvider.class)));
        final TestObserver<Boolean> obs = cut.isAlertingAvailable().test();

        obs.awaitTerminalEvent();
        obs.assertValue(true);
    }

    @Test
    public void assertAlertIsNotAvailable() {

        when(triggerProviderManager.findAll()).thenReturn(Collections.emptyList());
        final TestObserver<Boolean> obs = cut.isAlertingAvailable().test();

        obs.awaitTerminalEvent();
        obs.assertValue(false);
    }
}