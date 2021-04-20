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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.model.Domain;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.model.PatchDomain;
import io.gravitee.am.service.model.openid.PatchClientRegistrationSettings;
import io.gravitee.am.service.model.openid.PatchOIDCSettings;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@Component
public class DomainUpgrader implements Upgrader, Ordered {

    /**
     * Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainUpgrader.class);

    @Autowired
    private DomainService domainService;

    @Override
    public boolean upgrade() {
        LOGGER.info("Applying domain upgrade");
        domainService
            .findAll()
            .flatMapObservable(domains -> Observable.fromIterable(domains))
            .flatMapSingle(this::upgradeDomain)
            .toList()
            .subscribe();
        return true;
    }

    private Single<Domain> upgradeDomain(Domain domain) {
        if (domain.getOidc() != null) {
            return Single.just(domain);
        }

        PatchClientRegistrationSettings clientRegistrationPatch = new PatchClientRegistrationSettings();
        clientRegistrationPatch.setDynamicClientRegistrationEnabled(Optional.of(false));
        clientRegistrationPatch.setOpenDynamicClientRegistrationEnabled(Optional.of(false));
        clientRegistrationPatch.setAllowHttpSchemeRedirectUri(Optional.of(true));
        clientRegistrationPatch.setAllowLocalhostRedirectUri(Optional.of(true));
        clientRegistrationPatch.setAllowWildCardRedirectUri(Optional.of(true));

        PatchOIDCSettings oidcPatch = new PatchOIDCSettings();
        oidcPatch.setClientRegistrationSettings(Optional.of(clientRegistrationPatch));

        PatchDomain patchDomain = new PatchDomain();
        patchDomain.setOidc(Optional.of(oidcPatch));

        return domainService.patch(domain.getId(), patchDomain);
    }

    @Override
    public int getOrder() {
        return 6;
    }
}
