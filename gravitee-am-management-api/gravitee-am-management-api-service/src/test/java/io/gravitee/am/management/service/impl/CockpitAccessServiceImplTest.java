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
package io.gravitee.am.management.service.impl;

import io.gravitee.am.management.service.model.AccessPointTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class CockpitAccessServiceImplTest {

    private static final String ENV_HOST = "{organizationId}.env.gravitee.io";

    @Mock
    private ConfigurableEnvironment environment;

    private CockpitAccessServiceImpl cut;

    @BeforeEach
    public void beforeEach() {
        cut = new CockpitAccessServiceImpl(environment);
    }

    @Test
    void should_not_populate_templates_when_not_managed_cloud() {
        when(environment.getProperty("cloud.enabled", Boolean.class)).thenReturn(false);

        cut.afterPropertiesSet();

        assertTrue(cut.getAccessPointsTemplate().isEmpty());
    }

    @Test
    void should_populate_templates_when_managed_cloud_with_valid_hosts() {
        mockManagedCloud();
        when(
                environment.getProperty(
                        "installation.managed.accessPoints.environment.gateway.host"
                )
        ).thenReturn(ENV_HOST);
        when(
                environment.getProperty(
                        "installation.managed.accessPoints.environment.gateway.secured",
                        Boolean.class,
                        true
                )
        ).thenReturn(false);

        cut.afterPropertiesSet();

        Map<AccessPointTemplate.Type, List<AccessPointTemplate>> templates = cut.getAccessPointsTemplate();
        assertEquals(1, templates.size());

        AccessPointTemplate envTemplate = templates.get(AccessPointTemplate.Type.ENVIRONMENT).get(0);
        assertEquals(AccessPointTemplate.Target.GATEWAY, envTemplate.getTarget());
        assertEquals(ENV_HOST, envTemplate.getHost());
        assertTrue(!envTemplate.isSecured());
    }

    @Test
    void should_throw_when_managed_cloud_without_any_access_point_configured() {
        mockManagedCloud();

        assertThrows(RuntimeException.class, () -> cut.afterPropertiesSet());
    }

    @Test
    void should_throw_when_host_is_malformed() {
        mockManagedCloud();
        when(
                environment.getProperty(
                        "installation.managed.accessPoints.environment.gateway.host"
                )
        ).thenReturn("not a valid host!!");

        assertThrows(RuntimeException.class, () -> cut.afterPropertiesSet());
    }

    private void mockManagedCloud() {
        when(environment.getProperty("cloud.enabled", Boolean.class)).thenReturn(true);
        when(environment.getProperty("installation.type", "standalone")).thenReturn("managed");
    }
}
