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

type ApplicationTypeName = 'WEB' | 'NATIVE' | 'BROWSER' | 'SERVICE' | 'RESOURCE_SERVER' | 'AGENT';

interface ApplicationNavRule {
    /** The menu shows the entry when the user has one of these permissions. */
    readonly perms: readonly string[];
    /** The application types that have the entry. All types when absent. */
    readonly types?: readonly ApplicationTypeName[];
    readonly requiresSaml?: boolean;
    readonly requiresAgent?: boolean;
}

interface ApplicationNavItem extends Partial<ApplicationNavRule> {
    readonly key: string;
    readonly title: string;
}

interface ApplicationNavGroup {
    readonly label: string;
    /** The rule of the parent route that holds the items, such as `design` or `settings`. */
    readonly parent?: ApplicationNavRule;
    readonly items: readonly ApplicationNavItem[];
}

const USER_FACING: readonly ApplicationTypeName[] = ['WEB', 'NATIVE', 'BROWSER', 'RESOURCE_SERVER'];
const APPLICATION_SETTINGS: ApplicationNavRule = {
    perms: ['application_settings_read', 'application_openid_read', 'application_certificate_list'],
};

export const APPLICATION_NAV_GROUPS: readonly ApplicationNavGroup[] = [
    {
        label: 'Application',
        items: [
            { key: 'overview', title: 'Overview' },
            { key: 'endpoints', title: 'Endpoints' },
            {
                key: 'idp',
                title: 'Identity Providers',
                perms: ['application_identity_provider_list'],
                types: [...USER_FACING, 'AGENT'],
            },
            { key: 'analytics', title: 'Analytics', perms: ['application_analytics_list'], types: USER_FACING },
        ],
    },
    {
        label: 'Design',
        parent: {
            perms: ['application_email_template_list', 'application_email_template_read', 'application_form_list', 'application_form_read'],
            types: [...USER_FACING, 'SERVICE'],
        },
        items: [
            { key: 'design/forms', title: 'Forms', perms: ['application_form_list', 'application_form_read'], types: USER_FACING },
            {
                key: 'design/emails',
                title: 'Emails',
                perms: ['application_email_template_list', 'application_email_template_read'],
                types: USER_FACING,
            },
            { key: 'design/flows', title: 'Flows', perms: ['application_flow_list', 'application_flow_read'] },
        ],
    },
    {
        label: 'Settings',
        parent: APPLICATION_SETTINGS,
        items: [
            { key: 'settings/general', title: 'General', perms: ['application_settings_read'] },
            { key: 'settings/agent', title: 'Agent Identity', perms: ['application_settings_read'], requiresAgent: true },
            { key: 'settings/metadata', title: 'Application metadata', perms: ['application_settings_read'] },
            { key: 'settings/oauth2', title: 'OAuth 2.0 / OIDC', perms: ['application_openid_read'] },
            {
                key: 'settings/saml2',
                title: 'SAML 2.0',
                perms: ['application_saml_read'],
                types: ['WEB', 'NATIVE', 'BROWSER'],
                requiresSaml: true,
            },
            { key: 'settings/login', title: 'Login', perms: ['application_settings_read'], types: USER_FACING },
            { key: 'settings/members', title: 'Administrative roles', perms: ['application_member_list'] },
        ],
    },
    {
        label: 'Security',
        parent: APPLICATION_SETTINGS,
        items: [
            {
                key: 'settings/secrets-certificates',
                title: 'Secrets & Certificates',
                perms: ['application_settings_read', 'application_certificate_list'],
            },
            { key: 'settings/factors', title: 'Multifactor Auth', perms: ['application_factor_list'], types: USER_FACING },
            { key: 'settings/account', title: 'User Accounts', perms: ['application_settings_read'], types: USER_FACING },
            { key: 'settings/session', title: 'Session management', perms: ['application_settings_read'] },
            { key: 'settings/resources', title: 'Resources', perms: ['application_resource_list'], types: ['RESOURCE_SERVER'] },
        ],
    },
];

interface ApplicationNavContext {
    /** The application type as the API returns it, in lower case. */
    readonly type: string;
    readonly samlEnabled: boolean;
    readonly hasAny: (...permissions: string[]) => boolean;
}

type SectionAccess = 'open' | 'forbidden' | 'absent';

function ruleAccess(rule: Partial<ApplicationNavRule>, { type, samlEnabled, hasAny }: ApplicationNavContext): SectionAccess {
    const appType = type.toUpperCase();
    if (
        (rule.types && !rule.types.some(allowed => allowed === appType)) ||
        (rule.requiresSaml && !samlEnabled) ||
        (rule.requiresAgent && appType !== 'AGENT')
    ) {
        return 'absent';
    }
    return rule.perms && !hasAny(...rule.perms) ? 'forbidden' : 'open';
}

/**
 * Whether a section of the application menu opens, with the rules of its entry: `forbidden` for a missing permission,
 * `absent` when the application type or the domain does not have the section. The group rules only hide the group in
 * the menu, as Angular's parent routes have no guard.
 */
export function applicationSectionAccess(section: string, context: ApplicationNavContext): SectionAccess {
    const item = APPLICATION_NAV_GROUPS.flatMap(group => group.items).find(candidate => candidate.key === section);
    if (!item) {
        throw new Error(`No application menu entry for the section ${section}`);
    }
    return ruleAccess(item, context);
}

/** The application menu, with the entries the user, the application type and the domain allow. */
export function applicationNavGroups(groups: readonly ApplicationNavGroup[], context: ApplicationNavContext): NavGroup[] {
    const allows = (rule: Partial<ApplicationNavRule>) => ruleAccess(rule, context) === 'open';
    return groups
        .filter(group => !group.parent || allows(group.parent))
        .map(group => ({ label: group.label, items: group.items.filter(allows).map(({ key, title }) => ({ key, title })) }))
        .filter(group => group.items.length > 0);
}
