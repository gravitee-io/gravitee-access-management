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
package io.gravitee.am.gateway.reactor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.gravitee.am.common.event.DomainEvent;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.gateway.reactor.impl.DefaultSecurityDomainManager;
import io.gravitee.am.model.Domain;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class SecurityDomainManagerTest {

    @InjectMocks
    private SecurityDomainManager securityDomainManager = new DefaultSecurityDomainManager();

    @Mock
    private EventManager eventManager;

    @Test
    public void shouldNotDeploy_domainIsDisabled() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(false);

        securityDomainManager.deploy(domain);
        Assert.assertTrue(securityDomainManager.get(domain.getId()) == null);
        verify(eventManager, never()).publishEvent(eq(DomainEvent.DEPLOY), any());
    }

    @Test
    public void shouldDeploy_domainIsEnabled() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);

        securityDomainManager.deploy(domain);
        Assert.assertFalse(securityDomainManager.get(domain.getId()) == null);
        verify(eventManager, times(1)).publishEvent(eq(DomainEvent.DEPLOY), any());
    }

    @Test
    public void shouldNotUpdate_domainIsDisabled_noCurrentDomain() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(false);

        securityDomainManager.update(domain);
        Assert.assertTrue(securityDomainManager.get(domain.getId()) == null);
        verify(eventManager, never()).publishEvent(eq(DomainEvent.UPDATE), any());
    }

    @Test
    public void shouldNotUpdate_domainIsDisabled_existingDomain() {
        final Domain existingDomain = new Domain();
        existingDomain.setId("domain-1");
        existingDomain.setEnabled(true);
        securityDomainManager.deploy(existingDomain);

        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(false);
        securityDomainManager.update(domain);

        verify(eventManager, times(1)).publishEvent(eq(DomainEvent.DEPLOY), any());
        verify(eventManager, times(1)).publishEvent(eq(DomainEvent.UNDEPLOY), any());
    }

    @Test
    public void shouldUpdate_domainIsEnabled() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);
        securityDomainManager.update(domain);

        Assert.assertFalse(securityDomainManager.get(domain.getId()) == null);
        verify(eventManager, times(1)).publishEvent(eq(DomainEvent.UPDATE), any());
    }

    @Test
    public void shouldNotUndeployed_domainNotExist() {
        final Domain domain = new Domain();
        domain.setId("domain-1");
        domain.setEnabled(true);
        securityDomainManager.undeploy(domain.getId());

        verify(eventManager, never()).publishEvent(eq(DomainEvent.UNDEPLOY), any());
    }

    @Test
    public void shouldUndeployed() {
        final Domain existingDomain = new Domain();
        existingDomain.setId("domain-1");
        existingDomain.setEnabled(true);
        securityDomainManager.deploy(existingDomain);

        securityDomainManager.undeploy(existingDomain.getId());

        verify(eventManager, times(1)).publishEvent(eq(DomainEvent.UNDEPLOY), any());
    }
}
