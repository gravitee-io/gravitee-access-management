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
package io.gravitee.am.policy.enrich.auth;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.policy.enrich.auth.configuration.EnrichAuthFlowPolicyConfiguration;
import io.gravitee.am.policy.enrich.auth.configuration.Property;
import io.gravitee.am.repository.gateway.api.AuthenticationFlowContextRepository;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnrichAuthFlowPolicy {
    private static Logger LOGGER = LoggerFactory.getLogger(EnrichAuthFlowPolicy.class);
    private static final String GATEWAY_POLICY_ENRICH_AUTH_FLOW_ERROR_KEY = "GATEWAY_POLICY_ENRICH_AUTH_FLOW_ERROR";

    private final EnrichAuthFlowPolicyConfiguration configuration;

    public EnrichAuthFlowPolicy(EnrichAuthFlowPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext context, PolicyChain policyChain) {
        LOGGER.debug("Start enrich authentication flow policy");

        if (configuration.getProperties() != null && !configuration.getProperties().isEmpty()) {

            AuthenticationFlowContext authContext = (AuthenticationFlowContext)context.getAttribute(ConstantKeys.AUTH_FLOW_CONTEXT_KEY);
            if (authContext == null) {
                // this should never happen because a default GraviteeContext with AuthFlowContext is generated
                // when there are no data into the repository. This case may happen if the GraviteeContextHandler
                // is not register on the route calling this policy in this case log a WARN but continue to process the chain
                LOGGER.warn("Enrich Authentication Flow policy required a valid GraviteeContext");
                policyChain.doNext(request, response);

            } else {

                enrichAuthFlowContext(context)
                        .subscribe(
                                success -> policyChain.doNext(request, response),
                                error -> {
                                    LOGGER.warn("An error occurs while enriching authentication flow context", error);
                                    policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENRICH_AUTH_FLOW_ERROR_KEY, error.getMessage()));
                                }
                        );
            }
        } else {

            LOGGER.debug("No properties configured for the Enrich Authentication Flow policy");
            policyChain.doNext(request, response);
        }
    }

    private Single<AuthenticationFlowContext> enrichAuthFlowContext(ExecutionContext executionContext) {
        Map<String, Object> data = new HashMap<>();
        TemplateEngine tplEngine = executionContext.getTemplateEngine();
        for (Property property : configuration.getProperties()) {
            Object value = tplEngine.getValue(property.getValue(), String.class);
            data.put(property.getKey(), value);
        }

        final Instant now = Instant.now();
        final AuthenticationFlowContextRepository authContextRepository = executionContext.getComponent(AuthenticationFlowContextRepository.class);
        final Environment environment = executionContext.getComponent(Environment.class);
        final Integer expiration = environment.getProperty("authenticationFlow.expirationTimeOut", Integer.class, 300);

        AuthenticationFlowContext authContext = (AuthenticationFlowContext) executionContext.getAttribute(ConstantKeys.AUTH_FLOW_CONTEXT_KEY);
        authContext.setVersion(authContext.getVersion() + 1);
        if (authContext.getData() != null) {
            // data already present, do not remove them but override same entries
            authContext.getData().putAll(data);
        } else {
            authContext.setData(data);
        }
        authContext.setCreatedAt(new Date(now.toEpochMilli()));
        authContext.setExpireAt(new Date(now.plus(expiration, ChronoUnit.SECONDS).toEpochMilli()));

        return authContextRepository.create(authContext);
    }
}
