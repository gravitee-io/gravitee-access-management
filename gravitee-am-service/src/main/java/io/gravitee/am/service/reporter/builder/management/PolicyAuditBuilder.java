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
package io.gravitee.am.service.reporter.builder.management;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.model.Policy;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyAuditBuilder extends ManagementAuditBuilder<PolicyAuditBuilder> {

    public PolicyAuditBuilder() {
        super();
    }

    public PolicyAuditBuilder policy(Policy policy) {
        if (EventType.POLICY_CREATED.equals(getType()) || EventType.POLICY_UPDATED.equals(getType())) {
            setNewValue(policy);
        }
        domain(policy.getDomain());
        setTarget(policy.getId(), EntityType.POLICY, policy.getType(), policy.getName(), policy.getDomain());
        return this;
    }
}
