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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.Role;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.RoleRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoRoleRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create role
        Role role = new Role();
        role.setName("testName");
        role.setDomain("testDomain");
        roleRepository.create(role).blockingGet();

        // fetch roles
        TestObserver<Set<Role>> testObserver = roleRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(roles -> roles.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create role
        Role role = new Role();
        role.setName("testName");
        Role roleCreated = roleRepository.create(role).blockingGet();

        // fetch role
        TestObserver<Role> testObserver = roleRepository.findById(roleCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        roleRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Role role = new Role();
        role.setName("testName");
        TestObserver<Role> testObserver = roleRepository.create(role).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getName().equals(role.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create role
        Role role = new Role();
        role.setName("testName");
        Role roleCreated = roleRepository.create(role).blockingGet();

        // update role
        Role updatedRole = new Role();
        updatedRole.setId(roleCreated.getId());
        updatedRole.setName("testUpdatedName");

        TestObserver<Role> testObserver = roleRepository.update(updatedRole).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getName().equals(updatedRole.getName()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create role
        Role role = new Role();
        role.setName("testName");
        Role roleCreated = roleRepository.create(role).blockingGet();

        // fetch role
        TestObserver<Role> testObserver = roleRepository.findById(roleCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(r -> r.getName().equals(roleCreated.getName()));

        // delete role
        TestObserver testObserver1 = roleRepository.delete(roleCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch role
        roleRepository.findById(roleCreated.getId()).test().assertEmpty();
    }

}
