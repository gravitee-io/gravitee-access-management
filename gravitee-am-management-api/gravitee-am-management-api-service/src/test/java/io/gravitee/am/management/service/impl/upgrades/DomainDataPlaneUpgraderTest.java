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

import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Domain;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainDataPlaneUpgraderTest {

    @Mock
    private DomainService domainService;

    @InjectMocks
    private DomainDataPlaneUpgrader domainDataPlaneUpgrader;

    @Test
    void shouldAssignDefaultDataPlaneId() {
        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setName("domain");
        when(domainService.listAll()).thenReturn(Flowable.just(domain));
        ArgumentCaptor<Domain> argumentCaptor = ArgumentCaptor.forClass(Domain.class);
        when(domainService.update(any(), argumentCaptor.capture())).thenAnswer(i -> Single.just(i.getArgument(1)));
        assertTrue(domainDataPlaneUpgrader.upgrade());

        assertEquals("default", argumentCaptor.getValue().getDataPlaneId());
    }

    @Test
    void shouldNotChangeDataPlaneId() {
        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setName("domain");
        domain.setDataPlaneId("data-plane-id");
        when(domainService.listAll()).thenReturn(Flowable.just(domain));
        assertTrue(domainDataPlaneUpgrader.upgrade());

        verify(domainService, never()).update(any(),any());
    }

}
