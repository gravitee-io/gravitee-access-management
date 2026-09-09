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
import {
  attributeMappingErrors,
  MAX_ATTRIBUTE_MAPPINGS,
  ReporterAttributeMapping,
  supportsAttributeMappings,
} from './reporter-attribute-mappings.types';

function mapping(exportedName: string, expression = 'value'): ReporterAttributeMapping {
  return { exportedName, expression };
}

describe('attributeMappingErrors', () => {
  it('accepts nothing configured', () => {
    expect(attributeMappingErrors([], [])).toEqual([]);
  });

  it('accepts a valid mapping', () => {
    expect(attributeMappingErrors([mapping('user_sub', "{#context.attributes['user'].id}")], ['USER_LOGIN'])).toEqual([]);
  });

  it('rejects more mappings than the reporter accepts', () => {
    const tooMany = Array.from({ length: MAX_ATTRIBUTE_MAPPINGS + 1 }, (_, i) => mapping(`field${i}`));

    expect(attributeMappingErrors(tooMany, [])).toContain('A reporter can export at most 20 attributes.');
  });

  it('accepts exactly the maximum number of mappings', () => {
    const atLimit = Array.from({ length: MAX_ATTRIBUTE_MAPPINGS }, (_, i) => mapping(`field${i}`));

    expect(attributeMappingErrors(atLimit, [])).toEqual([]);
  });

  it.each([
    ['an empty expression', ''],
    ['a whitespace-only expression', '   '],
    ['a tab-only expression', '\t'],
  ])('rejects %s', (_label, expression) => {
    expect(attributeMappingErrors([mapping('user_sub', expression)], [])).toContain('Every attribute mapping needs an expression.');
  });

  it('rejects an expression over 512 characters', () => {
    expect(attributeMappingErrors([mapping('user_sub', 'a'.repeat(513))], [])).toContain('Every attribute mapping needs an expression.');
  });

  it('accepts an expression of exactly 512 characters', () => {
    expect(attributeMappingErrors([mapping('user_sub', 'a'.repeat(512))], [])).toEqual([]);
  });

  it('accepts a literal expression, which the reporter exports verbatim', () => {
    expect(attributeMappingErrors([mapping('environment', 'production')], [])).toEqual([]);
  });

  it('accepts an expression with surrounding whitespace', () => {
    expect(attributeMappingErrors([mapping('environment', ' production ')], [])).toEqual([]);
  });

  it.each([['bad-name'], ['user.sub'], ['user sub'], [' user_sub'], ['naïve'], ['']])('rejects the exported name %p', (exportedName) => {
    expect(attributeMappingErrors([mapping(exportedName)], [])).toContain(
      'Exported names use letters, digits and underscore only, up to 64 characters.',
    );
  });

  it.each([['user_sub'], ['USER_SUB'], ['A_1'], ['_leading'], ['trailing_'], ['0']])('accepts the exported name %p', (exportedName) => {
    expect(attributeMappingErrors([mapping(exportedName)], [])).toEqual([]);
  });

  it('rejects an exported name over 64 characters', () => {
    expect(attributeMappingErrors([mapping('a'.repeat(65))], [])).toContain(
      'Exported names use letters, digits and underscore only, up to 64 characters.',
    );
  });

  it('accepts an exported name of exactly 64 characters', () => {
    expect(attributeMappingErrors([mapping('a'.repeat(64))], [])).toEqual([]);
  });

  it('rejects a repeated exported name', () => {
    expect(attributeMappingErrors([mapping('user_sub'), mapping('user_sub')], [])).toContain(
      'Exported name "user_sub" is used more than once.',
    );
  });

  it('treats exported names that differ only in case as distinct, as the API does', () => {
    expect(attributeMappingErrors([mapping('user_sub'), mapping('USER_SUB')], [])).toEqual([]);
  });

  it('rejects event types chosen without any mapping', () => {
    expect(attributeMappingErrors([], ['USER_LOGIN'])).toContain(
      'Event types apply to attribute mappings; add one to keep this selection.',
    );
  });
});

describe('supportsAttributeMappings', () => {
  it.each([['reporter-am-kafka'], ['reporter-am-tcp'], ['reporter-am-file']])('is true for %s', (type) => {
    expect(supportsAttributeMappings({ type })).toBe(true);
  });

  it.each([['mongodb'], ['reporter-am-jdbc']])('is false for %s, which drops the exported attributes', (type) => {
    expect(supportsAttributeMappings({ type })).toBe(false);
  });

  it('is false for a system reporter, which the API refuses mappings on', () => {
    expect(supportsAttributeMappings({ type: 'reporter-am-file', system: true })).toBe(false);
  });
});
