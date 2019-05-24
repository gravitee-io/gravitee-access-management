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
package io.gravitee.am.gateway.policy.impl;

import io.gravitee.am.gateway.policy.Policy;
import io.gravitee.am.gateway.policy.PolicyException;
import io.gravitee.am.gateway.policy.PolicyMetadata;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.api.annotations.OnResponse;

import java.lang.reflect.Method;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyImpl implements Policy {

    private final Object policyInst;
    private PolicyMetadata policyMetadata;

    private PolicyImpl(Object policyInst) {
        this.policyInst = policyInst;
    }

    @Override
    public String id() {
        return policyMetadata.id();
    }

    @Override
    public void execute(Object... args) throws PolicyException {
        invoke(policyMetadata.method(OnRequest.class), args);
    }

    @Override
    public boolean isRunnable() {
        return (policyMetadata.method(OnRequest.class) != null ||
                policyMetadata.method(OnResponse.class) != null);
    }

    public static Builder target(Object policyInstance) {
        return new Builder(policyInstance);
    }

    private Object invoke(Method invokedMethod, Object ... args) throws PolicyException {
        if (invokedMethod != null) {
            Class<?>[] parametersType = invokedMethod.getParameterTypes();
            Object[] parameters = new Object[parametersType.length];

            int idx = 0;

            // Map parameters according to parameter's type
            for (Class<?> paramType : parametersType) {
                parameters[idx++] = getParameterAssignableTo(paramType, args);
            }

            try {
                return invokedMethod.invoke(policyInst, parameters);
            } catch (Exception ex) {
                throw new PolicyException(ex);
            }
        }

        return null;
    }

    private <T> T getParameterAssignableTo(Class<T> paramType, Object ... args) {
        for(Object arg: args) {
            if (paramType.isAssignableFrom(arg.getClass())) {
                return (T) arg;
            }
        }

        return null;
    }

    private PolicyImpl definition(PolicyMetadata policyMetadata) {
        this.policyMetadata = policyMetadata;
        return this;
    }

    public static class Builder {

        private final Object policyInstance;
        private PolicyMetadata policyMetadata;

        private Builder(Object policyInstance) {
            this.policyInstance = policyInstance;
        }

        public Builder definition(PolicyMetadata policyMetadata) {
            this.policyMetadata = policyMetadata;
            return this;
        }

        public PolicyImpl build() {
            return new PolicyImpl(policyInstance)
                    .definition(policyMetadata);
        }
    }
}
