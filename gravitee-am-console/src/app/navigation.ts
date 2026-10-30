/*
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
import type { NavGroup } from '@gravitee/graphene-core';
import {
    AppWindowIcon,
    BellIcon,
    BotIcon,
    BoxesIcon,
    LayoutDashboardIcon,
    SettingsIcon,
    ShieldCheckIcon,
    UserCogIcon,
} from '@gravitee/graphene-core/icons';

export const ORGANIZATION_KEY = 'organization';

export const DOMAIN_NAV_GROUP: NavGroup = {
    label: 'Domain',
    items: [
        { key: 'dashboard', title: 'Dashboard', icon: LayoutDashboardIcon },
        { key: 'applications', title: 'Applications', icon: AppWindowIcon },
        { key: 'agents', title: 'Agents', icon: BotIcon },
        { key: 'mcp-servers', title: 'MCP Servers', icon: BoxesIcon },
        { key: 'authorization', title: 'Authorization', icon: ShieldCheckIcon },
        { key: 'alerts', title: 'Alerts', icon: BellIcon },
        { key: 'settings', title: 'Settings', icon: SettingsIcon },
    ],
};

export const MAIN_NAV_GROUPS: NavGroup[] = [
    DOMAIN_NAV_GROUP,
    {
        label: 'Organization',
        items: [{ key: ORGANIZATION_KEY, title: 'Organization', icon: UserCogIcon }],
    },
];

export function navItems(groups: NavGroup[]) {
    return groups.flatMap(group => group.items);
}

interface PermittedNavItem {
    readonly key: string;
    readonly title: string;
    /** The menu shows the item when the user has one of these permissions. */
    readonly perms: readonly string[];
}

interface PermittedNavGroup {
    readonly label: string;
    readonly items: readonly PermittedNavItem[];
}

/** The keys are the route segments of the Angular domain settings. */
export const DOMAIN_SETTINGS_NAV_GROUPS: readonly PermittedNavGroup[] = [
    {
        label: 'Settings',
        items: [
            { key: 'general', title: 'General', perms: ['domain_settings_read'] },
            { key: 'entrypoints', title: 'Entrypoints', perms: ['domain_settings_read'] },
            { key: 'web-protection', title: 'Web Protection', perms: ['domain_settings_read'] },
            { key: 'login', title: 'Login', perms: ['domain_settings_read'] },
            { key: 'members', title: 'Administrative Roles', perms: ['domain_member_list'] },
        ],
    },
    {
        label: 'Design',
        items: [
            { key: 'theme', title: 'Theme', perms: ['domain_theme_list', 'domain_theme_read', 'domain_form_list', 'domain_form_read'] },
            {
                key: 'texts',
                title: 'Texts',
                perms: [
                    'domain_i18n_dictionary_list',
                    'domain_i18n_dictionary_create',
                    'domain_i18n_dictionary_update',
                    'domain_i18n_dictionary_delete',
                ],
            },
            { key: 'emails', title: 'Emails', perms: ['domain_email_template_list', 'domain_email_template_read'] },
            { key: 'flows', title: 'Flows', perms: ['domain_flow_list'] },
        ],
    },
    { label: 'Identities', items: [{ key: 'providers', title: 'Providers', perms: ['domain_identity_provider_list'] }] },
    {
        label: 'Security',
        items: [
            { key: 'webauthn', title: 'WebAuthn', perms: ['domain_settings_read'] },
            { key: 'secrets', title: 'Client Secrets', perms: ['domain_settings_read'] },
            { key: 'factors', title: 'Multifactor Auth', perms: ['domain_factor_list'] },
            { key: 'password-policies', title: 'Password Policies', perms: ['domain_settings_read'] },
            { key: 'audits', title: 'Audit Log', perms: ['domain_audit_list'] },
            { key: 'account', title: 'User Accounts', perms: ['domain_settings_read'] },
            { key: 'certificates', title: 'Certificates', perms: ['domain_certificate_list'] },
            { key: 'trust-domains', title: 'Trusted Domains', perms: ['domain_trust_domain_list'] },
            { key: 'bot-detection', title: 'Bot Detection', perms: ['domain_bot_detection_list'] },
            { key: 'device-identifier', title: 'Device Identifiers', perms: ['domain_device_identifier_list'] },
        ],
    },
    { label: 'Resources', items: [{ key: 'resources', title: 'Services', perms: ['domain_resource_list'] }] },
    {
        label: 'User Management',
        items: [
            { key: 'users', title: 'Users', perms: ['domain_user_list'] },
            { key: 'groups', title: 'Groups', perms: ['domain_group_list'] },
            { key: 'roles', title: 'Roles', perms: ['domain_role_list'] },
            { key: 'scim', title: 'SCIM', perms: ['domain_scim_read'] },
            { key: 'self-service-account', title: 'Self-service Account', perms: ['domain_settings_read'] },
        ],
    },
    {
        label: 'OAuth 2.0',
        items: [
            { key: 'scopes', title: 'Scopes', perms: ['domain_scope_list'] },
            { key: 'extensionGrants', title: 'Extension Grants', perms: ['domain_extension_grant_list'] },
            { key: 'uma', title: 'UMA', perms: ['domain_uma_read'] },
            { key: 'token-exchange', title: 'Token Exchange', perms: ['domain_openid_read'] },
            { key: 'cimd', title: 'CIMD', perms: ['domain_openid_read'] },
            { key: 'dpop', title: 'DPoP', perms: ['domain_openid_read'] },
        ],
    },
    {
        label: 'OpenID',
        items: [
            { key: 'dcr', title: 'Client Registration', perms: ['domain_openid_read'] },
            { key: 'oidc-profile', title: 'Security Profile', perms: ['domain_openid_read'] },
            { key: 'ciba', title: 'CIBA', perms: ['domain_openid_read'] },
        ],
    },
    { label: 'Workload Identity', items: [{ key: 'spiffe', title: 'SPIFFE', perms: ['domain_openid_read'] }] },
    { label: 'SAML 2.0', items: [{ key: 'saml2', title: 'SAML 2.0', perms: ['domain_saml_read'] }] },
];

export function permittedNavGroups(groups: readonly PermittedNavGroup[], hasAny: (...permissions: string[]) => boolean): NavGroup[] {
    return groups
        .map(group => ({
            label: group.label,
            items: group.items.filter(item => hasAny(...item.perms)).map(({ key, title }) => ({ key, title })),
        }))
        .filter(group => group.items.length > 0);
}
