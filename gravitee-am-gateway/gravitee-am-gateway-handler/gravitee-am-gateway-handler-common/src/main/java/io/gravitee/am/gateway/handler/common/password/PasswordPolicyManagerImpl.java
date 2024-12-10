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

package io.gravitee.am.gateway.handler.common.password;


import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.PasswordPolicyEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.PasswordPolicyRepository;
import io.gravitee.common.event.Event;
import io.gravitee.common.event.EventListener;
import io.gravitee.common.service.AbstractService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class PasswordPolicyManagerImpl extends AbstractService implements PasswordPolicyManager, EventListener<PasswordPolicyEvent, Payload>, InitializingBean {

    @Autowired
    private Domain domain;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private PasswordPolicyRepository passwordPolicyRepository;

    private final ConcurrentMap<String, PasswordPolicy> policies = new ConcurrentHashMap<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("Start loading password policies for domain '{}'", domain.getId());
        passwordPolicyRepository.findByReference(ReferenceType.DOMAIN, domain.getId())
                .subscribe(this::registerPolicy, this::logRegistrationError);
    }

    private void registerPolicy(PasswordPolicy policy) {
        policies.put(policy.getId(), policy);
        log.debug("Password Policy '{}' loaded", policy.getId());
    }

    private void logRegistrationError(Throwable error) {
        log.warn("Password policies can't be loaded for domain '{}' : '{}'", domain.getId(), error.getMessage());
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Register event listener for password policy events");
        eventManager.subscribeForEvents(this, PasswordPolicyEvent.class, domain.getId());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Dispose event listener for password policy events for domain {}", domain.getName());
        eventManager.unsubscribeForEvents(this, PasswordPolicyEvent.class, domain.getId());
    }

    @Override
    public void onEvent(Event<PasswordPolicyEvent, Payload> event) {
        if (event.content().getReferenceType() == ReferenceType.DOMAIN
                && domain.getId().equals(event.content().getReferenceId())) {
            switch (event.type()) {
                case DEPLOY:
                case UPDATE:
                    deployPolicy(event.content().getId());
                    break;
                case UNDEPLOY:
                    undeployPolicy(event.content().getId());
                    break;
            }
        }
    }

    private void deployPolicy(String policyId) {
        log.debug("Update event received for password policy '{}'", policyId);
        passwordPolicyRepository.findById(policyId)
                .subscribe(this::registerPolicy, this::logRegistrationError);
    }

    private void undeployPolicy(String policyId) {
        log.debug("Undeploy event received for password policy '{}'", policyId);
        policies.remove(policyId);
    }

    @Override
    public Optional<PasswordPolicy> getPolicy(String policyId) {
        return Optional.ofNullable(policies.get(policyId));
    }

    @Override
    public Optional<PasswordPolicy> getDefaultPolicy() {
        return policies.values()
                .stream()
                .filter(PasswordPolicy::getDefaultPolicy)
                .findFirst();
    }

    @Override
    public Optional<PasswordPolicy> getPolicy(Client client, IdentityProvider identityProvider) {
        Optional<PasswordPolicy> clientPasswordPolicy = ofNullable(client)
                .map(Client::getPasswordSettings)
                .filter(not(PasswordSettings::isInherited))
                .map(PasswordSettings::toPasswordPolicy);
        Optional<PasswordPolicy> idpPasswordPolicy = ofNullable(identityProvider)
                .map(IdentityProvider::getPasswordPolicy)
                .map(this.policies::get);
            return clientPasswordPolicy
                .or(() -> idpPasswordPolicy)
                .or(this::getDefaultPolicy);
    }
}
