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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.management.handlers.management.api.resources.AbstractResource;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.service.DomainService;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import java.util.Map;
import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AbstractDomainResource extends AbstractResource {

    @Autowired
    protected DomainService domainService;

    @Context
    protected ResourceContext resourceContext;

    protected Domain filterDomainInfos(Domain domain) {
        Domain filteredDomain = new Domain();
        filteredDomain.setId(domain.getId());
        filteredDomain.setHrid(domain.getHrid());
        filteredDomain.setName(domain.getName());
        filteredDomain.setDescription(domain.getDescription());
        filteredDomain.setEnabled(domain.isEnabled());
        filteredDomain.setCreatedAt(domain.getCreatedAt());
        filteredDomain.setUpdatedAt(domain.getUpdatedAt());

        return filteredDomain;
    }

    protected Domain filterDomainInfos(Domain domain, Map<ReferenceType, Map<Permission, Set<Acl>>> userPermissions) {

        Domain filteredDomain = new Domain();

        if (hasAnyPermission(userPermissions, Permission.DOMAIN, Acl.READ)) {
            filteredDomain.setId(domain.getId());
            filteredDomain.setHrid(domain.getHrid());
            filteredDomain.setName(domain.getName());
            filteredDomain.setDescription(domain.getDescription());
            filteredDomain.setEnabled(domain.isEnabled());
            filteredDomain.setCreatedAt(domain.getCreatedAt());
            filteredDomain.setUpdatedAt(domain.getUpdatedAt());
            filteredDomain.setPath(domain.getPath());
            filteredDomain.setVhostMode(domain.isVhostMode());
            filteredDomain.setVhosts(domain.getVhosts());
            filteredDomain.setReferenceType(domain.getReferenceType());
            filteredDomain.setReferenceId(domain.getReferenceId());
            filteredDomain.setPasswordSettings(domain.getPasswordSettings());
            filteredDomain.setMaster(domain.isMaster());
        }

        if(hasAnyPermission(userPermissions, Permission.DOMAIN_ALERT, Acl.READ)) {
            filteredDomain.setAlertEnabled(domain.isAlertEnabled());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_OPENID, Acl.READ)) {
            filteredDomain.setOidc(domain.getOidc());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_SAML, Acl.READ)) {
            filteredDomain.setSaml(domain.getSaml());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_UMA, Acl.READ)) {
            filteredDomain.setUma(domain.getUma());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_SCIM, Acl.READ)) {
            filteredDomain.setScim(domain.getScim());
        }

        if (hasAnyPermission(userPermissions, Permission.DOMAIN_SETTINGS, Acl.READ)) {
            filteredDomain.setLoginSettings(domain.getLoginSettings());
            filteredDomain.setWebAuthnSettings(domain.getWebAuthnSettings());
            filteredDomain.setAccountSettings(domain.getAccountSettings());
            filteredDomain.setSelfServiceAccountManagementSettings(domain.getSelfServiceAccountManagementSettings());
            filteredDomain.setTags(domain.getTags());
            filteredDomain.setCorsSettings(domain.getCorsSettings());
        }

        return filteredDomain;
    }
}
