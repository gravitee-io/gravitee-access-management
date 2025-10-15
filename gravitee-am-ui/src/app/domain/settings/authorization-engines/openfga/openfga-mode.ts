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
import * as CodeMirror from 'codemirror';

CodeMirror.defineMode('openfga', function () {
  const keywords = ['model', 'schema', 'type', 'relations', 'define', 'from', 'or', 'and', 'but', 'not', 'self', 'condition'];
  const operators = [
    '#',
    ':',
    '[',
    ']',
    ',',
    '=',
    '+',
    '{',
    '}',
    '(',
    ')',
    '?',
    '!',
    '*',
    '.',
    '@',
    '&',
    '^',
    '%',
    '$',
    '|',
    '~',
    '<',
    '>',
    '/',
  ];

  return {
    token: function (stream: any) {
      if (stream.eatSpace()) {
        return null;
      }

      if (stream.match('//')) {
        stream.skipToEnd();
        return 'openfga-comment';
      }

      if (stream.match(/^\d+\.\d+/)) {
        return 'openfga-type';
      }

      for (const keyword of keywords) {
        if (stream.match(keyword)) {
          return 'openfga-keyword';
        }
      }

      if (stream.match(/^[a-zA-Z_][a-zA-Z0-9_]*/)) {
        return 'openfga-type';
      }

      for (const op of operators) {
        if (stream.eat(op)) {
          return 'openfga-operator';
        }
      }

      stream.next();
      return null;
    },
  };
});

CodeMirror.defineMIME('text/x-openfga', 'openfga');
