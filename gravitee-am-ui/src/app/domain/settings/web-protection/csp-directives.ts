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

/**
 * CSP directive editing helpers.
 *
 * Per-field rules (name required, name characters, value required, no semicolons) are expressed as
 * native validators on the template. These mirror the service-layer CspSettingsValidator.
 */

export interface CspDirectiveRow {
  name: string;
  value: string;
}

/** Per-row advisory state, indexed alongside the rows themselves. */
export interface CspRowIssue {
  /** True for every occurrence after the first with the same (case-insensitive) name. */
  duplicate: boolean;
  /** Syntactically fine, but not a directive we know about. */
  unknownName: boolean;
  /** report-to is configured but AM sends no Reporting-Endpoints header. */
  reportToNeedsEndpoint: boolean;
}

export interface CspAnalysis {
  rows: CspRowIssue[];
  /** Problems that belong to the policy rather than to any one field. */
  formErrors: string[];
  /** True when the policy would be rejected for a reason no field-level validator catches. */
  hasBlockingIssue: boolean;
}

/** Directives that carry no value at all. */
export const CSP_NO_VALUE_DIRECTIVES: string[] = ['upgrade-insecure-requests', 'block-all-mixed-content'];

/** Directives whose value is optional — a bare `sandbox` is the most restrictive form. */
export const CSP_OPTIONAL_VALUE_DIRECTIVES: string[] = ['sandbox', 'trusted-types'];

export const CSP_REPORT_URI = 'report-uri';
export const CSP_REPORT_TO = 'report-to';

/** Rejects the characters the API rejects, and doubles as the no-semicolon rule for names. */
export const CSP_DIRECTIVE_NAME_PATTERN = '[A-Za-z0-9-]+';

/**
 * Values are kept verbatim, so only characters that cannot survive the round trip are forbidden.
 */
export const CSP_DIRECTIVE_VALUE_PATTERN = '[^;\\x00-\\x08\\x0a-\\x1f\\x7f]*';

/** Known directive names, used for autocomplete and the unrecognized-name hint. */
export const CSP_DIRECTIVE_NAMES: string[] = [
  'base-uri',
  'block-all-mixed-content',
  'child-src',
  'connect-src',
  'default-src',
  'fenced-frame-src',
  'font-src',
  'form-action',
  'frame-ancestors',
  'frame-src',
  'img-src',
  'manifest-src',
  'media-src',
  'object-src',
  'report-to',
  'report-uri',
  'require-trusted-types-for',
  'sandbox',
  'script-src',
  'script-src-attr',
  'script-src-elem',
  'style-src',
  'style-src-attr',
  'style-src-elem',
  'trusted-types',
  'upgrade-insecure-requests',
  'worker-src',
];

const canonical = (name: string): string => (name ?? '').trim().toLowerCase();

/** Trims and discards a single trailing `;`, which is optional in the API. */
function stripTrailingSemicolon(entry: string): string {
  const trimmed = (entry ?? '').trim();
  return trimmed.endsWith(';') ? trimmed.slice(0, -1).trim() : trimmed;
}

/**
 * Parses a stored `"name value"` entry into an editable row.
 *
 * Never returns null: an entry that does not parse cleanly still becomes a row so the operator can
 * see and fix it rather than having it silently disappear.
 */
export function parseCspDirective(entry: string): CspDirectiveRow {
  const stripped = stripTrailingSemicolon(entry);
  const separator = stripped.search(/[ \t]/);
  if (separator < 0) {
    return { name: stripped, value: '' };
  }
  return { name: stripped.slice(0, separator), value: stripped.slice(separator + 1).trim() };
}

/** Renders a row back to the storage form, without a trailing semicolon. */
export function serializeCspDirective(row: CspDirectiveRow): string {
  const name = (row.name ?? '').trim();
  const value = (row.value ?? '').trim();
  return value ? `${name} ${value}` : name;
}

export function parseCspDirectives(entries: string[]): CspDirectiveRow[] {
  return (entries ?? []).map(parseCspDirective);
}

export function serializeCspDirectives(rows: CspDirectiveRow[]): string[] {
  return (rows ?? []).map(serializeCspDirective);
}

/** False only for directives that take no value at all — those get a disabled value field. */
export function allowsValue(name: string): boolean {
  return !CSP_NO_VALUE_DIRECTIVES.includes(canonical(name));
}

/** True when a value must be supplied. Optional-value directives are neither required nor blocked. */
export function requiresValue(name: string): boolean {
  const key = canonical(name);
  return !CSP_NO_VALUE_DIRECTIVES.includes(key) && !CSP_OPTIONAL_VALUE_DIRECTIVES.includes(key);
}

export function isKnownDirective(name: string): boolean {
  return CSP_DIRECTIVE_NAMES.includes(canonical(name));
}

const isSyntacticallyValidName = (name: string): boolean => new RegExp(`^${CSP_DIRECTIVE_NAME_PATTERN}$`).test(name ?? '');

/**
 * Works out everything the per-field validators cannot: duplicate names, whether the policy has a
 * usable shape, and the advisory hints.
 */
export function analyzeCspDirectives(rows: CspDirectiveRow[], reportOnly: boolean): CspAnalysis {
  const directives = rows ?? [];
  const names = directives.map((row) => canonical(row.name));
  const hasReportTo = directives.some((row) => canonical(row.name) === CSP_REPORT_TO && (row.value ?? '').trim());
  const hasReportUri = directives.some((row) => canonical(row.name) === CSP_REPORT_URI && (row.value ?? '').trim());

  const issues: CspRowIssue[] = directives.map((row, index) => {
    const key = names[index];
    return {
      duplicate: key.length > 0 && names.indexOf(key) !== index,
      unknownName: key.length > 0 && isSyntacticallyValidName(row.name) && !isKnownDirective(key),
      reportToNeedsEndpoint: key === CSP_REPORT_TO && !hasReportUri,
    };
  });

  const formErrors: string[] = [];
  if (directives.length === 0) {
    formErrors.push('Add at least one directive, or turn off Enable CSP.');
  }
  if (reportOnly && !hasReportTo && !hasReportUri) {
    formErrors.push(`Report only needs a "${CSP_REPORT_URI}" or "${CSP_REPORT_TO}" directive with a value.`);
  }

  return {
    rows: issues,
    formErrors,
    hasBlockingIssue: formErrors.length > 0 || issues.some((issue) => issue.duplicate),
  };
}
