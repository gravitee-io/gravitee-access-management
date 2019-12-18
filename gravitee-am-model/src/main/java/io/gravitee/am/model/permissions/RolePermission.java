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
package io.gravitee.am.model.permissions;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum RolePermission {
    MANAGEMENT_SETTINGS(RoleScope.MANAGEMENT, ManagementPermission.SETTINGS),
    MANAGEMENT_DOMAIN(RoleScope.MANAGEMENT, ManagementPermission.DOMAIN),
    MANAGEMENT_IDENTITY_PROVIDER(RoleScope.MANAGEMENT, ManagementPermission.IDENTITY_PROVIDER),
    MANAGEMENT_AUDIT(RoleScope.MANAGEMENT, ManagementPermission.AUDIT),
    MANAGEMENT_REPORTER(RoleScope.MANAGEMENT, ManagementPermission.REPORTER),
    MANAGEMENT_SCOPE(RoleScope.MANAGEMENT, ManagementPermission.SCOPE),
    MANAGEMENT_USER(RoleScope.MANAGEMENT, ManagementPermission.USER),
    MANAGEMENT_GROUP(RoleScope.MANAGEMENT, ManagementPermission.GROUP),
    MANAGEMENT_ROLE(RoleScope.MANAGEMENT, ManagementPermission.ROLE),
    MANAGEMENT_TAG(RoleScope.MANAGEMENT, ManagementPermission.TAG),
    MANAGEMENT_FORM(RoleScope.MANAGEMENT, ManagementPermission.FORM),

    DOMAIN_SETTINGS(RoleScope.DOMAIN, DomainPermission.SETTINGS),
    DOMAIN_LOGIN_SETTINGS(RoleScope.DOMAIN, DomainPermission.LOGIN_SETTINGS),
    DOMAIN_APPLICATION(RoleScope.DOMAIN, DomainPermission.APPLICATION),
    DOMAIN_FORM(RoleScope.DOMAIN, DomainPermission.FORM),
    DOMAIN_EMAIL_TEMPLATE(RoleScope.DOMAIN, DomainPermission.EMAIL_TEMPLATE),
    DOMAIN_EXTENSION_POINT(RoleScope.DOMAIN, DomainPermission.EXTENSION_POINT),
    DOMAIN_IDENTITY_PROVIDER(RoleScope.DOMAIN, DomainPermission.IDENTITY_PROVIDER),
    DOMAIN_AUDIT(RoleScope.DOMAIN, DomainPermission.AUDIT),
    DOMAIN_USER_ACCOUNT(RoleScope.DOMAIN, DomainPermission.USER_ACCOUNT),
    DOMAIN_CERTIFICATE(RoleScope.DOMAIN, DomainPermission.CERTIFICATE),
    DOMAIN_USER(RoleScope.DOMAIN, DomainPermission.USER),
    DOMAIN_GROUP(RoleScope.DOMAIN, DomainPermission.GROUP),
    DOMAIN_ROLE(RoleScope.DOMAIN, DomainPermission.ROLE),
    DOMAIN_SCIM(RoleScope.DOMAIN, DomainPermission.SCIM),
    DOMAIN_SCOPE(RoleScope.DOMAIN, DomainPermission.SCOPE),
    DOMAIN_EXTENSION_GRANT(RoleScope.DOMAIN, DomainPermission.EXTENSION_POINT),
    DOMAIN_DCR(RoleScope.DOMAIN, DomainPermission.DCR),
    DOMAIN_REPORTER(RoleScope.DOMAIN, DomainPermission.REPORTER),
    DOMAIN_MEMBER(RoleScope.DOMAIN, DomainPermission.MEMBER),

    APPLICATION_SETTINGS(RoleScope.APPLICATION, ApplicationPermission.SETTINGS),
    APPLICATION_IDENTITY_PROVIDER(RoleScope.APPLICATION, ApplicationPermission.IDENTITY_PROVIDER),
    APPLICATION_FORM(RoleScope.APPLICATION, ApplicationPermission.FORM),
    APPLICATION_EMAIL_TEMPLATE(RoleScope.APPLICATION, ApplicationPermission.EMAIL_TEMPLATE),
    APPLICATION_METADATA(RoleScope.APPLICATION, ApplicationPermission.METADATA),
    APPLICATION_OAUTH2(RoleScope.APPLICATION, ApplicationPermission.METADATA),
    APPLICATION_USER_ACCOUNT(RoleScope.APPLICATION, ApplicationPermission.USER_ACCOUNT),
    APPLICATION_CERTIFICATE(RoleScope.APPLICATION, ApplicationPermission.CERTIFICATE),
    APPLICATION_MEMBER(RoleScope.APPLICATION, ApplicationPermission.MEMBER);

    RoleScope scope;
    Permission permission;

    RolePermission(RoleScope scope, Permission permission) {
        this.scope = scope;
        this.permission = permission;
    }

    public RoleScope getScope() {
        return scope;
    }

    public void setScope(RoleScope scope) {
        this.scope = scope;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }
}
