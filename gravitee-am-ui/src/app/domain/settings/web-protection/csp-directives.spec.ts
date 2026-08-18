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
  allowsValue,
  analyzeCspDirectives,
  CSP_DIRECTIVE_NAME_PATTERN,
  CSP_DIRECTIVE_VALUE_PATTERN,
  CspDirectiveRow,
  parseCspDirective,
  parseCspDirectives,
  requiresValue,
  serializeCspDirective,
  serializeCspDirectives,
} from './csp-directives';

const rows = (...entries: [string, string][]): CspDirectiveRow[] => entries.map(([name, value]) => ({ name, value }));

describe('csp-directives', () => {
  describe('parsing', () => {
    it('splits a name and value on the first whitespace', () => {
      expect(parseCspDirective("default-src 'self'")).toEqual({ name: 'default-src', value: "'self'" });
    });

    it('keeps a multi-token source list verbatim', () => {
      expect(parseCspDirective("script-src 'self' https://cdn.example.com")).toEqual({
        name: 'script-src',
        value: "'self' https://cdn.example.com",
      });
    });

    it('treats a trailing semicolon as optional', () => {
      expect(parseCspDirective("default-src 'self';")).toEqual(parseCspDirective("default-src 'self'"));
    });

    it('keeps an optional trailing semicolon out of the value', () => {
      const row = parseCspDirective("default-src 'self';");

      expect(row.value).toBe("'self'");
      expect(new RegExp(`^${CSP_DIRECTIVE_VALUE_PATTERN}$`).test(row.value)).toBe(true);
    });

    it('keeps an optional trailing semicolon out of a valueless directive name', () => {
      const row = parseCspDirective('upgrade-insecure-requests;');

      expect(row).toEqual({ name: 'upgrade-insecure-requests', value: '' });
      expect(new RegExp(`^${CSP_DIRECTIVE_NAME_PATTERN}$`).test(row.name)).toBe(true);
    });

    it('parses a directive that takes no value', () => {
      expect(parseCspDirective('upgrade-insecure-requests')).toEqual({ name: 'upgrade-insecure-requests', value: '' });
    });

    it('preserves the casing the operator typed', () => {
      expect(parseCspDirective("Script-Src 'self'").name).toBe('Script-Src');
    });

    it('still produces a row for an entry that does not parse cleanly, so it can be fixed', () => {
      expect(parseCspDirective('this is not valid')).toEqual({ name: 'this', value: 'is not valid' });
    });
  });

  describe('serialising', () => {
    it('renders name and value without a trailing semicolon', () => {
      expect(serializeCspDirective({ name: 'default-src', value: "'self'" })).toBe("default-src 'self'");
    });

    it('renders a valueless directive as the name alone', () => {
      expect(serializeCspDirective({ name: 'upgrade-insecure-requests', value: '' })).toBe('upgrade-insecure-requests');
    });

    it('round-trips a stored list', () => {
      const stored = ["default-src 'self'", "script-src 'self' https://cdn.example.com", 'upgrade-insecure-requests'];

      expect(serializeCspDirectives(parseCspDirectives(stored))).toEqual(stored);
    });

    it('normalizes a trailing semicolon out on round-trip', () => {
      expect(serializeCspDirectives(parseCspDirectives(["default-src 'self';"]))).toEqual(["default-src 'self'"]);
    });
  });

  describe('analysis', () => {
    const analyze = (directives: CspDirectiveRow[], reportOnly = false) => analyzeCspDirectives(directives, reportOnly);

    it('reports nothing for a clean policy', () => {
      const analysis = analyze(rows(['default-src', "'self'"], ['script-src', "'self'"]));

      expect(analysis.formErrors).toEqual([]);
      expect(analysis.hasBlockingIssue).toBe(false);
      expect(analysis.rows.every((row) => !row.duplicate && !row.unknownName)).toBe(true);
    });

    it('flags every duplicate after the first', () => {
      const analysis = analyze(rows(['default-src', "'self'"], ['default-src', "'none'"], ['default-src', "'*'"]));

      expect(analysis.rows.map((row) => row.duplicate)).toEqual([false, true, true]);
      expect(analysis.hasBlockingIssue).toBe(true);
    });

    it('treats names differing only by case as duplicates', () => {
      const analysis = analyze(rows(['script-src', "'self'"], ['Script-Src', "'none'"]));

      expect(analysis.rows.map((row) => row.duplicate)).toEqual([false, true]);
    });

    it('does not flag empty names as duplicates of each other', () => {
      const analysis = analyze(rows(['', ''], ['', '']));

      expect(analysis.rows.map((row) => row.duplicate)).toEqual([false, false]);
    });

    it('flags an unrecognized name without blocking the save', () => {
      const analysis = analyze(rows(['some-future-directive', "'self'"]));

      expect(analysis.rows[0].unknownName).toBe(true);
      expect(analysis.hasBlockingIssue).toBe(false);
    });

    it('does not flag known names, whatever their casing', () => {
      const analysis = analyze(rows(['default-src', "'self'"], ['Script-Src', "'self'"]));

      expect(analysis.rows.map((row) => row.unknownName)).toEqual([false, false]);
    });

    it('does not flag a syntactically invalid name as unrecognized, since the field already errors', () => {
      expect(analyze(rows(['script_src', "'self'"])).rows[0].unknownName).toBe(false);
    });

    it('requires at least one directive', () => {
      const analysis = analyze([]);

      expect(analysis.formErrors[0]).toContain('at least one directive');
      expect(analysis.hasBlockingIssue).toBe(true);
    });

    it('requires a report target in report only mode', () => {
      const analysis = analyze(rows(['default-src', "'self'"]), true);

      expect(analysis.formErrors[0]).toContain('Report only needs');
      expect(analysis.hasBlockingIssue).toBe(true);
    });

    it('accepts report only with a report-uri', () => {
      expect(analyze(rows(['default-src', "'self'"], ['report-uri', '/csp-reports']), true).formErrors).toEqual([]);
    });

    it('accepts report only with a report-to', () => {
      expect(analyze(rows(['default-src', "'self'"], ['report-to', 'csp-endpoint']), true).formErrors).toEqual([]);
    });

    it('ignores a report target that has no value', () => {
      expect(analyze(rows(['default-src', "'self'"], ['report-uri', '']), true).formErrors.length).toBeGreaterThan(0);
    });

    it('notes that report-to alone is not delivered by AM', () => {
      expect(analyze(rows(['report-to', 'csp-endpoint'])).rows[0].reportToNeedsEndpoint).toBe(true);
    });

    it('drops that note once report-uri is configured too', () => {
      const analysis = analyze(rows(['report-to', 'csp-endpoint'], ['report-uri', '/csp-reports']));

      expect(analysis.rows[0].reportToNeedsEndpoint).toBe(false);
    });
  });

  describe('value pattern', () => {
    const valuePattern = new RegExp(`^${CSP_DIRECTIVE_VALUE_PATTERN}$`);

    it('accepts ordinary source lists', () => {
      expect(valuePattern.test("'self' https://cdn.example.com data:")).toBe(true);
    });

    it('accepts a tab', () => {
      expect(valuePattern.test(`'self'${String.fromCharCode(0x09)}https://cdn.example.com`)).toBe(true);
    });

    it('rejects a semicolon', () => {
      expect(valuePattern.test("'self'; script-src 'self'")).toBe(false);
    });

    it('rejects every control character except tab', () => {
      for (let code = 0; code < 0x20; code++) {
        if (code === 0x09) {
          continue;
        }
        expect(valuePattern.test(`'self'${String.fromCharCode(code)}x`)).toBe(false);
      }
      expect(valuePattern.test(`'self'${String.fromCharCode(0x7f)}x`)).toBe(false);
    });

    it('accepts non-ascii characters, which headers permit', () => {
      expect(valuePattern.test('https://café.example.com')).toBe(true);
    });
  });

  describe('value expectations', () => {
    it('allows a value for ordinary directives', () => {
      expect(allowsValue('default-src')).toBe(true);
      expect(requiresValue('default-src')).toBe(true);
    });

    it('blocks a value for directives that take none', () => {
      expect(allowsValue('upgrade-insecure-requests')).toBe(false);
      expect(allowsValue('block-all-mixed-content')).toBe(false);
      expect(requiresValue('upgrade-insecure-requests')).toBe(false);
    });

    it('allows but does not require a value for optional-value directives', () => {
      expect(allowsValue('sandbox')).toBe(true);
      expect(requiresValue('sandbox')).toBe(false);
      expect(allowsValue('trusted-types')).toBe(true);
      expect(requiresValue('trusted-types')).toBe(false);
    });

    it('ignores casing', () => {
      expect(allowsValue('Upgrade-Insecure-Requests')).toBe(false);
    });
  });
});
