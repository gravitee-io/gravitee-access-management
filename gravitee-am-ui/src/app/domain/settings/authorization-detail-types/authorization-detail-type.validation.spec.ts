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
  expiresInSeconds,
  expiryError,
  formatSchema,
  saveErrorMessage,
  schemaExample,
  SCHEMA_EDITOR_OPTIONS,
  schemaError,
  SCHEMA_MAX_LENGTH,
} from './authorization-detail-type.validation';

describe('authorization detail type validation', () => {
  describe('schemaExample', () => {
    it('should constrain the type property to the type, escaped', () => {
      const example = JSON.parse(schemaExample('a"b'));

      expect(example.type).toBe('object');
      expect(example.required).toContain('type');
      expect(example.properties.type.const).toBe('a"b');
    });
  });

  describe('formatSchema', () => {
    it('should indent the schema by two spaces', () => {
      expect(formatSchema('{"type":"object","required":["type"]}')).toBe('{\n  "type": "object",\n  "required": [\n    "type"\n  ]\n}');
    });
  });

  describe('saveErrorMessage', () => {
    it.each([
      [{ status: 400, error: { message: 'Schema root must declare a type' } }, 'Schema root must declare a type'],
      [{ status: 403, error: { message: 'ignored' } }, 'Access denied'],
      [{ status: 500, error: null }, 'Server error'],
    ])('should describe %j', (error, message) => {
      expect(saveErrorMessage(error)).toBe(message);
    });
  });

  describe('schema editor lint', () => {
    const lint = (text: string) => {
      const editor = {
        posFromIndex: (index: number) => {
          const before = text.slice(0, index).split('\n');
          return { line: before.length - 1, ch: before[before.length - 1].length };
        },
      } as any;
      return SCHEMA_EDITOR_OPTIONS.lint.getAnnotations(text, undefined, editor);
    };

    it.each(['', '  ', '{"type":"object"}'])('should not mark %p', (text) => {
      expect(lint(text)).toEqual([]);
    });

    it.each([
      ['a missing value', '{"type":}', 'Value expected', { line: 0, ch: 8 }, { line: 0, ch: 9 }],
      ['a misspelled literal', '{"a":tru}', 'Invalid symbol', { line: 0, ch: 5 }, { line: 0, ch: 8 }],
      ['a trailing comma in an array', '[1,]', 'Value expected', { line: 0, ch: 3 }, { line: 0, ch: 4 }],
      ['an error before the last line', '{\n"a":tru,\n"b":1\n}\n', 'Invalid symbol', { line: 1, ch: 4 }, { line: 1, ch: 7 }],
      ['truncated input', '{"a":\n', 'Value expected', { line: 0, ch: 4 }, { line: 0, ch: 5 }],
      ['a comment', '{} // note', 'Invalid comment token', { line: 0, ch: 3 }, { line: 0, ch: 10 }],
    ])('should locate %s', (_case, text, message, from, to) => {
      expect(lint(text)).toEqual([{ severity: 'error', message, from, to }]);
    });

    it('should mark the end of the text when only JSON.parse finds an error', () => {
      const parse = jest.spyOn(JSON, 'parse').mockImplementationOnce(() => {
        throw new Error('rejected');
      });

      expect(lint('{}\n')).toEqual([{ severity: 'error', message: 'Invalid JSON', from: { line: 0, ch: 1 }, to: { line: 0, ch: 2 } }]);

      parse.mockRestore();
    });
  });

  describe('schemaError', () => {
    it.each([undefined, null, '', '   '])('should accept an absent schema: %p', (schema) => {
      expect(schemaError(schema)).toBeNull();
    });

    it('should accept valid JSON', () => {
      expect(schemaError('{"type": "object"}')).toBeNull();
    });

    it('should reject invalid JSON', () => {
      expect(schemaError('{"type": ')).toEqual('Schema must be valid JSON');
    });

    it('should reject a schema over the length limit', () => {
      expect(schemaError(' '.repeat(SCHEMA_MAX_LENGTH) + '{}')).toEqual(`Schema must be at most ${SCHEMA_MAX_LENGTH} characters`);
    });
  });

  describe('expiresInSeconds', () => {
    it('should convert the duration to seconds', () => {
      expect(expiresInSeconds(2, 'hours')).toEqual(7200);
    });

    it('should be undefined without both parts', () => {
      expect(expiresInSeconds(null, 'hours')).toBeUndefined();
      expect(expiresInSeconds(2, null)).toBeUndefined();
    });
  });

  describe('expiryError', () => {
    it('should accept no duration, even with a unit', () => {
      expect(expiryError(null, null)).toBeNull();
      expect(expiryError(null, 'hours')).toBeNull();
    });

    it('should require a unit with a duration', () => {
      expect(expiryError(1, null)).toEqual('Select a unit for the duration');
    });

    it('should accept 1 second', () => {
      expect(expiryError(1, 'seconds')).toBeNull();
    });

    it.each([0, -1, 0.5])('should reject less than 1 second: %p', (duration) => {
      expect(expiryError(duration, 'seconds')).toEqual('Duration must be at least 1 second');
    });

    it('should reject a fraction of a second', () => {
      expect(expiryError(1.5, 'seconds')).toEqual('Duration must be a whole number of seconds');
    });

    it('should reject a duration that exceeds an int32 of seconds', () => {
      expect(expiryError(100, 'years')).toEqual('Duration is too long');
    });
  });
});
