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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Role;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoOrganizationRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Override
    public String collectionName() {
        return "organizations";
    }

    @Test
    public void testFindById() {
        Organization organization = new Organization();
        organization.setName("testName");

        // TODO: find another way to inject data in DB. Avoid to rely on class under test for that.
        Organization organizationCreated = organizationRepository.create(organization).blockingGet();

        TestObserver<Organization> obs = organizationRepository.findById(organizationCreated.getId()).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getId().equals(organizationCreated.getId()));
    }

    @Test
    public void testNotFoundById() {
        organizationRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        Organization organization = new Organization();
        organization.setName("testName");

        TestObserver<Organization> obs = organizationRepository.create(organization).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(organization.getName()) && o.getId() != null);
    }

    @Test
    public void testUpdate() {
        Organization organization = new Organization();
        organization.setName("testName");
        Organization organizationCreated = organizationRepository.create(organization).blockingGet();

        Organization organizationUpdated = new Organization();
        organizationUpdated.setId(organizationCreated.getId());
        organizationUpdated.setName("testNameUpdated");

        TestObserver<Organization> obs = organizationRepository.update(organizationUpdated).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(organizationUpdated.getName()) && o.getId().equals(organizationCreated.getId()));
    }

    @Test
    public void testDelete() {
        Organization organization = new Organization();
        organization.setName("testName");
        Organization organizationCreated = organizationRepository.create(organization).blockingGet();

        assertNotNull(organizationRepository.findById(organizationCreated.getId()).blockingGet());

        TestObserver<Void> obs = organizationRepository.delete(organizationCreated.getId()).test();
        obs.awaitTerminalEvent();
        obs.assertNoValues();

        assertNull(organizationRepository.findById(organizationCreated.getId()).blockingGet());
    }
}
