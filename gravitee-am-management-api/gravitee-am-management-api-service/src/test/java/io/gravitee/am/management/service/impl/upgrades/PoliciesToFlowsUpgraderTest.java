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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.common.policy.ExtensionPoint;
import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.model.Policy;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.repository.management.api.PolicyRepository;
import io.gravitee.am.service.FlowService;
import io.gravitee.am.service.model.plugin.PolicyPlugin;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

import static io.reactivex.Single.just;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PoliciesToFlowsUpgraderTest {

    public static final String MY_DOMAIN_ID = "MY_DOMAIN_ID";
    public static final String MY_DOMAIN_ID2 = "MY_DOMAIN_ID2";
    public static final List<Type> FLOWS = asList(Type.ROOT, Type.CONSENT, Type.LOGIN, Type.REGISTER);

    @InjectMocks
    private PoliciesToFlowsUpgrader upgrader;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private FlowService flowService;

    @Mock
    private PolicyPluginService policyPluginService;

    @Test
    public void testMigration_NoPolicies() throws Exception {
        when(policyRepository.collectionExists()).thenReturn(just(true));
        when(policyRepository.deleteCollection()).thenReturn(Completable.complete());
        when(policyRepository.findAll()).thenReturn(Flowable.empty());

        assertTrue(upgrader.upgrade());

        verify(policyRepository).findAll();
        verify(flowService, never()).defaultFlows(ReferenceType.DOMAIN, MY_DOMAIN_ID);
        verify(flowService, never()).create(any(), anyString(), any());
        verify(policyRepository).deleteCollection();
    }

    @Test
    public void testMigration_SingleDomainWithPolicies() throws Exception {
        when(policyRepository.collectionExists()).thenReturn(just(true));
        when(policyRepository.deleteCollection()).thenReturn(Completable.complete());

        // return policies for root & consent
        Policy rootPolicy1 = new Policy();
        rootPolicy1.setEnabled(true);
        rootPolicy1.setDomain(MY_DOMAIN_ID);
        rootPolicy1.setExtensionPoint(ExtensionPoint.ROOT);
        rootPolicy1.setOrder(0);
        rootPolicy1.setConfiguration("ROOT CONFIG 1 ");

        Policy rootPolicy2 = new Policy();
        rootPolicy2.setEnabled(true);
        rootPolicy2.setDomain(MY_DOMAIN_ID);
        rootPolicy2.setExtensionPoint(ExtensionPoint.ROOT);
        rootPolicy2.setOrder(1);
        rootPolicy2.setConfiguration("ROOT CONFIG 2 ");

        Policy preConsent = new Policy();
        preConsent.setEnabled(true);
        preConsent.setDomain(MY_DOMAIN_ID);
        preConsent.setExtensionPoint(ExtensionPoint.PRE_CONSENT);
        preConsent.setOrder(0);
        preConsent.setConfiguration("PRE CONSENT CONFIG");

        Policy postConsent = new Policy();
        postConsent.setEnabled(false);
        postConsent.setDomain(MY_DOMAIN_ID);
        postConsent.setExtensionPoint(ExtensionPoint.POST_CONSENT);
        postConsent.setOrder(0);
        postConsent.setConfiguration("POST CONSENT CONFIG");

        Policy postConsent2 = new Policy();
        postConsent2.setEnabled(true);
        postConsent2.setDomain(MY_DOMAIN_ID);
        postConsent2.setExtensionPoint(ExtensionPoint.POST_CONSENT);
        postConsent2.setOrder(1);
        postConsent2.setConfiguration("POST CONSENT CONFIG");

        when(policyRepository.findAll()).thenReturn(Flowable.just(rootPolicy2, rootPolicy1, preConsent, postConsent2, postConsent)); // rootPolicy2 first to test ordering
        when(flowService.defaultFlows(ReferenceType.DOMAIN, MY_DOMAIN_ID)).thenReturn(FLOWS.stream().map(type -> buildFlow(type, ReferenceType.DOMAIN, MY_DOMAIN_ID)).collect(Collectors.toList()));
        when(flowService.create(any(), anyString(), any())).thenReturn(Single.just(new Flow()));
        when(policyPluginService.findById(null)).thenReturn(Maybe.just(new PolicyPlugin()));

        assertTrue(upgrader.upgrade());

        verify(flowService).defaultFlows(ReferenceType.DOMAIN, MY_DOMAIN_ID);
        verify(policyRepository).deleteCollection();
        verify(policyRepository).findAll();
        verify(flowService, times(4)).create(any(), anyString(), argThat(flow -> {
            boolean result = false;
            switch (flow.getType()) {
                case ROOT:
                    result = CollectionUtils.isEmpty(flow.getPost()) && flow.getPre().size() == 2;
                    result = result && flow.getPre().get(0).getConfiguration().equals(rootPolicy1.getConfiguration());
                    result = result && flow.getPre().get(0).isEnabled() == rootPolicy1.isEnabled();
                    result = result && flow.getPre().get(1).getConfiguration().equals(rootPolicy2.getConfiguration());
                    result = result && flow.getPre().get(1).isEnabled() == rootPolicy2.isEnabled();
                    break;
                case CONSENT:
                    result = flow.getPost().size() == 2 && flow.getPre().size() == 1;
                    result = result && flow.getPre().get(0).getConfiguration().equals(preConsent.getConfiguration());
                    result = result && flow.getPre().get(0).isEnabled() == preConsent.isEnabled();

                    result = result && flow.getPost().get(0).getConfiguration().equals(postConsent.getConfiguration());
                    result = result && flow.getPost().get(0).isEnabled() == postConsent.isEnabled();
                    result = result && flow.getPost().get(1).getConfiguration().equals(postConsent2.getConfiguration());
                    result = result && flow.getPost().get(1).isEnabled() == postConsent2.isEnabled();
                    break;
                default:
                    result = true;
            }
            return result;
        }));
    }

    @Test
    public void testMigration_MultipleDomains_withPolicies() {
        when(policyRepository.collectionExists()).thenReturn(just(true));
        when(policyRepository.deleteCollection()).thenReturn(Completable.complete());

        // return policies for root & consent
        Policy rootPolicy1 = new Policy();
        rootPolicy1.setEnabled(true);
        rootPolicy1.setDomain(MY_DOMAIN_ID);
        rootPolicy1.setExtensionPoint(ExtensionPoint.ROOT);
        rootPolicy1.setOrder(0);
        rootPolicy1.setConfiguration("ROOT CONFIG 1 ");

        Policy rootPolicy2 = new Policy();
        rootPolicy2.setEnabled(true);
        rootPolicy2.setDomain(MY_DOMAIN_ID);
        rootPolicy2.setExtensionPoint(ExtensionPoint.ROOT);
        rootPolicy2.setOrder(1);
        rootPolicy2.setConfiguration("ROOT CONFIG 2 ");

        Policy preConsent = new Policy();
        preConsent.setEnabled(true);
        preConsent.setDomain(MY_DOMAIN_ID2);
        preConsent.setExtensionPoint(ExtensionPoint.PRE_CONSENT);
        preConsent.setOrder(0);
        preConsent.setConfiguration("PRE CONSENT CONFIG");

        Policy postConsent = new Policy();
        postConsent.setEnabled(false);
        postConsent.setDomain(MY_DOMAIN_ID2);
        postConsent.setExtensionPoint(ExtensionPoint.POST_CONSENT);
        postConsent.setOrder(0);
        postConsent.setConfiguration("POST CONSENT CONFIG");

        Policy postConsent2 = new Policy();
        postConsent2.setEnabled(true);
        postConsent2.setDomain(MY_DOMAIN_ID2);
        postConsent2.setExtensionPoint(ExtensionPoint.POST_CONSENT);
        postConsent2.setOrder(1);
        postConsent2.setConfiguration("POST CONSENT CONFIG");

        when(policyRepository.findAll()).thenReturn(Flowable.just(rootPolicy2, rootPolicy1, preConsent, postConsent2, postConsent)); // rootPolicy2 first to test ordering
        when(flowService.defaultFlows(ReferenceType.DOMAIN, MY_DOMAIN_ID)).thenReturn(FLOWS.stream().map(type -> buildFlow(type, ReferenceType.DOMAIN, MY_DOMAIN_ID)).collect(Collectors.toList()));
        when(flowService.defaultFlows(ReferenceType.DOMAIN, MY_DOMAIN_ID2)).thenReturn(FLOWS.stream().map(type -> buildFlow(type, ReferenceType.DOMAIN, MY_DOMAIN_ID2)).collect(Collectors.toList()));
        when(flowService.create(any(), anyString(), any())).thenReturn(Single.just(new Flow()));
        when(policyPluginService.findById(null)).thenReturn(Maybe.just(new PolicyPlugin()));

        assertTrue(upgrader.upgrade());

        verify(flowService).defaultFlows(ReferenceType.DOMAIN, MY_DOMAIN_ID);
        verify(flowService).defaultFlows(ReferenceType.DOMAIN, MY_DOMAIN_ID2);
        verify(policyRepository).deleteCollection();
        verify(policyRepository).findAll();
        verify(flowService, times(8)).create(any(), anyString(), argThat(flow -> {
            boolean result = false;
            switch (flow.getType()) {
                case ROOT:
                    if (flow.getReferenceId().equals(MY_DOMAIN_ID)) {
                        result = CollectionUtils.isEmpty(flow.getPost()) && flow.getPre().size() == 2;
                        result = result && flow.getPre().get(0).getConfiguration().equals(rootPolicy1.getConfiguration());
                        result = result && flow.getPre().get(0).isEnabled() == rootPolicy1.isEnabled();
                        result = result && flow.getPre().get(1).getConfiguration().equals(rootPolicy2.getConfiguration());
                        result = result && flow.getPre().get(1).isEnabled() == rootPolicy2.isEnabled();
                    } else {
                        result = true;
                    }
                    break;
                case CONSENT:
                    if (flow.getReferenceId().equals(MY_DOMAIN_ID2)) {
                        result = flow.getPost().size() == 2 && flow.getPre().size() == 1;
                        result = result && flow.getPre().get(0).getConfiguration().equals(preConsent.getConfiguration());
                        result = result && flow.getPre().get(0).isEnabled() == preConsent.isEnabled();

                        result = result && flow.getPost().get(0).getConfiguration().equals(postConsent.getConfiguration());
                        result = result && flow.getPost().get(0).isEnabled() == postConsent.isEnabled();
                        result = result && flow.getPost().get(1).getConfiguration().equals(postConsent2.getConfiguration());
                        result = result && flow.getPost().get(1).isEnabled() == postConsent2.isEnabled();
                    } else {
                        result = true;
                    }
                    break;
                default:
                    result = true;
            }
            return result;
        }));
    }

    private Flow buildFlow(Type type, ReferenceType referenceType, String referenceId) {
        Flow flow = new Flow();
        if (Type.ROOT.equals(type)) {
            flow.setName("ALL");
        } else {
            flow.setName(type.name());
        }
        flow.setType(type);
        flow.setReferenceType(referenceType);
        flow.setReferenceId(referenceId);
        flow.setEnabled(true);
        return flow;
    }
}
