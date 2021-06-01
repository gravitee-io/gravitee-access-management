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

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowRepositoryTest extends AbstractManagementTest {

    @Autowired
    private FlowRepository flowRepository;

    @Test
    public void testFindAll() {
        Flow flow = buildFlow(1,1);
        flow.setReferenceType(ReferenceType.DOMAIN);
        flow.setReferenceId("DOMAIN1");
        Flow flow2 = buildFlow(2,3);
        flow2.setReferenceType(ReferenceType.DOMAIN);
        flow2.setReferenceId("DOMAIN1");

        Flow flowCreated = flowRepository.create(flow).blockingGet();
        Flow flow2Created = flowRepository.create(flow2).blockingGet();

        TestObserver<List<Flow>> obs = flowRepository.findAll(ReferenceType.DOMAIN, "DOMAIN1").toList().test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(list -> list.size() == 2);
        obs.assertValue(list -> list.get(0).getId().equals(flowCreated.getId()) ? list.get(0).getPost().size() == 1 : list.get(0).getPost().size() == 3);
        obs.assertValue(list -> list.get(0).getId().equals(flowCreated.getId()) ? list.get(0).getPre().size() == 1 : list.get(0).getPre().size() == 2);
    }

    @Test
    public void testFindByApplication() {
        Flow flow = buildFlow(1,1);
        flow.setReferenceType(ReferenceType.DOMAIN);
        flow.setReferenceId("DOMAIN1");
        Flow flow2 = buildFlow(2,3);
        flow2.setReferenceType(ReferenceType.DOMAIN);
        flow2.setReferenceId("DOMAIN1");
        flow2.setApplication("APP1");

        Flow flowCreated = flowRepository.create(flow).blockingGet();
        Flow flow2Created = flowRepository.create(flow2).blockingGet();

        TestObserver<List<Flow>> obs = flowRepository.findByApplication(ReferenceType.DOMAIN, "DOMAIN1", "APP1").toList().test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(list -> list.size() == 1);
        obs.assertValue(list -> list.get(0).getId().equals(flow2Created.getId()));
    }

    @Test
    public void testFindByRefAndId() {
        Flow flow = buildFlow(1,1);
        flow.setReferenceType(ReferenceType.DOMAIN);
        flow.setReferenceId("DOMAIN1");

        Flow flowCreated = flowRepository.create(flow).blockingGet();

        TestObserver<Flow> obs = flowRepository.findById(ReferenceType.DOMAIN, "DOMAIN1", flowCreated.getId()).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        assertFlowEqualsTo(flow, flowCreated, obs);
    }

    protected void assertFlowEqualsTo(Flow flow, Flow flowCreated, TestObserver<Flow> obs) {
        obs.assertValue(o -> o.getId().equals(flowCreated.getId()));
        obs.assertValue(o -> o.getName().equals(flow.getName()));
        obs.assertValue(o -> o.getType().equals(flow.getType()));
        obs.assertValue(o -> o.getCondition().equals(flow.getCondition()));
        obs.assertValue(o -> o.getOrder().equals(flow.getOrder()));
        obs.assertValue(o -> o.getReferenceId().equals(flow.getReferenceId()));
        obs.assertValue(o -> o.getReferenceType().equals(flow.getReferenceType()));
        obs.assertValue(o -> o.isEnabled() == flow.isEnabled());

        obs.assertValue(o -> o.getPre().size() == flow.getPre().size());
        obs.assertValue(o -> o.getPost().size() == flow.getPost().size());
        // step order should be preserved
        obs.assertValue(o -> {
            boolean result = true;
            for (int i = 0; i < o.getPre().size(); ++i) {
                result = result && o.getPre().get(i).getName().equals(flow.getPre().get(i).getName());
                result = result && o.getPre().get(i).getConfiguration().equals(flow.getPre().get(i).getConfiguration());
                result = result && o.getPre().get(i).getPolicy().equals(flow.getPre().get(i).getPolicy());
                result = result && o.getPre().get(i).getDescription().equals(flow.getPre().get(i).getDescription());
            }
            return result;
        });
        obs.assertValue(o -> {
            boolean result = true;
            for (int i = 0; i < o.getPost().size(); ++i) {
                result = result && o.getPost().get(i).getName().equals(flow.getPost().get(i).getName());
                result = result && o.getPost().get(i).getConfiguration().equals(flow.getPost().get(i).getConfiguration());
                result = result && o.getPost().get(i).getPolicy().equals(flow.getPost().get(i).getPolicy());
                result = result && o.getPost().get(i).getDescription().equals(flow.getPost().get(i).getDescription());
            }
            return result;
        });
    }

    @Test
    public void testFindByWithStep() {
        Flow flow = buildFlow(1,1);

        Flow flowCreated = flowRepository.create(flow).blockingGet();

        TestObserver<Flow> obs = flowRepository.findById(flowCreated.getId()).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        assertFlowEqualsTo(flow, flowCreated, obs);
    }

    @Test
    public void testFindByWithMultipleStep() {
        Flow flow = buildFlow(4,3);

        Flow flowCreated = flowRepository.create(flow).blockingGet();

        TestObserver<Flow> obs = flowRepository.findById(flowCreated.getId()).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        assertFlowEqualsTo(flow, flowCreated, obs);
    }

    @Test
    public void testFindById() {
        Flow flow = new Flow();
        flow.setName("ROOT");
        flow.setOrder(5);

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
        flow.setOrder(5);

        TestObserver<Flow> obs = flowRepository.create(flow).test();
        obs.awaitTerminalEvent();
        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(flow.getName()) && o.getId() != null);
    }

    @Test
    public void testUpdateWithStep() {
        Flow flow = buildFlow(1,1);
        Flow flowCreated = flowRepository.create(flow).blockingGet();

        Flow flowUpdated = buildFlow(2,3);
        flowUpdated.setId(flowCreated.getId());
        flowUpdated.setName("testNameUpdated");

        TestObserver<Flow> obs = flowRepository.update(flowUpdated).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(flowUpdated.getName()) && o.getId().equals(flowCreated.getId()));
        obs.assertValue(o -> o.getPre().size() == 2);
        obs.assertValue(o -> o.getPost().size() == 3);
    }

    @Test
    public void testUpdateWithoutStep() {
        Flow flow = buildFlow(1,1);
        Flow flowCreated = flowRepository.create(flow).blockingGet();

        Flow flowUpdated = buildFlow(0,0);
        flowUpdated.setId(flowCreated.getId());
        flowUpdated.setName("testNameUpdated");

        TestObserver<Flow> obs = flowRepository.update(flowUpdated).test();
        obs.awaitTerminalEvent();

        obs.assertComplete();
        obs.assertNoErrors();
        obs.assertValue(o -> o.getName().equals(flowUpdated.getName()) && o.getId().equals(flowCreated.getId()));
        obs.assertValue(o -> o.getPre().size() == 0);
        obs.assertValue(o -> o.getPost().size() == 0);
    }

    @Test
    public void testDelete() {
        Flow flow = new Flow();
        flow.setName("ROOT");
        flow.setOrder(5);
        Flow flowCreated = flowRepository.create(flow).blockingGet();

        assertNotNull(flowRepository.findById(flowCreated.getId()).blockingGet());

        TestObserver<Void> obs = flowRepository.delete(flowCreated.getId()).test();
        obs.awaitTerminalEvent();
        obs.assertNoValues();

        assertNull(flowRepository.findById(flowCreated.getId()).blockingGet());
    }

    private Flow buildFlow(int nbPreSteps, int nbPostSteps) {
        String rand = UUID.randomUUID().toString();
        Flow flow = new Flow();
        flow.setName("ROOT"+rand);
        flow.setCreatedAt(new Date());
        flow.setUpdatedAt(new Date());
        flow.setCondition("condition"+rand);
        flow.setEnabled(true);
        flow.setOrder(5);
        flow.setReferenceId("refId"+rand);
        flow.setReferenceType(ReferenceType.DOMAIN);
        flow.setType(Type.REGISTER);

        if (nbPreSteps > 0 ) {
            List<Step> preSteps = new ArrayList<>();
            for (int i = 0; i < nbPreSteps; ++i) {
                Step preStep = new Step();
                preStep.setName("Step" + i + " " + rand);
                preStep.setEnabled(true);
                preStep.setConfiguration("ConfigStep"+ i + " " + rand);
                preStep.setPolicy("policy step1");
                preStep.setDescription("description step"+ i + " " + rand);

                preSteps.add(preStep);
            }
            flow.setPre(preSteps);
        }

        if (nbPostSteps > 0 ) {
            List<Step> postSteps = new ArrayList<>();
            for (int i = 0; i < nbPostSteps; ++i) {
                Step postStep = new Step();
                postStep.setName("Step" + i + " " + rand);
                postStep.setEnabled(true);
                postStep.setConfiguration("ConfigStep"+ i + " " + rand);
                postStep.setPolicy("policy step1");
                postStep.setDescription("description step"+ i + " " + rand);

                postSteps.add(postStep);
            }
            flow.setPost(postSteps);
        }

        return flow;
    }
}
