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

import io.gravitee.am.management.core.event.DomainEvent;
import io.gravitee.am.management.service.impl.upgrades.DeployAdminDomainUpgrader;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.DomainService;
import io.gravitee.common.event.EventManager;
import io.reactivex.Maybe;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeployAdminDomainUpgraderTest {

    private final static String ADMIN_DOMAIN = "admin";

    @InjectMocks
    private DeployAdminDomainUpgrader deployAdminDomainUpgrader = new DeployAdminDomainUpgrader();

    @Mock
    private DomainService domainService;

    @Mock
    private EventManager eventManager;

    @Test
    public void shouldDeployAdminDomain() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.just(adminDomain));
        deployAdminDomainUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(eventManager, times(1)).publishEvent(eq(DomainEvent.DEPLOY), any(Domain.class));
    }

    @Test
    public void shouldDeployAdminDomain_domainNotFound() {
        final Domain adminDomain = new Domain();
        adminDomain.setId(ADMIN_DOMAIN);
        adminDomain.setName("ADMIN");

        when(domainService.findById(ADMIN_DOMAIN)).thenReturn(Maybe.empty());
        deployAdminDomainUpgrader.upgrade();

        verify(domainService, times(1)).findById(ADMIN_DOMAIN);
        verify(eventManager,never()).publishEvent(eq(DomainEvent.DEPLOY), any(Domain.class));
    }

}
