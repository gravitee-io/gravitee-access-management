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
package io.gravitee.am.gateway.handler.common.flow;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.flow.impl.FlowManagerImpl;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.model.Domain;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.am.service.FlowService;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class FlowManagerTest {

    @Mock
    private Domain domain;

    @Mock
    private FlowService flowService;

    @Mock
    private PolicyPluginManager policyPluginManager;

    @Mock
    private EventManager eventManager;

    @Mock
    private DomainReadinessService domainReadinessService;

    @InjectMocks
    private FlowManagerImpl flowManager = new FlowManagerImpl();

    @Test
    public void shouldNoFindByExtensionPoint_noFlows() {
        when(domain.getId()).thenReturn("domain-id");
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.empty());
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, null, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.isEmpty());
            return true;
        });
        verify(policyPluginManager, never()).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldNotFindByExtensionPoint_flowDisabled() {
        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("flow-id");
        when(flow.getType()).thenReturn(Type.CONSENT);
        when(flow.isEnabled()).thenReturn(false);
        when(domain.getId()).thenReturn("domain-id");
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(flow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, null, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.isEmpty());
            return true;
        });
        verify(policyPluginManager, never()).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldNotFindByExtensionPoint_noPolicy() {
        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("flow-id");
        when(flow.getType()).thenReturn(Type.CONSENT);
        when(flow.isEnabled()).thenReturn(true);
        when(domain.getId()).thenReturn("domain-id");
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(flow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, null, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.isEmpty());
            return true;
        });
        verify(policyPluginManager, never()).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldNotFindByExtensionPoint_disabledPolicy() {
        Step step = mock(Step.class);
        when(step.isEnabled()).thenReturn(false);

        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("flow-id");
        when(flow.getType()).thenReturn(Type.CONSENT);
        when(flow.isEnabled()).thenReturn(true);
        when(flow.getPre()).thenReturn(Collections.singletonList(step));
        when(domain.getId()).thenReturn("domain-id");
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(flow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, null, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.isEmpty());
            return true;
        });
        verify(policyPluginManager, never()).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldNotFindByExtensionPoint_onePolicy_differentStep() {
        Step step = mock(Step.class);
        when(step.isEnabled()).thenReturn(true);
        when(step.getPolicy()).thenReturn("step-policy");
        when(step.getConfiguration()).thenReturn("step-configuration");

        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("flow-id");
        when(flow.getType()).thenReturn(Type.CONSENT);
        when(flow.isEnabled()).thenReturn(true);
        when(flow.getPost()).thenReturn(Collections.singletonList(step));

        Policy policy = mock(Policy.class);

        when(domain.getId()).thenReturn("domain-id");
        when(policyPluginManager.create(step.getPolicy(), step.getCondition(), step.getConfiguration())).thenReturn(policy);
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(flow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, null, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.isEmpty());
            return true;
        });
        verify(policyPluginManager, times(1)).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldFindByExtensionPoint_domainPolicy() {
        Step step = mock(Step.class);
        when(step.isEnabled()).thenReturn(true);
        when(step.getPolicy()).thenReturn("step-policy");
        when(step.getConfiguration()).thenReturn("step-configuration");
        when(step.getCondition()).thenReturn("step-condition");

        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("flow-id");
        when(flow.getType()).thenReturn(Type.CONSENT);
        when(flow.isEnabled()).thenReturn(true);
        when(flow.getPre()).thenReturn(Collections.singletonList(step));

        Policy policy = mock(Policy.class);

        when(domain.getId()).thenReturn("domain-id");
        when(policyPluginManager.create(step.getPolicy(), step.getCondition(), step.getConfiguration())).thenReturn(policy);
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(flow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, null, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.size() == 1);
            return true;
        });
        verify(policyPluginManager, times(1)).create(anyString(), anyString(), anyString());
    }

    @Test
    public void shouldNotFindByExtensionPoint_applicationPolicy_clientNull() {
        Step step = mock(Step.class);
        when(step.isEnabled()).thenReturn(true);
        when(step.getPolicy()).thenReturn("step-policy");
        when(step.getConfiguration()).thenReturn("step-configuration");

        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("flow-id");
        when(flow.getType()).thenReturn(Type.CONSENT);
        when(flow.isEnabled()).thenReturn(true);
        when(flow.getPre()).thenReturn(Collections.singletonList(step));
        when(flow.getApplication()).thenReturn("app-id");

        Policy policy = mock(Policy.class);

        when(domain.getId()).thenReturn("domain-id");
        when(policyPluginManager.create(step.getPolicy(), step.getCondition(), step.getConfiguration())).thenReturn(policy);
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(flow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, null, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.isEmpty());
            return true;
        });
        verify(policyPluginManager, times(1)).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldNotFindByExtensionPoint_applicationPolicy_wrongClient() {
        Step step = mock(Step.class);
        when(step.isEnabled()).thenReturn(true);
        when(step.getPolicy()).thenReturn("step-policy");
        when(step.getConfiguration()).thenReturn("step-configuration");

        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("flow-id");
        when(flow.getType()).thenReturn(Type.CONSENT);
        when(flow.isEnabled()).thenReturn(true);
        when(flow.getPre()).thenReturn(Collections.singletonList(step));
        when(flow.getApplication()).thenReturn("app-id");

        Policy policy = mock(Policy.class);
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("other-app-id");

        when(domain.getId()).thenReturn("domain-id");
        when(policyPluginManager.create(step.getPolicy(), step.getCondition(), step.getConfiguration())).thenReturn(policy);
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(flow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, client, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.isEmpty());
            return true;
        });
        verify(policyPluginManager, times(1)).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldFindByExtensionPoint_applicationPolicy() {
        Step step = mock(Step.class);
        when(step.isEnabled()).thenReturn(true);
        when(step.getPolicy()).thenReturn("step-policy");
        when(step.getConfiguration()).thenReturn("step-configuration");

        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn("flow-id");
        when(flow.getType()).thenReturn(Type.CONSENT);
        when(flow.isEnabled()).thenReturn(true);
        when(flow.getPre()).thenReturn(Collections.singletonList(step));
        when(flow.getApplication()).thenReturn("app-id");

        Policy policy = mock(Policy.class);
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("app-id");

        when(domain.getId()).thenReturn("domain-id");
        when(policyPluginManager.create(step.getPolicy(), step.getCondition(), step.getConfiguration())).thenReturn(policy);
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(flow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, client, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.size() == 1);
            return true;
        });
        verify(policyPluginManager, times(1)).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldFindByExtensionPoint_twoFlows_inherit_false() {
        Step domainStep = mock(Step.class);
        when(domainStep.isEnabled()).thenReturn(true);
        when(domainStep.getPolicy()).thenReturn("step-policy");
        when(domainStep.getConfiguration()).thenReturn("domain-step-configuration");

        Step appStep = mock(Step.class);
        when(appStep.isEnabled()).thenReturn(true);
        when(appStep.getPolicy()).thenReturn("step-policy");
        when(appStep.getConfiguration()).thenReturn("app-step-configuration");

        Flow domainFlow = mock(Flow.class);
        when(domainFlow.getId()).thenReturn("domain-flow-id");
        when(domainFlow.getType()).thenReturn(Type.CONSENT);
        when(domainFlow.isEnabled()).thenReturn(true);
        when(domainFlow.getPre()).thenReturn(Collections.singletonList(domainStep));

        Flow appFlow = mock(Flow.class);
        when(appFlow.getId()).thenReturn("app-flow-id");
        when(appFlow.getType()).thenReturn(Type.CONSENT);
        when(appFlow.isEnabled()).thenReturn(true);
        when(appFlow.getPre()).thenReturn(Collections.singletonList(appStep));
        when(appFlow.getApplication()).thenReturn("app-id");

        Policy domainPolicy = mock(Policy.class);
        Policy appPolicy = mock(Policy.class);
        when(appPolicy.id()).thenReturn("app-policy");
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("app-id");

        when(domain.getId()).thenReturn("domain-id");
        when(policyPluginManager.create(domainStep.getPolicy(), domainStep.getCondition(), domainStep.getConfiguration())).thenReturn(domainPolicy);
        when(policyPluginManager.create(appStep.getPolicy(), appStep.getCondition(), appStep.getConfiguration())).thenReturn(appPolicy);
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(domainFlow, appFlow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, client, null).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.size() == 1);
            Assert.assertTrue(policies.get(0).id().equals(appPolicy.id()));
            return true;
        });
        verify(policyPluginManager, times(2)).create(anyString(), eq(null), anyString());
    }

    @Test
    public void shouldFindByExtensionPoint_twoFlows_inherit_true() {
        Step domainStep = mock(Step.class);
        when(domainStep.isEnabled()).thenReturn(true);
        when(domainStep.getPolicy()).thenReturn("step-policy");
        when(domainStep.getConfiguration()).thenReturn("domain-step-configuration");

        Step appStep = mock(Step.class);
        when(appStep.isEnabled()).thenReturn(true);
        when(appStep.getPolicy()).thenReturn("step-policy");
        when(appStep.getConfiguration()).thenReturn("app-step-configuration");

        Flow domainFlow = mock(Flow.class);
        when(domainFlow.getId()).thenReturn("domain-flow-id");
        when(domainFlow.getType()).thenReturn(Type.CONSENT);
        when(domainFlow.isEnabled()).thenReturn(true);
        when(domainFlow.getPre()).thenReturn(Collections.singletonList(domainStep));

        Flow appFlow = mock(Flow.class);
        when(appFlow.getId()).thenReturn("app-flow-id");
        when(appFlow.getType()).thenReturn(Type.CONSENT);
        when(appFlow.isEnabled()).thenReturn(true);
        when(appFlow.getPre()).thenReturn(Collections.singletonList(appStep));
        when(appFlow.getApplication()).thenReturn("app-id");

        Policy domainPolicy = mock(Policy.class);
        when(domainPolicy.id()).thenReturn("domain-policy");
        Policy appPolicy = mock(Policy.class);
        when(appPolicy.id()).thenReturn("app-policy");
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("app-id");
        when(client.isFlowsInherited()).thenReturn(true);

        when(domain.getId()).thenReturn("domain-id");
        when(policyPluginManager.create(domainStep.getPolicy(), domainStep.getCondition(), domainStep.getConfiguration())).thenReturn(domainPolicy);
        when(policyPluginManager.create(appStep.getPolicy(), appStep.getCondition(), appStep.getConfiguration())).thenReturn(appPolicy);
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(domainFlow, appFlow));
        flowManager.afterPropertiesSet();
        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, client, ExecutionPredicate.alwaysTrue()).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.size() == 2);
            Assert.assertTrue(policies.get(0).id().equals(domainPolicy.id()));
            Assert.assertTrue(policies.get(1).id().equals(appPolicy.id()));
            return true;
        });
        verify(policyPluginManager, times(2)).create(anyString(), eq(null), anyString());
    }


    @Test
    public void shouldFindByExtensionPoint_twoFlows_inherit_true_ConditionFiltering() {
        Step domainStep = mock(Step.class);
        when(domainStep.isEnabled()).thenReturn(true);
        when(domainStep.getPolicy()).thenReturn("step-policy");
        when(domainStep.getConfiguration()).thenReturn("domain-step-configuration");

        Step appStep = mock(Step.class);
        when(appStep.isEnabled()).thenReturn(true);
        when(appStep.getPolicy()).thenReturn("step-policy");
        when(appStep.getConfiguration()).thenReturn("app-step-configuration");

        Flow domainFlow = mock(Flow.class);
        when(domainFlow.getId()).thenReturn("domain-flow-id");
        when(domainFlow.getType()).thenReturn(Type.CONSENT);
        when(domainFlow.isEnabled()).thenReturn(true);
        when(domainFlow.getPre()).thenReturn(Collections.singletonList(domainStep));
        when(domainFlow.getCondition()).thenReturn("false");

        Flow appFlow = mock(Flow.class);
        when(appFlow.getId()).thenReturn("app-flow-id");
        when(appFlow.getType()).thenReturn(Type.CONSENT);
        when(appFlow.isEnabled()).thenReturn(true);
        when(appFlow.getPre()).thenReturn(Collections.singletonList(appStep));
        when(appFlow.getApplication()).thenReturn("app-id");
        when(appFlow.getCondition()).thenReturn("true");

        Policy appPolicy = mock(Policy.class);
        when(appPolicy.id()).thenReturn("app-policy");
        Client client = mock(Client.class);
        when(client.getId()).thenReturn("app-id");
        when(client.isFlowsInherited()).thenReturn(true);

        when(domain.getId()).thenReturn("domain-id");
        when(policyPluginManager.create(appStep.getPolicy(), appStep.getCondition(), appStep.getConfiguration())).thenReturn(appPolicy);
        when(flowService.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(domainFlow, appFlow));
        flowManager.afterPropertiesSet();

        ExecutionContext executionContext = mock(ExecutionContext.class);
        when(executionContext.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());

        TestObserver<List<Policy>> obs = flowManager.findByExtensionPoint(ExtensionPoint.PRE_CONSENT, client, ExecutionPredicate.from(executionContext)).test();
        obs.awaitDone(10, TimeUnit.SECONDS);
        obs.assertValue(policies -> {
            Assert.assertTrue(policies.size() == 1);
            Assert.assertTrue(policies.get(0).id().equals(appPolicy.id()));
            return true;
        });
        verify(policyPluginManager, times(2)).create(anyString(), eq(null), anyString());
    }
}
