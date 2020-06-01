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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    public void testFindByDomain() {
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
    public void testFindByDomainAndKey() {
        // create scope
        Scope scope = new Scope();
        scope.setName("firstOne");
        scope.setKey("one");
        scope.setDomain("testDomain");
        scopeRepository.create(scope).blockingGet();

        scope.setName("anotherOne");
        scope.setDomain("another");
        scopeRepository.create(scope).blockingGet();


        // fetch scopes
        TestObserver<Scope> testObserver = scopeRepository.findByDomainAndKey("testDomain","one").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(result -> "firstOne".equals(result.getName()));
    }

    @Test
    public void testFindByDomainAndKeys() {
        // create scope
        Scope scope = new Scope();
        scope.setName("firstOne");
        scope.setKey("one");
        scope.setDomain("testDomain");
        Scope scopeCreated1 = scopeRepository.create(scope).blockingGet();

        scope.setName("anotherOne");
        scope.setDomain("another");
        scopeRepository.create(scope).blockingGet();

        scope.setName("secondOne");
        scope.setKey("two");
        scope.setDomain("testDomain");
        Scope scopeCreated2 = scopeRepository.create(scope).blockingGet();

        // fetch scopes
        TestObserver<List<Scope>> testObserver = scopeRepository.findByDomainAndKeys("testDomain", Arrays.asList("one","two","three")).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(scopes -> scopes.size()==2 &&
                scopes.stream()
                .map(Scope::getId)
                .collect(Collectors.toList())
                .containsAll(Arrays.asList(scopeCreated1.getId(), scopeCreated2.getId()))
        );
    }

    @Test
    public void testFindById() {
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
    public void testCreate() {
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
    public void testUpdate() {
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
    public void testDelete() {
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
