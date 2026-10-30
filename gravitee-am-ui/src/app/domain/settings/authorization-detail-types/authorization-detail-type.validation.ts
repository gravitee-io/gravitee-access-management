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
import { parse, ParseError, printParseErrorCode } from 'jsonc-parser';
import moment from 'moment';

import type { Editor } from 'codemirror';

export const TYPE_MAX_LENGTH = 512;
export const NAME_MAX_LENGTH = 255;
export const DESCRIPTION_MAX_LENGTH = 1024;
export const SCHEMA_MAX_LENGTH = 65536;
export const TYPE_PATTERN = '[\\x21-\\x7E]*';
export const NAME_PATTERN = '.*\\S.*';

const EXPIRES_IN_MAX_SECONDS = 2147483647;

export const SCHEMA_EDITOR_OPTIONS = {
  lineNumbers: true,
  lineWrapping: true,
  mode: 'application/json',
  foldGutter: true,
  gutters: ['CodeMirror-lint-markers', 'CodeMirror-linenumbers', 'CodeMirror-foldgutter'],
  lint: { getAnnotations: lintSchema },
};

export function schemaError(schema: string): string | null {
  if (!schema?.trim()) {
    return null;
  }
  if (schema.length > SCHEMA_MAX_LENGTH) {
    return `Schema must be at most ${SCHEMA_MAX_LENGTH} characters`;
  }
  try {
    JSON.parse(schema);
    return null;
  } catch {
    return 'Schema must be valid JSON';
  }
}

export function schemaExample(type: string): string {
  return JSON.stringify(
    {
      type: 'object',
      required: ['type', 'actions'],
      properties: {
        type: { const: type },
        actions: { type: 'array', items: { type: 'string' } },
      },
    },
    null,
    2,
  );
}

/** Rounds numbers beyond double precision and drops duplicate keys. */
export function formatSchema(schema: string): string {
  return JSON.stringify(JSON.parse(schema), null, 2);
}

export function saveErrorMessage(error: any): string {
  return error?.status === 403 ? 'Access denied' : error?.error?.message || 'Server error';
}

function lintSchema(text: string, _options: unknown, editor: Editor) {
  if (!schemaError(text) || text.length > SCHEMA_MAX_LENGTH) {
    return [];
  }
  const errors: ParseError[] = [];
  parse(text, errors, { disallowComments: true, allowTrailingComma: false, allowEmptyContent: false });
  const [error] = errors;
  const start = Math.min(error?.offset ?? text.length, text.replace(/\s+$/, '').length - 1);
  return [
    {
      message: error ? describeParseError(error) : 'Invalid JSON',
      severity: 'error',
      from: editor.posFromIndex(start),
      to: editor.posFromIndex(start + Math.max(error?.length ?? 1, 1)),
    },
  ];
}

function describeParseError(error: ParseError): string {
  const words = printParseErrorCode(error.error).replace(/([a-z])([A-Z])/g, '$1 $2');
  return words.charAt(0) + words.slice(1).toLowerCase();
}

export function expiresInSeconds(duration: number, unit: moment.unitOfTime.DurationConstructor): number | undefined {
  return isSet(duration) && unit ? moment.duration(duration, unit).asSeconds() : undefined;
}

export function expiryError(duration: number, unit: moment.unitOfTime.DurationConstructor): string | null {
  if (!isSet(duration)) {
    return null;
  }
  if (!unit) {
    return 'Select a unit for the duration';
  }
  const seconds = expiresInSeconds(duration, unit);
  if (seconds < 1) {
    return 'Duration must be at least 1 second';
  }
  if (!Number.isInteger(seconds)) {
    return 'Duration must be a whole number of seconds';
  }
  if (seconds > EXPIRES_IN_MAX_SECONDS) {
    return 'Duration is too long';
  }
  return null;
}

function isSet(duration: number): boolean {
  return duration !== null && duration !== undefined;
}
