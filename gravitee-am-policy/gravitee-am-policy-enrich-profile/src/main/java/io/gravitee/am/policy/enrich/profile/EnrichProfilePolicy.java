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
package io.gravitee.am.policy.enrich.profile;

import io.gravitee.am.model.User;
import io.gravitee.am.policy.enrich.profile.configuration.EnrichProfilePolicyConfiguration;
import io.gravitee.am.policy.enrich.profile.configuration.Property;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnrichProfilePolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnrichProfilePolicy.class);
    private static final String GATEWAY_POLICY_ENRICH_PROFILE_ERROR_KEY = "GATEWAY_POLICY_ENRICH_PROFILE_ERROR";

    public static final String errorMessage = "Unable to update user profile with context information";

    private EnrichProfilePolicyConfiguration configuration;

    public EnrichProfilePolicy(EnrichProfilePolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext context, PolicyChain policyChain) {
        LOGGER.debug("Start EnrichProfilePolicy.onRequest");
        try {
            if (prepareUserProfile(context)) {
                enrichProfile(context)
                        .subscribe(
                                (user) -> {
                                    LOGGER.debug("User profile updated", user.getId());
                                    policyChain.doNext(request, response);
                                },
                                (error) -> {
                                    if (configuration.isExitOnError()) {
                                        LOGGER.warn("Update of user profile failed!", error.getMessage());
                                        policyChain.failWith(PolicyResult.failure(errorMessage));
                                    } else {
                                        LOGGER.info("Update of user profile failed!", error.getMessage());
                                        policyChain.doNext(request, response);
                                    }
                                });
            } else {
                policyChain.doNext(request, response);
            }
        } catch (Exception e) {
            if (configuration.isExitOnError()) {
                LOGGER.warn(errorMessage, e);
                policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENRICH_PROFILE_ERROR_KEY, errorMessage));
            } else {
                LOGGER.info(errorMessage, e);
                policyChain.doNext(request, response);
            }
        }
    }

    /**
     * Complete the user  profile based on the Policy configuration
     *
     * @param context
     * @return true if the profile must be updated, false otherwise
     */
    protected boolean prepareUserProfile(ExecutionContext context) {
        boolean needUpdate = false;
        if (!(configuration.getProperties() == null || configuration.getProperties().isEmpty())) {
            User user = (User)context.getAttribute("user");
            if (user != null) {
                LOGGER.debug("Enrich profile for user '{}'", user.getId());
                Map<String, Object> additionalInformation = user.getAdditionalInformation();
                if (additionalInformation == null) {
                    additionalInformation = new HashMap<>();
                    user.setAdditionalInformation(additionalInformation);
                }

                TemplateEngine tplEngine = context.getTemplateEngine();
                for (Property property : configuration.getProperties()) {
                    String additionalInfo = tplEngine.getValue(property.getClaimValue(), String.class);
                    additionalInformation.put(property.getClaim(), additionalInfo);
                }

                needUpdate = true;
            } else {
                LOGGER.debug("User is missing from the execution context, ignore this policy");
            }
        } else {
            LOGGER.debug("No properties found in policy configuration, ignore this policy");
        }
        return needUpdate;
    }

    protected Single<User> enrichProfile(ExecutionContext context) {
        UserRepository userRepository = context.getComponent(UserRepository.class);
        User user = (User)context.getAttribute("user");
        return userRepository.update(user);
    }

}
