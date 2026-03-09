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
package io.gravitee.am.policy.user.action.example;

import io.gravitee.am.gateway.handler.common.policy.AbstractUserActionPolicy;
import io.gravitee.am.policy.user.action.example.configuration.ExampleUserActionPolicyConfiguration;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.reactivex.rxjava3.core.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example User Action Policy
 * 
 * This is an example implementation showing how to extend AbstractUserActionPolicy
 * to create a custom user action policy that can be loaded as a plugin.
 *
 * @author GraviteeSource Team
 */
public class ExampleUserActionPolicy extends AbstractUserActionPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExampleUserActionPolicy.class);

    private ExampleUserActionPolicyConfiguration configuration;
    /**
     * Constructor
     * @param configuration the policy configuration
     */
    public ExampleUserActionPolicy(ExampleUserActionPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Implement custom user action logic
     */
    @Override
    public Completable performUserAction(Request request, ExecutionContext context) throws Exception {
        LOGGER.debug("Executing ExampleUserActionPolicy");
        String requestParam = request.parameters().getFirst("test");
        // do some logic
        if (requestParam == null || requestParam.equals("error")) {
            return Completable.error(new IllegalArgumentException("error bad parameter from custom user action policy"));
        }

        return Completable.complete();
    }

    @Override
    public String getAction() {
        return configuration.getAction();
    }

    @Override
    public String getTemplate() {
        return configuration.getTemplate();
    }

}
