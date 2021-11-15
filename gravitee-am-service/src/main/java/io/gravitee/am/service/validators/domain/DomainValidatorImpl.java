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
package io.gravitee.am.service.validators.domain;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidator;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidatorImpl;
import io.gravitee.am.service.validators.path.PathValidator;
import io.reactivex.Completable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class DomainValidatorImpl implements DomainValidator {

    private final PathValidator pathValidator;
    private final VirtualHostValidator virtualHostValidator;

    @Autowired
    public DomainValidatorImpl(PathValidator pathValidator, VirtualHostValidator virtualHostValidator){
        this.pathValidator = pathValidator;
        this.virtualHostValidator = virtualHostValidator;
    }

    @Override
    public Completable validate(Domain domain, List<String> domainRestrictions) {

        List<Completable> chain = new ArrayList<>();

        if (domain.getName().contains("/")) {
            return Completable.error(new InvalidDomainException("Domain name cannot contain '/' character"));
        }

        if (!CollectionUtils.isEmpty(domainRestrictions) && !domain.isVhostMode()) {
            return Completable.error(new InvalidDomainException("Domain can only work in vhost mode"));
        }

        if (domain.isVhostMode()) {
            if (domain.getVhosts() == null || domain.getVhosts().isEmpty()) {
                return Completable.error(new InvalidDomainException("VHost mode requires at least one VHost"));
            }

            // Check at there is only one vhost flagged with override entrypoint.
            long count = domain.getVhosts().stream().filter(VirtualHost::isOverrideEntrypoint).count();
            if (count > 1) {
                return Completable.error(new InvalidDomainException("Only one vhost can be used to override entrypoint"));
            } else if (count == 0) {
                return Completable.error(new InvalidDomainException("You must select one vhost to override entrypoint"));
            }

            chain.addAll(domain.getVhosts().stream()
                    .map(vhost -> virtualHostValidator.validate(vhost, domainRestrictions))
                    .collect(Collectors.toList()));
        } else {
            if ("/".equals(domain.getPath())) {
                return Completable.error(new InvalidDomainException("'/' path is not allowed in context-path mode"));
            }

            chain.add(pathValidator.validate(domain.getPath()));
        }

        if (domain.getWebAuthnSettings() != null && domain.getWebAuthnSettings().getCertificates() != null) {
            boolean containsInvalidCertFormat = domain.getWebAuthnSettings()
                    .getCertificates()
                    .values()
                    .stream()
                    .anyMatch(cert -> !(cert instanceof String) || (((String) cert).startsWith("-----") || ((String) cert).endsWith("-----")));
            if (containsInvalidCertFormat) {
                return Completable.error(new InvalidDomainException("WebAuthnSettings contains certificates with boundaries"));
            }
        }

        return Completable.merge(chain);
    }

}
