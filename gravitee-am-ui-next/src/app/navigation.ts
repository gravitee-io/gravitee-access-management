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

/**
 * The menus follow the Gamma prototype (gamma-baby `modules/am/navigation.ts`). The main sidebar always shows the
 * domain menu, below the domain switcher. Settings and Organization open a context sidebar with their own groups.
 */
export const ORGANIZATION_KEY = 'organization';

export const MAIN_NAV_GROUPS: NavGroup[] = [
    {
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
    },
    {
        label: 'Organization',
        items: [{ key: ORGANIZATION_KEY, title: 'Organization', icon: UserCogIcon }],
    },
];

/** Keys of a domain's settings. `forms` is not in the prototype: it holds the domain login form the spike already edits. */
export const DOMAIN_SETTINGS_NAV_GROUPS: NavGroup[] = [
    {
        label: 'Settings',
        items: [
            { key: 'general', title: 'General' },
            { key: 'entrypoints', title: 'Entrypoints' },
            { key: 'web-protection', title: 'Web Protection' },
            { key: 'login', title: 'Login' },
            { key: 'members', title: 'Administrative Roles' },
        ],
    },
    {
        label: 'Design',
        items: [
            { key: 'theme', title: 'Theme' },
            { key: 'forms', title: 'Forms' },
            { key: 'texts', title: 'Texts' },
            { key: 'emails', title: 'Emails' },
            { key: 'flows', title: 'Flows' },
        ],
    },
    { label: 'Identities', items: [{ key: 'providers', title: 'Providers' }] },
    {
        label: 'Security',
        items: [
            { key: 'webauthn', title: 'WebAuthn' },
            { key: 'secrets', title: 'Client Secrets' },
            { key: 'factors', title: 'Multifactor Auth' },
            { key: 'password-policies', title: 'Password Policies' },
            { key: 'audits', title: 'Audit Log' },
            { key: 'account', title: 'User Accounts' },
            { key: 'certificates', title: 'Certificates' },
            { key: 'trusted-domains', title: 'Trusted Domains' },
            { key: 'bot-detection', title: 'Bot Detection' },
            { key: 'device-identifiers', title: 'Device Identifiers' },
        ],
    },
    { label: 'Resources', items: [{ key: 'services', title: 'Services' }] },
    {
        label: 'User Management',
        items: [
            { key: 'users', title: 'Users' },
            { key: 'groups', title: 'Groups' },
            { key: 'roles', title: 'Roles' },
            { key: 'scim', title: 'SCIM' },
            { key: 'self-service-account', title: 'Self-service Account' },
        ],
    },
    {
        label: 'OAuth 2.0',
        items: [
            { key: 'scopes', title: 'Scopes' },
            { key: 'extension-grants', title: 'Extension Grants' },
            { key: 'uma', title: 'UMA' },
            { key: 'token-exchange', title: 'Token Exchange' },
            { key: 'cimd', title: 'CIMD' },
            { key: 'dpop', title: 'DPoP' },
        ],
    },
    {
        label: 'OpenID',
        items: [
            { key: 'client-registration', title: 'Client Registration' },
            { key: 'oidc-profile', title: 'Security Profile' },
            { key: 'ciba', title: 'CIBA' },
        ],
    },
    { label: 'Workload Identity', items: [{ key: 'spiffe', title: 'SPIFFE' }] },
    { label: 'SAML 2.0', items: [{ key: 'saml2', title: 'SAML 2.0' }] },
];

export const ORGANIZATION_NAV_GROUPS: NavGroup[] = [
    {
        label: 'Console',
        items: [
            { key: 'general', title: 'Authentication' },
            { key: 'members', title: 'Administrative Roles' },
            { key: 'forms', title: 'Forms' },
            { key: 'providers', title: 'Identity Providers' },
        ],
    },
    {
        label: 'User Management',
        items: [
            { key: 'users', title: 'Users' },
            { key: 'groups', title: 'Groups' },
            { key: 'roles', title: 'Roles' },
        ],
    },
    {
        label: 'Gateway',
        items: [
            { key: 'tags', title: 'Sharding Tags' },
            { key: 'entrypoints', title: 'Entrypoints' },
        ],
    },
    { label: 'Audit', items: [{ key: 'audits', title: 'Audit Log' }] },
    { label: 'Cockpit', items: [{ key: 'cockpit', title: 'Cockpit' }] },
];

/** An application's sections. Keys with a slash are nested paths, e.g. `settings/general`. */
export const APPLICATION_NAV_GROUPS: NavGroup[] = [
    {
        label: 'Application',
        items: [
            { key: 'overview', title: 'Overview' },
            { key: 'endpoints', title: 'Endpoints' },
            { key: 'identity-providers', title: 'Identity Providers' },
            { key: 'analytics', title: 'Analytics' },
        ],
    },
    {
        label: 'Design',
        items: [
            { key: 'design/forms', title: 'Forms' },
            { key: 'design/emails', title: 'Emails' },
            { key: 'design/flows', title: 'Flows' },
        ],
    },
    {
        label: 'Settings',
        items: [
            { key: 'settings/general', title: 'General' },
            { key: 'settings/agent', title: 'Agent Identity' },
            { key: 'settings/metadata', title: 'Metadata' },
            { key: 'settings/oauth2', title: 'OAuth 2.0 / OIDC' },
            { key: 'settings/saml2', title: 'SAML 2.0' },
            { key: 'settings/login', title: 'Login' },
            { key: 'settings/members', title: 'Administrative Roles' },
        ],
    },
    {
        label: 'Security',
        items: [
            { key: 'settings/secrets', title: 'Secrets & Certificates' },
            { key: 'settings/factors', title: 'Multifactor Auth' },
            { key: 'settings/account', title: 'User Accounts' },
            { key: 'settings/session', title: 'Session Management' },
            { key: 'settings/resources', title: 'Resources' },
        ],
    },
];

export function navItems(groups: NavGroup[]) {
    return groups.flatMap(group => group.items);
}
