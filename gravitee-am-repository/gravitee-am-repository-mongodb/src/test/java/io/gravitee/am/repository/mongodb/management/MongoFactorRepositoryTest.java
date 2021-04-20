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

import io.gravitee.am.model.Factor;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.reactivex.observers.TestObserver;
import java.util.Set;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoFactorRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private FactorRepository factorRepository;

    @Override
    public String collectionName() {
        return "factors";
    }

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create factor
        Factor factor = new Factor();
        factor.setName("testName");
        factor.setDomain("testDomain");
        factorRepository.create(factor).blockingGet();

        // fetch factors
        TestObserver<Set<Factor>> testObserver = factorRepository.findByDomain("testDomain").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(factors -> factors.size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create factor
        Factor factor = new Factor();
        factor.setName("testName");
        Factor factorCreated = factorRepository.create(factor).blockingGet();

        // fetch factor
        TestObserver<Factor> testObserver = factorRepository.findById(factorCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(f -> f.getName().equals("testName"));
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        factorRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Factor factor = new Factor();
        factor.setName("testName");

        TestObserver<Factor> testObserver = factorRepository.create(factor).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(f -> f.getName().equals(factor.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create factor
        Factor factor = new Factor();
        factor.setName("testName");
        Factor factorCreated = factorRepository.create(factor).blockingGet();

        // update factor
        Factor updateFactor = new Factor();
        updateFactor.setId(factorCreated.getId());
        updateFactor.setName("testUpdatedName");

        TestObserver<Factor> testObserver = factorRepository.update(updateFactor).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(f -> f.getName().equals(updateFactor.getName()));
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create factor
        Factor factor = new Factor();
        factor.setName("testName");
        Factor factorCreated = factorRepository.create(factor).blockingGet();

        // fetch factor
        TestObserver<Factor> testObserver = factorRepository.findById(factorCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(f -> f.getName().equals(factorCreated.getName()));

        // delete factor
        TestObserver testObserver1 = factorRepository.delete(factorCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch factor
        factorRepository.findById(factorCreated.getId()).test().assertEmpty();
    }
}
