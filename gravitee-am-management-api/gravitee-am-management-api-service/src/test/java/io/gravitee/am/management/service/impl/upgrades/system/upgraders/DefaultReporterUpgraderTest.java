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
package io.gravitee.am.management.service.impl.upgrades.system.upgraders;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.ReporterService;
import io.gravitee.am.service.model.UpdateReporter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Islem TRIKI (islem.triki at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class DefaultReporterUpgraderTest {


    @Mock
    private ReporterService reporterService;
    @InjectMocks
    private DefaultReporterUpgrader defaultReporterUpgrader;

    private Reporter systemReporter = createDefaultTestReporter(true);
    private Reporter nonSystemReporter = createDefaultTestReporter(false);

    @Test
    public void shouldUpdateDefaultIdp() throws Exception {

        when(reporterService.findAll()).thenReturn(Flowable.just(systemReporter));
        when(reporterService.createReporterConfig(any())).thenReturn("new-config-created");
        when(reporterService.update(any(), anyString(), any(UpdateReporter.class), eq(true))).thenReturn(changeReporterConfig(true));

        TestObserver<Void> observer = defaultReporterUpgrader.upgrade().test();
        observer.await(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(reporterService, times(1)).findAll();
        verify(reporterService, times(1))
                .update(any(), anyString(), argThat(upReporter -> upReporter.getConfiguration().equals("new-config-created")), anyBoolean());

    }

    @Test
    public void shouldNotUpdateDefaultIdp() throws Exception {

        when(reporterService.findAll()).thenReturn(Flowable.just(nonSystemReporter));

        TestObserver<Void> observer = defaultReporterUpgrader.upgrade().test();
        observer.await(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(reporterService, times(1)).findAll();
        verify(reporterService, never())
                .update(any(), anyString(), any(UpdateReporter.class), anyBoolean());
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
        reporter.setReference(Reference.domain("domain-id"));

        return reporter;
    }

}
