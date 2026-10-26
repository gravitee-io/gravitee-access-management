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

import io.gravitee.am.common.event.Action;
import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.FlowEvent;
import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.gateway.handler.common.flow.impl.FlowManagerImpl;
import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.monitoring.DomainReadinessService;
import io.gravitee.am.plugins.policy.core.PolicyPluginManager;
import io.gravitee.am.service.FlowService;
import io.gravitee.common.event.Event;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks, for every flow type, that a flow which is disabled (at load time or after an update)
 * or undeployed is properly removed from the execution flows.
 *
 * @author GraviteeSource Team
 */
@RunWith(Parameterized.class)
public class FlowManagerDisabledFlowTest {

    private static final String DOMAIN_ID = "domain-id";
    private static final String FLOW_ID = "flow-id";

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Type> flowTypes() {
        return Arrays.asList(Type.values());
    }

    @Parameterized.Parameter
    public Type type;

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

    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(domain.getId()).thenReturn(DOMAIN_ID);
        when(policyPluginManager.create(anyString(), any(), any())).thenAnswer(i -> mock(Policy.class));
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void shouldNotDeploy_flowDisabledAtStartup() {
        Flow disabledFlow = flow(false);
        when(flowService.findAll(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Flowable.just(disabledFlow));
        flowManager.afterPropertiesSet();
        assertNoPolicy();
    }

    @Test
    public void shouldDeploy_flowEnabledAtStartup() {
        Flow enabledFlow = flow(true);
        when(flowService.findAll(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Flowable.just(enabledFlow));
        flowManager.afterPropertiesSet();
        // sanity check: every extension point of the type has its policy, so the removal tests are meaningful
        extensionPoints().forEach(ep -> assertEquals(ep.name(), 1, policies(ep).size()));
    }

    @Test
    public void shouldRemovePolicies_flowDisabledAfterUpdate() {
        Flow enabledFlow = flow(true);
        when(flowService.findAll(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Flowable.just(enabledFlow));
        flowManager.afterPropertiesSet();
        extensionPoints().forEach(ep -> assertEquals(ep.name(), 1, policies(ep).size()));

        Flow disabledFlow = flow(false);
        when(flowService.findById(FLOW_ID)).thenReturn(Maybe.just(disabledFlow));
        flowManager.onEvent(event(FlowEvent.UPDATE));

        assertNoPolicy();
    }

    @Test
    public void shouldRemovePolicies_flowUndeployed() {
        Flow enabledFlow = flow(true);
        when(flowService.findAll(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Flowable.just(enabledFlow));
        flowManager.afterPropertiesSet();
        extensionPoints().forEach(ep -> assertEquals(ep.name(), 1, policies(ep).size()));

        flowManager.onEvent(event(FlowEvent.UNDEPLOY));

        assertNoPolicy();
    }

    private Flow flow(boolean enabled) {
        Step step = mock(Step.class);
        when(step.isEnabled()).thenReturn(true);
        when(step.getPolicy()).thenReturn("step-policy");

        Flow flow = mock(Flow.class);
        when(flow.getId()).thenReturn(FLOW_ID);
        when(flow.getType()).thenReturn(type);
        when(flow.isEnabled()).thenReturn(enabled);
        when(flow.getPre()).thenReturn(List.of(step));
        when(flow.getPost()).thenReturn(List.of(step));
        return flow;
    }

    private Event<FlowEvent, Payload> event(FlowEvent flowEvent) {
        Payload payload = new Payload(FLOW_ID, ReferenceType.DOMAIN, DOMAIN_ID, Action.UPDATE);
        @SuppressWarnings("unchecked")
        Event<FlowEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(flowEvent);
        when(event.content()).thenReturn(payload);
        return event;
    }

    /**
     * Extension points expected for the flow type, derived from the type name
     * so the expectation does not depend on the mapping used by the manager.
     */
    private List<ExtensionPoint> extensionPoints() {
        return Stream.of(ExtensionPoint.values())
                .filter(ep -> ep.name().equals(type.name())
                        || ep.name().equals("PRE_" + type.name())
                        || ep.name().equals("POST_" + type.name()))
                .collect(toList());
    }

    private List<Policy> policies(ExtensionPoint extensionPoint) {
        return flowManager.findByExtensionPoint(extensionPoint, null, null).blockingGet();
    }

    private void assertNoPolicy() {
        assertTrue("no extension point found for type " + type, !extensionPoints().isEmpty());
        extensionPoints().forEach(ep -> assertTrue(ep.name(), policies(ep).isEmpty()));
    }
}
