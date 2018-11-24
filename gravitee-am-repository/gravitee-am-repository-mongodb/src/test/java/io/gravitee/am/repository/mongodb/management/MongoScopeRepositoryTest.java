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

import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ScopeRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoScopeRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private ScopeRepository scopeRepository;

    @Override
    public String collectionName() {
        return "scopes";
    }

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create scope
        Scope scope = new Scope();
        scope.setName("testName");
        scope.setDomain("testDomain");
        scopeRepository.create(scope).blockingGet();

        // fetch scopes
        TestObserver<Set<Scope>> testObserver = scopeRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopes -> scopes.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create scope
        Scope scope = new Scope();
        scope.setName("testName");
        Scope scopeCreated = scopeRepository.create(scope).blockingGet();

        // fetch scope
        TestObserver<Scope> testObserver = scopeRepository.findById(scopeCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        scopeRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Scope scope = new Scope();
        scope.setName("testName");
        scope.setSystem(true);
        scope.setClaims(Collections.emptyList());
        TestObserver<Scope> testObserver = scopeRepository.create(scope).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getName().equals(scope.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create scope
        Scope scope = new Scope();
        scope.setName("testName");
        Scope scopeCreated = scopeRepository.create(scope).blockingGet();

        // update scope
        Scope updatedScope = new Scope();
        updatedScope.setId(scopeCreated.getId());
        updatedScope.setName("testUpdatedName");

        TestObserver<Scope> testObserver = scopeRepository.update(updatedScope).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getName().equals(updatedScope.getName()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create scope
        Scope scope = new Scope();
        scope.setName("testName");
        Scope scopeCreated = scopeRepository.create(scope).blockingGet();

        // fetch scope
        TestObserver<Scope> testObserver = scopeRepository.findById(scopeCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getName().equals(scope.getName()));

        // delete scope
        TestObserver testObserver1 = scopeRepository.delete(scopeCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch scope
        scopeRepository.findById(scopeCreated.getId()).test().assertEmpty();
    }

}
