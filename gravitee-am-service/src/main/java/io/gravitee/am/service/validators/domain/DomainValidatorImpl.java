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

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.service.exception.InvalidDomainException;
import io.gravitee.am.service.validators.dynamicparams.ClientRegistrationSettingsValidator;
import io.gravitee.am.service.validators.path.PathValidator;
import io.gravitee.am.service.validators.virtualhost.VirtualHostValidator;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URISyntaxException;
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
    private final ClientRegistrationSettingsValidator clientRegistrationSettingsValidator;

    @Autowired
    public DomainValidatorImpl(PathValidator pathValidator,
                               VirtualHostValidator virtualHostValidator,
                               ClientRegistrationSettingsValidator clientRegistrationSettingsValidator ){
        this.pathValidator = pathValidator;
        this.virtualHostValidator = virtualHostValidator;
        this.clientRegistrationSettingsValidator = clientRegistrationSettingsValidator;
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

        chain.add(clientRegistrationSettingsValidator.validate(domain)
                .concatMapCompletable(result -> {
                    List<String> invalidClients = result.clientsWithInvalidRedirectUris();
                    if (!invalidClients.isEmpty()) {
                        return Completable.error(new InvalidDomainException("Redirect URIs must be changed, apps: %s".formatted(invalidClients)));
                    } else {
                        return Completable.complete();
                    }
                })
        );

        if (domain.getWebAuthnSettings() != null && domain.getWebAuthnSettings().getCertificates() != null) {
            boolean containsInvalidCertFormat = domain.getWebAuthnSettings()
                    .getCertificates()
                    .values()
                    .stream()
                    .anyMatch(cert -> !(cert instanceof String certificate) || (certificate.startsWith("-----") || certificate.endsWith("-----")));
            if (containsInvalidCertFormat) {
                return Completable.error(new InvalidDomainException("WebAuthnSettings contains certificates with boundaries"));
            }
        }

        chain.add(validateCertificateBasedAuthSettings(domain));

        return Completable.merge(chain);
    }

    private Completable validateCertificateBasedAuthSettings(Domain domain) {
        LoginSettings loginSettings = domain.getLoginSettings();
        if (loginSettings == null || !loginSettings.isCertificateBasedAuthEnabled()) {
            return Completable.complete();
        }

        String cbaUrl = loginSettings.getCertificateBasedAuthUrl();
        if (!StringUtils.hasText(cbaUrl)) {
            return Completable.error(new InvalidDomainException("certificateBasedAuthUrl must be provided when certificate-based authentication is enabled"));
        }

        try {
            var uri = UriBuilder.fromURIString(cbaUrl).build();
            if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                return Completable.error(new InvalidDomainException("certificateBasedAuthUrl must be a valid HTTPS URL"));
            }
        } catch (IllegalArgumentException | URISyntaxException e) {
            return Completable.error(new InvalidDomainException("certificateBasedAuthUrl must be a valid HTTPS URL"));
        }

        return Completable.complete();
    }

}
