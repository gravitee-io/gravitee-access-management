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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.ExtensionGrant;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExtensionGrantRepositoryTest extends AbstractManagementTest {

    @Autowired
    private ExtensionGrantRepository extensionGrantRepository;

    @Test
    public void testFindByDomain(){
        // create extension grant
        ExtensionGrant extensionGrant = buildExtensionGrant();
        extensionGrant.setDomain("testDomain");
        ExtensionGrant createdGrant = extensionGrantRepository.create(extensionGrant).blockingGet();

        ExtensionGrant excludedElement = buildExtensionGrant();
        extensionGrantRepository.create(excludedElement).blockingGet();

        // fetch extension grants
        TestSubscriber<ExtensionGrant> testSubscriber = extensionGrantRepository.findByDomain("testDomain").test();
        testSubscriber.awaitDone(10, TimeUnit.SECONDS);

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(grant -> grant.getId().equals(createdGrant.getId()));
    }

    @Test
    public void testFindById(){
        // create extension grant
        ExtensionGrant extensionGrant = buildExtensionGrant();
        ExtensionGrant extensionGrantCreated = extensionGrantRepository.create(extensionGrant).blockingGet();

        // fetch extension grant
        TestObserver<ExtensionGrant> testObserver = extensionGrantRepository.findById(extensionGrantCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getId().equals(extensionGrantCreated.getId()));
        testObserver.assertValue(e -> e.getName().equals(extensionGrantCreated.getName()));
        testObserver.assertValue(e -> e.getConfiguration().equals(extensionGrantCreated.getConfiguration()));
        testObserver.assertValue(e -> e.getDomain().equals(extensionGrantCreated.getDomain()));
        testObserver.assertValue(e -> e.getGrantType().equals(extensionGrantCreated.getGrantType()));
        testObserver.assertValue(e -> e.getType().equals(extensionGrantCreated.getType()));
        testObserver.assertValue(e -> e.getIdentityProvider().equals(extensionGrantCreated.getIdentityProvider()));
        testObserver.assertValue(e -> e.isCreateUser() == extensionGrantCreated.isCreateUser());
        testObserver.assertValue(e -> e.isUserExists() == extensionGrantCreated.isUserExists());
    }

    private ExtensionGrant buildExtensionGrant() {
        ExtensionGrant extensionGrant = new ExtensionGrant();
        String randomString = UUID.randomUUID().toString();
        extensionGrant.setName("name"+randomString);
        extensionGrant.setConfiguration("conf"+randomString);
        extensionGrant.setType("type"+randomString);
        extensionGrant.setDomain("domain"+randomString);
        extensionGrant.setGrantType("granttype"+randomString);
        extensionGrant.setIdentityProvider("idp"+randomString);
        extensionGrant.setUserExists(true);
        extensionGrant.setCreateUser(false);
        extensionGrant.setCreatedAt(new Date());
        extensionGrant.setUpdatedAt(new Date());
        return extensionGrant;
    }

    @Test
    public void testNotFoundById(){
        var observer = extensionGrantRepository.findById("test").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void testCreate(){
        ExtensionGrant extensionGrant = buildExtensionGrant();

        TestObserver<ExtensionGrant> testObserver = extensionGrantRepository.create(extensionGrant).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals(extensionGrant.getName()));
    }

    @Test
    public void testUpdate(){
        // create extension grant
        ExtensionGrant extensionGrant = buildExtensionGrant();
        ExtensionGrant extensionGrantCreated = extensionGrantRepository.create(extensionGrant).blockingGet();

        // update extension grant
        ExtensionGrant updatedExtension = new ExtensionGrant();
        updatedExtension.setId(extensionGrantCreated.getId());
        updatedExtension.setName("testUpdatedName");

        TestObserver<ExtensionGrant> testObserver = extensionGrantRepository.update(updatedExtension).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals(updatedExtension.getName()));
    }

    @Test
    public void testDelete(){
        // create extension grant
        ExtensionGrant extensionGrant = buildExtensionGrant();
        ExtensionGrant extensionGrantCreated = extensionGrantRepository.create(extensionGrant).blockingGet();

        // fetch extension grant
        TestObserver<ExtensionGrant> testObserver = extensionGrantRepository.findById(extensionGrantCreated.getId()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(e -> e.getName().equals(extensionGrantCreated.getName()));

        // delete extension grant
        TestObserver testObserver1 = extensionGrantRepository.delete(extensionGrantCreated.getId()).test();
        testObserver1.awaitDone(10, TimeUnit.SECONDS);

        // fetch extension grant
        var observer = extensionGrantRepository.findById(extensionGrantCreated.getId()).test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }
}
