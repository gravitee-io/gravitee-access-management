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

import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.reactivex.observers.TestObserver;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoIdentityProviderRepositoryTest extends AbstractManagementRepositoryTest {

    public static final String ORGANIZATION_ID = "orga#1";

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Override
    public String collectionName() {
        return "identities";
    }

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create idp
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("testName");
        identityProvider.setReferenceType(ReferenceType.DOMAIN);
        identityProvider.setReferenceId("testDomain");
        identityProviderRepository.create(identityProvider).blockingGet();

        // fetch idps
        TestObserver<Set<IdentityProvider>> testObserver = identityProviderRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idps -> idps.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create idp
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("testName");
        IdentityProvider identityProviderCreated = identityProviderRepository.create(identityProvider).blockingGet();

        // fetch idp
        TestObserver<IdentityProvider> testObserver = identityProviderRepository.findById(identityProviderCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getName().equals("testName"));
    }

    @Test
    public void testFindById_refrenceType() throws TechnicalException {
        // create idp
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("testName");
        identityProvider.setReferenceType(ReferenceType.ORGANIZATION);
        identityProvider.setReferenceId(ORGANIZATION_ID);
        IdentityProvider identityProviderCreated = identityProviderRepository.create(identityProvider).blockingGet();

        // fetch idp
        TestObserver<IdentityProvider> testObserver = identityProviderRepository
            .findById(ReferenceType.ORGANIZATION, ORGANIZATION_ID, identityProviderCreated.getId())
            .test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        identityProviderRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("testName");
        identityProvider.setMappers(Collections.singletonMap("username", "johndoe"));
        identityProvider.setRoleMapper(Collections.singletonMap("username=johndoe", new String[] { "dev", "admin" }));
        TestObserver<IdentityProvider> testObserver = identityProviderRepository.create(identityProvider).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(
            idp ->
                idp.getName().equals(identityProvider.getName()) &&
                idp.getMappers().containsKey("username") &&
                idp.getRoleMapper().containsKey("username=johndoe")
        );
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create idp
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("testName");
        IdentityProvider identityProviderCreated = identityProviderRepository.create(identityProvider).blockingGet();

        // update idp
        IdentityProvider updatedIdentityProvider = new IdentityProvider();
        updatedIdentityProvider.setId(identityProviderCreated.getId());
        updatedIdentityProvider.setName("testUpdatedName");

        TestObserver<IdentityProvider> testObserver = identityProviderRepository.update(updatedIdentityProvider).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getName().equals(updatedIdentityProvider.getName()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create idp
        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setName("testName");
        IdentityProvider identityProviderCreated = identityProviderRepository.create(identityProvider).blockingGet();

        // fetch idp
        TestObserver<IdentityProvider> testObserver = identityProviderRepository.findById(identityProviderCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(idp -> idp.getName().equals(identityProvider.getName()));

        // delete idp
        TestObserver testObserver1 = identityProviderRepository.delete(identityProviderCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch idp
        identityProviderRepository.findById(identityProviderCreated.getId()).test().assertEmpty();
    }
}
