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

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.management.service.impl.upgrades.DefaultOrganizationUpgrader;
import io.gravitee.am.model.Organization;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.*;
import io.reactivex.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultOrganizationUpgraderTest {

   @Mock
   private OrganizationService organizationService;

   private DefaultOrganizationUpgrader cut;

   @Before
   public void before() {

       cut = new DefaultOrganizationUpgrader(organizationService);
   }

    @Test
    public void shouldCreateDefaultOrganization() {

       when(organizationService.createDefault()).thenReturn(Maybe.just(new Organization()));
       assertTrue(cut.upgrade());
    }

    @Test
    public void shouldCreateDefaultOrganization_alreadyCreated() {

        when(organizationService.createDefault()).thenReturn(Maybe.empty());
        assertTrue(cut.upgrade());
    }

    @Test
    public void shouldCreateDefaultOrganization_technicalError() {

        when(organizationService.createDefault()).thenReturn(Maybe.error(TechnicalException::new));
        assertFalse(cut.upgrade());
    }
}