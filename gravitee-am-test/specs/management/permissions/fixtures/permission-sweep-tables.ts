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

import { PermissionEndpoint } from './permission-endpoints.generated';

/**
 * Shared between `endpoint-permission-sweep` (which proves a caller *without* a permission is
 * refused) and `permission-sufficiency` (which proves the documented permission *alone* admits).
 * Both drive the same generated endpoint table, so keeping these corrections in one place stops
 * the two sweeps disagreeing about what the table means.
 */

/**
 * Routes whose OpenAPI description names a different permission to the one actually enforced.
 * Each was found by these sweeps and confirmed against the guard in the resource class; corrected
 * here rather than in the generated file, which stays a faithful copy of the spec.
 *
 *  - groups/tags/entrypoints are documented as ORGANIZATION[LIST] but check the resource-specific
 *    permission, which is what a caller holding only that permission demonstrates.
 *  - device-identifiers documents `domain_device_identifiers_list`, which is not a permission at
 *    all — the enum constant is singular, and the plural form makes role updates fail (AM-7477).
 */
export const DOCUMENTED_PERMISSION_OVERRIDES: Record<string, string> = {
  '/organizations/{organizationId}/groups': 'organization_group_list',
  '/organizations/{organizationId}/tags': 'organization_tag_list',
  '/organizations/{organizationId}/entrypoints': 'organization_entrypoint_list',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/device-identifiers': 'domain_device_identifier_list',
};

/**
 * Routes neither sweep can drive, each for a stated reason. Kept as data so the exclusions are
 * visible and countable rather than quietly missing from the generated set.
 */
export const EXCLUDED_ROUTES: Record<string, string> = {
  // Mandatory query parameter that cannot be supplied generically — the request is rejected by
  // bean validation (400 "[formTemplate: must not be null]") before authorisation is reached.
  '/organizations/{organizationId}/forms': 'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/forms': 'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/emails': 'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/forms':
    'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/emails':
    'requires a template query parameter',

  // Answer 500 before any permission check, so an unauthorised caller receives a server error
  // rather than a refusal. Same defect class as AM-7476; re-include once fixed.
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/analytics': 'AM-7476 class: 500 before authorisation',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/analytics':
    'AM-7476 class: 500 before authorisation',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/search/_cursor':
    'AM-7476 class: 500 before authorisation',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/protected-resources':
    'AM-7476: type query parameter parsed before the permission check',
};

/**
 * Additional exclusions that apply only when a test *grants* the documented permission and expects
 * to be admitted. They are deliberately not in the shared table above: a caller who holds nothing
 * is still correctly refused on that route, so the negative sweep must keep covering it.
 */
export const SUFFICIENCY_ONLY_EXCLUDED: Record<string, string> = {
  // INSTALLATION is only relevant to the PLATFORM tier, so it cannot be granted by an
  // organization-assignable role and this technique cannot reach it.
  '/platform/installation': 'PLATFORM-tier permission, not grantable at organization level',
};

/** The permission a route really enforces, preferring a confirmed override over the spec. */
export const requiredPermission = (endpoint: PermissionEndpoint): string =>
  DOCUMENTED_PERMISSION_OVERRIDES[endpoint.route] ?? endpoint.permission;
