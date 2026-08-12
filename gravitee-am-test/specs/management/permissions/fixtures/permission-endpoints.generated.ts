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

/* eslint-disable */
// GENERATED FILE — do not edit by hand.
// Regenerate with: node scripts/generate-permission-endpoints.js
//
// Source: docs/mapi/openapi.yaml, from each operation's stated permission requirement.
// Only bodyless operations (GET, DELETE) whose path parameters are all fillable with real ids are
// included, so a refusal can only be about permissions.

export interface PermissionEndpoint {
  method: 'GET' | 'DELETE';
  /** OpenAPI route, with {organizationId}/{environmentId}/{domain}/{application} placeholders. */
  route: string;
  /** Flattened permission the operation documents as required, e.g. "domain_user_list". */
  permission: string;
  summary: string;
}

export const PERMISSION_ENDPOINTS: PermissionEndpoint[] = [
  {
    method: 'GET',
    route: '/organizations/{organizationId}/audits',
    permission: 'organization_audit_list',
    summary: 'List audit logs for the organization',
  },
  { method: 'GET', route: '/organizations/{organizationId}/entrypoints', permission: 'organization_list', summary: 'List entrypoints' },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments',
    permission: 'environment_list',
    summary: 'List all the environments',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/data-planes',
    permission: 'data_plane_read',
    summary: 'List of data planes',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains',
    permission: 'domain_list',
    summary: 'List security domains for an environment',
  },
  {
    method: 'DELETE',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}',
    permission: 'domain_delete',
    summary: 'Delete the security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}',
    permission: 'domain_read',
    summary: 'Get a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/alerts/notifiers',
    permission: 'domain_alert_notifier_list',
    summary: 'List alert notifiers',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/alerts/triggers',
    permission: 'domain_alert_list',
    summary: 'List alert triggers',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/analytics',
    permission: 'domain_analytics_read',
    summary: 'Find domain analytics',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications',
    permission: 'application_list',
    summary: 'List registered applications for a security domain',
  },
  {
    method: 'DELETE',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}',
    permission: 'application_delete',
    summary: 'Delete an application',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}',
    permission: 'application_read',
    summary: 'Get an application',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/analytics',
    permission: 'application_analytics_read',
    summary: 'Find application analytics',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/emails',
    permission: 'application_email_template_read',
    summary: 'Find a email for an application',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/flows',
    permission: 'application_flow_list',
    summary: 'List registered flows for an application',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/forms',
    permission: 'application_form_read',
    summary: 'Find a form for an application',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/members',
    permission: 'application_member_list',
    summary: 'List members for an application',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/members/permissions',
    permission: 'application_read',
    summary: "List application member's permissions",
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/resources',
    permission: 'application_resource_list',
    summary: 'List resources for an application',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/secrets',
    permission: 'application_openid_list',
    summary: 'List secrets of an application',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/search',
    permission: 'application_list',
    summary: 'List applications with cursor-based pagination',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/search/_cursor',
    permission: 'application_list',
    summary: 'List applications with cursor-based pagination',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/audits',
    permission: 'domain_audit_list',
    summary: 'List audit logs for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/auth-device-notifiers',
    permission: 'domain_authdevice_notifier_list',
    summary: 'List registered Authentication Device Notifiers for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/authorization-engines',
    permission: 'domain_authorization_engine_list',
    summary: 'List registered authorization engines for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/bot-detections',
    permission: 'domain_bot_detection_list',
    summary: 'List registered bot detections for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/certificates',
    permission: 'domain_certificate_list',
    summary: 'List registered certificates for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/device-identifiers',
    permission: 'domain_device_identifiers_list',
    summary: 'List registered device identifiers for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/emails',
    permission: 'domain_email_template_read',
    summary: 'Find a email',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/entrypoints',
    permission: 'domain_read',
    summary: 'Get the matching gateway entrypoint of the domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/extensionGrants',
    permission: 'domain_extension_grant_list',
    summary: 'List registered extension grants for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/factors',
    permission: 'domain_factor_list',
    summary: 'List registered factors for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/flows',
    permission: 'domain_flow_list',
    summary: 'List registered flows for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/forms',
    permission: 'domain_form_read',
    summary: 'Find a form',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/groups',
    permission: 'domain_group_list',
    summary: 'List groups for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/identities',
    permission: 'domain_identity_provider_list',
    summary: 'List registered identity providers for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/members',
    permission: 'domain_member_list',
    summary: 'List members for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/members/permissions',
    permission: 'domain_read',
    summary: "List domain member's permissions",
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/password-policies',
    permission: 'domain_settings_read',
    summary: 'List registered password policies for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/protected-resources',
    permission: 'protected_resource_list',
    summary: 'List registered protected resources for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/reporters',
    permission: 'domain_reporter_list',
    summary: 'List registered reporters for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/resources',
    permission: 'domain_resource_list',
    summary: 'List registered resources for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/roles',
    permission: 'domain_role_list',
    summary: 'List registered roles for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/scopes',
    permission: 'domain_scope_list',
    summary: 'List scopes for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/themes',
    permission: 'domain_theme_list',
    summary: 'List themes on the specified security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/trust-domains',
    permission: 'domain_trust_domain_list',
    summary: 'List trust domains registered against the security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/users',
    permission: 'domain_user_list',
    summary: 'List users for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/environments/{environmentId}/members/permissions',
    permission: 'environment_read',
    summary: "List environment member's permissions",
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/forms',
    permission: 'organization_form_read',
    summary: 'Find an organization form template',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/groups',
    permission: 'organization_list',
    summary: 'List groups of the organization',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/identities',
    permission: 'organization_identity_provider_list',
    summary: 'List registered identity providers of the organization',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/members',
    permission: 'organization_member_list',
    summary: 'List members for an organization',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/reporters',
    permission: 'organization_reporter_list',
    summary: 'List registered reporters for a security domain',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/roles',
    permission: 'organization_role_list',
    summary: 'List registered roles of the organization',
  },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/settings',
    permission: 'organization_settings_read',
    summary: 'Get organization main settings',
  },
  { method: 'GET', route: '/organizations/{organizationId}/tags', permission: 'organization_list', summary: 'List sharding tags' },
  {
    method: 'GET',
    route: '/organizations/{organizationId}/users',
    permission: 'organization_user_list',
    summary: 'List users of the organization',
  },
  { method: 'GET', route: '/platform/installation', permission: 'installation_read', summary: 'Get installation information' },
];
