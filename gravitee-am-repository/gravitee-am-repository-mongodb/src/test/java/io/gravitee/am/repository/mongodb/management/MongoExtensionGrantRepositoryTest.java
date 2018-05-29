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

import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ExtensionGrantRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoExtensionGrantRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private ExtensionGrantRepository extensionGrantRepository;

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create extension grant
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setName("testName");
        extensionGrant.setDomain("testDomain");
        extensionGrantRepository.create(extensionGrant).blockingGet();

        // fetch extension grants
        TestObserver<Set<ExtensionGrant>> testObserver = extensionGrantRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(extensionGrants -> extensionGrants.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create extension grant
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setName("testName");
        ExtensionGrant extensionGrantCreated = extensionGrantRepository.create(extensionGrant).blockingGet();

        // fetch extension grant
        TestObserver<ExtensionGrant> testObserver = extensionGrantRepository.findById(extensionGrantCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        extensionGrantRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setName("testName");

        TestObserver<ExtensionGrant> testObserver = extensionGrantRepository.create(extensionGrant).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals(extensionGrant.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create extension grant
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setName("testName");
        ExtensionGrant extensionGrantCreated = extensionGrantRepository.create(extensionGrant).blockingGet();

        // update extension grant
        ExtensionGrant updatedExtension = new ExtensionGrant();
        updatedExtension.setId(extensionGrantCreated.getId());
        updatedExtension.setName("testUpdatedName");

        TestObserver<ExtensionGrant> testObserver = extensionGrantRepository.update(updatedExtension).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals(updatedExtension.getName()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create extension grant
        ExtensionGrant extensionGrant = new ExtensionGrant();
        extensionGrant.setName("testName");
        ExtensionGrant extensionGrantCreated = extensionGrantRepository.create(extensionGrant).blockingGet();

        // fetch extension grant
        TestObserver<ExtensionGrant> testObserver = extensionGrantRepository.findById(extensionGrantCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals(extensionGrantCreated.getName()));

        // delete extension grant
        TestObserver testObserver1 = extensionGrantRepository.delete(extensionGrantCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch extension grant
        extensionGrantRepository.findById(extensionGrantCreated.getId()).test().assertEmpty();
    }

}
