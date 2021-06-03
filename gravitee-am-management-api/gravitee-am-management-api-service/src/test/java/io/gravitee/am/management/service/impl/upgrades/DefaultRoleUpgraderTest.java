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

import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.service.RoleService;
import io.reactivex.Completable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.when;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultRoleUpgraderTest {

    @Mock
    private RoleService roleService;

    private DefaultRoleUpgrader cut;

    @Before
    public void before() {
        cut = new DefaultRoleUpgrader(roleService);
    }

    @Test
    public void shouldCreateSystemRoles() {
        when(roleService.createOrUpdateSystemRoles()).thenReturn(Completable.complete());
        cut.upgrade();
    }

    @Test
    public void shouldCreateSystemRoles_technicalError() {
        when(roleService.createOrUpdateSystemRoles()).thenReturn(Completable.error(TechnicalException::new));
        assertFalse(cut.upgrade());
    }
}
