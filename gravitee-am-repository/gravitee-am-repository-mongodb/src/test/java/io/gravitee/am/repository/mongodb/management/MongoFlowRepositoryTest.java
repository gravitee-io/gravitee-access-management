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

import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.repository.management.api.FlowRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoFlowRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private FlowRepository flowRepository;

    @Override
    public String collectionName() {
        return "flows";
    }

    @Test
    public void testFindById() {
        Flow flow = new Flow();
        flow.setName("ROOT");

        Flow flowCreated = flowRepository.create(flow).blockingGet();

        TestObserver<Flow> obs = flowRepository.findById(flowCreated.getId()).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getId().equals(flowCreated.getId()));
    }

    @Test
    public void testNotFoundById() {
        flowRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() {
        Flow flow = new Flow();
        flow.setName("ROOT");

        TestObserver<Flow> obs = flowRepository.create(flow).test();
        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(flow.getName()) && o.getId() != null);
    }

    @Test
    public void testUpdate() {
        Flow flow = new Flow();
        flow.setName("ROOT");
        Flow flowCreated = flowRepository.create(flow).blockingGet();

        Flow flowUpdated = new Flow();
        flowUpdated.setId(flowCreated.getId());
        flowUpdated.setName("testNameUpdated");

        TestObserver<Flow> obs = flowRepository.update(flowUpdated).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(flowUpdated.getName()) && o.getId().equals(flowCreated.getId()));
    }

    @Test
    public void testDelete() {
        Flow flow = new Flow();
        flow.setName("ROOT");
        Flow flowCreated = flowRepository.create(flow).blockingGet();

        assertNotNull(flowRepository.findById(flowCreated.getId()).blockingGet());

        TestObserver<Void> obs = flowRepository.delete(flowCreated.getId()).test();
        obs.awaitTerminalEvent();
        obs.assertNoValues();

        assertNull(flowRepository.findById(flowCreated.getId()).blockingGet());
    }
}
