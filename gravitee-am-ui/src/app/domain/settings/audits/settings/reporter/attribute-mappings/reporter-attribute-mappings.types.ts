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

/** One extra attribute a reporter exports, read from the audit context by expression. */
export interface ReporterAttributeMapping {
  expression: string;
  exportedName: string;
}

export const MAX_ATTRIBUTE_MAPPINGS = 20;
export const MAX_EXPRESSION_LENGTH = 512;
export const MAX_EXPORTED_NAME_LENGTH = 64;
export const EXPORTED_NAME_PATTERN = '^[A-Za-z0-9_]+$';

export interface AttributeMappingsSeed {
  mappings: ReporterAttributeMapping[];
  eventTypes: string[];
}

export interface AttributeMappingsChange extends AttributeMappingsSeed {
  isValid: boolean;
}

/** Reporter types whose payload carries the exported attributes. */
export const REPORTER_TYPES_SUPPORTING_ATTRIBUTE_MAPPINGS = ['reporter-am-kafka', 'reporter-am-tcp', 'reporter-am-file'];

export function supportsAttributeMappings(reporter: any): boolean {
  return !reporter?.system && REPORTER_TYPES_SUPPORTING_ATTRIBUTE_MAPPINGS.includes(reporter?.type);
}

const exportedNameRegExp = new RegExp(EXPORTED_NAME_PATTERN);

export function isValidExportedName(exportedName: string): boolean {
  return !!exportedName && exportedName.length <= MAX_EXPORTED_NAME_LENGTH && exportedNameRegExp.test(exportedName);
}

/** Expressions reach the API exactly as typed, so surrounding whitespace is significant. */
export function isValidExpression(expression: string): boolean {
  return !!expression && expression.trim().length > 0 && expression.length <= MAX_EXPRESSION_LENGTH;
}

/** The rules ReporterAttributeMappingsValidator applies server-side, one message per broken rule. */
export function attributeMappingErrors(mappings: ReporterAttributeMapping[], eventTypes: string[]): string[] {
  const errors: string[] = [];
  const rows = mappings ?? [];
  const types = eventTypes ?? [];

  if (rows.length > MAX_ATTRIBUTE_MAPPINGS) {
    errors.push(`A reporter can export at most ${MAX_ATTRIBUTE_MAPPINGS} attributes.`);
  }
  if (rows.some((mapping) => !isValidExpression(mapping?.expression))) {
    errors.push(`Every attribute mapping needs an expression.`);
  }
  if (rows.some((mapping) => !isValidExportedName(mapping?.exportedName))) {
    errors.push(`Exported names use letters, digits and underscore only, up to ${MAX_EXPORTED_NAME_LENGTH} characters.`);
  }

  const names = rows.map((mapping) => mapping?.exportedName).filter((name) => isValidExportedName(name));
  const duplicates = [...new Set(names.filter((name, index) => names.indexOf(name) !== index))];
  duplicates.forEach((name) => errors.push(`Exported name "${name}" is used more than once.`));

  if (types.length > 0 && rows.length === 0) {
    errors.push('Event types apply to attribute mappings; add one to keep this selection.');
  }

  return errors;
}
