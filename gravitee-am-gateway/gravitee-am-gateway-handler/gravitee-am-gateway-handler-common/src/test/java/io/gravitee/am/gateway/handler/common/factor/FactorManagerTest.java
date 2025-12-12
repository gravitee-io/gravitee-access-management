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
package io.gravitee.am.gateway.handler.common.factor;

import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.gateway.handler.common.factor.impl.FactorManagerImpl;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.factor.core.FactorPluginManager;
import io.gravitee.am.service.FactorService;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class FactorManagerTest {

    public static final String DOMAIN_ID = "domainid";
    public static final String FACTOR_ID = "factor-id";
    @Mock
    private FactorService factorService;

    @Mock
    private Domain domain;

    @Mock
    private FactorPluginManager factorPluginManager;

    @Mock
    private DomainReadinessService domainReadinessService;

    @InjectMocks
    private FactorManagerImpl factorMng = new FactorManagerImpl();

    @Before
    public void prepare() {
        when(domain.getId()).thenReturn(DOMAIN_ID);
        final Factor factor = new Factor();
        factor.setId(FACTOR_ID);
        when(factorService.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(factor));
        when(factorPluginManager.create(any())).thenReturn(mock(FactorProvider.class));
        factorMng.afterPropertiesSet();
    }

    @Test
    public void shouldProvideActiveFactor() {
        Client client = new Client();
        ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
        applicationFactorSettings.setId(FACTOR_ID);
        FactorSettings factorSettings = new FactorSettings();
        factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
        client.setFactorSettings(factorSettings);

        assertTrue(factorMng.getClientFactor(client, FACTOR_ID).isPresent());
    }

    @Test
    public void shouldNotProvideActiveFactor() {
        Client client = new Client();
        ApplicationFactorSettings applicationFactorSettings = new ApplicationFactorSettings();
        applicationFactorSettings.setId(FACTOR_ID);
        FactorSettings factorSettings = new FactorSettings();
        factorSettings.setApplicationFactors(List.of(applicationFactorSettings));
        client.setFactorSettings(factorSettings);

        assertFalse(factorMng.getClientFactor(client, "inactive-factor").isPresent());
    }

    @Test
    public void shouldNotProvideActiveFactor_noClient() {
        assertFalse(factorMng.getClientFactor(null, "inactive-factor").isPresent());
    }
}
