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
import { APPLICATION_NAV_GROUPS, applicationNavGroups, ORGANIZATION_SETTINGS_NAV_GROUPS, permittedNavGroups } from '../navigation';

const granted =
    (...permissions: string[]) =>
    (...wanted: string[]) =>
        wanted.some(permission => permissions.includes(permission));

const keys = (type: string, hasAny: (...permissions: string[]) => boolean, samlEnabled = false) =>
    applicationNavGroups(APPLICATION_NAV_GROUPS, { type, samlEnabled, hasAny }).flatMap(group => group.items.map(item => item.key));

const ALL = granted(
    'application_identity_provider_list',
    'application_analytics_list',
    'application_form_list',
    'application_email_template_list',
    'application_flow_list',
    'application_settings_read',
    'application_openid_read',
    'application_saml_read',
    'application_member_list',
    'application_certificate_list',
    'application_factor_list',
    'application_resource_list',
);

describe('applicationNavGroups', () => {
    it('shows a web application every entry its type allows', () => {
        expect(keys('web', ALL, true)).toEqual([
            'overview',
            'endpoints',
            'idp',
            'analytics',
            'design/forms',
            'design/emails',
            'design/flows',
            'settings/general',
            'settings/metadata',
            'settings/oauth2',
            'settings/saml2',
            'settings/login',
            'settings/members',
            'settings/secrets-certificates',
            'settings/factors',
            'settings/account',
            'settings/session',
        ]);
    });

    it('hides the user-facing entries from a service application', () => {
        expect(keys('service', ALL, true)).toEqual([
            'overview',
            'endpoints',
            'design/flows',
            'settings/general',
            'settings/metadata',
            'settings/oauth2',
            'settings/members',
            'settings/secrets-certificates',
            'settings/session',
        ]);
    });

    it('shows the agent identity and hides the design to an agent', () => {
        const agent = keys('agent', ALL);

        expect(agent).toContain('settings/agent');
        expect(agent).toContain('idp');
        expect(agent.filter(key => key.startsWith('design/'))).toEqual([]);
    });

    it('shows SAML 2.0 only when the domain has SAML on', () => {
        expect(keys('web', ALL, false)).not.toContain('settings/saml2');
    });

    it('shows the resources only to a resource server', () => {
        expect(keys('resource_server', ALL)).toContain('settings/resources');
        expect(keys('web', ALL)).not.toContain('settings/resources');
    });

    it('shows only the entries the user has a permission for', () => {
        expect(keys('web', granted('application_member_list'))).toEqual(['overview', 'endpoints']);
        expect(keys('web', granted('application_settings_read', 'application_member_list'))).toEqual([
            'overview',
            'endpoints',
            'settings/general',
            'settings/metadata',
            'settings/login',
            'settings/members',
            'settings/secrets-certificates',
            'settings/account',
            'settings/session',
        ]);
    });
});

const organizationKeys = (granted: string[], cloudMode = false) =>
    permittedNavGroups(
        ORGANIZATION_SETTINGS_NAV_GROUPS,
        (...permissions) => permissions.some(permission => granted.includes(permission)),
        cloudMode,
    )
        .flatMap(group => group.items)
        .map(item => item.key);

describe('organization settings menu', () => {
    it('shows an item with the permission of its route', () => {
        expect(
            organizationKeys([
                'organization_settings_read',
                'organization_tag_list',
                'organization_entrypoint_list',
                'organization_form_list',
            ]),
        ).toEqual(['general', 'forms', 'tags', 'entrypoints']);
    });

    it('shows nothing without a permission', () => {
        expect(organizationKeys([])).toEqual([]);
    });

    it('shows Cockpit with installation_read, except in cloud mode', () => {
        expect(organizationKeys(['installation_read'])).toEqual(['cockpit']);
        expect(organizationKeys(['installation_read'], true)).toEqual([]);
    });
});
