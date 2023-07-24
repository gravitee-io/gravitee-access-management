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
package io.gravitee.am.management.handlers.management.api.resources.utils;

import io.gravitee.am.management.service.PolicyPluginService;
import io.gravitee.am.service.model.Flow;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowUtils {
    public static Completable checkPoliciesDeployed(PolicyPluginService policyPluginService, List<Flow> flows) {
        return Flowable.fromIterable(flows).concatMapCompletable(flow -> checkPoliciesDeployed(policyPluginService, flow));
    }

    public static Completable checkPoliciesDeployed(PolicyPluginService policyPluginService, Flow flow) {
        if (flow != null) {
            var pre = Optional.ofNullable(flow.getPre()).orElseGet(() -> Collections.emptyList());
            var post = Optional.ofNullable(flow.getPost()).orElseGet(() -> Collections.emptyList());
            return Flowable.fromStream(Stream.concat(pre.stream(), post.stream())).concatMapCompletable(step -> policyPluginService.checkPluginDeployment(step.getPolicy()));
        }
        return Completable.complete();
    }
}