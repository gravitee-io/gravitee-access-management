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

import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.model.UpdateReporter;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Islem TRIKI (islem.triki at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultReporterUpgraderTest {

    @InjectMocks
    private DefaultReporterUpgrader defaultReporterUpgrader = new DefaultReporterUpgrader();

    @Mock
    private ReporterService reporterService;

    private Reporter systemReporter = createDefaultTestReporter(true);
    private Reporter nonSystemReporter = createDefaultTestReporter(false);

    @Test
    public void shouldUpdateDefaultIdp(){

        when(reporterService.findAll()).thenReturn(Flowable.just(systemReporter));
        when(reporterService.createReporterConfig(anyString())).thenReturn("new-config-created");
        when(reporterService.update(anyString(), anyString(), any(UpdateReporter.class), eq(true))).thenReturn(changeReporterConfig(true));

        defaultReporterUpgrader.upgrade();

        verify(reporterService, times(1)).findAll();
        verify(reporterService, times(1))
                .update(anyString(), anyString(), argThat(upReporter -> upReporter.getConfiguration().equals("new-config-created")), anyBoolean());

    }

    @Test
    public void shouldNotUpdateDefaultIdp(){

        when(reporterService.findAll()).thenReturn(Flowable.just(nonSystemReporter));

        defaultReporterUpgrader.upgrade();

        verify(reporterService, times(1)).findAll();
        verify(reporterService, never())
                .update(anyString(), anyString(), any(UpdateReporter.class), anyBoolean());
    }

    private Single<Reporter> changeReporterConfig(boolean system){
        if (system){
            systemReporter.setConfiguration("changed-system-config");
        } else {
            nonSystemReporter.setConfiguration("changed-non-system-config");
        }

        return Single.just(system ? systemReporter : nonSystemReporter);
    }

    private Reporter createDefaultTestReporter(boolean system){
        Reporter reporter = new Reporter();
        reporter.setName("Default test Reporter");
        reporter.setSystem(system);
        reporter.setId("test-reporter-id");
        reporter.setConfiguration("configuration-test-reporter");
        reporter.setDomain("domain-id");

        return reporter;
    }
}
