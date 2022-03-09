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

package io.gravitee.am.gateway.handler.common.flow.execution;

import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.model.flow.Flow;
import java.util.List;
import java.util.Objects;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExecutionFlow {
    private final String flowId;
    private final List<Policy> policies;
    private final String application;
    private final String condition;

    public ExecutionFlow(Flow flow, List<Policy> executionPolicies) {
        this.flowId = flow.getId();
        this.policies = executionPolicies;
        this.application = flow.getApplication();
        this.condition = flow.getCondition();
    }

    public String getFlowId() {
        return flowId;
    }

    public List<Policy> getPolicies() {
        return policies;
    }

    public String getApplication() {
        return application;
    }

    public String getCondition() {
        return condition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionFlow that = (ExecutionFlow) o;
        return Objects.equals(flowId, that.flowId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowId);
    }
}
