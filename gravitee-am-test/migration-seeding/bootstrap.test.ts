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

import { describe, expect, it } from '@jest/globals';
import { toMinorVersion } from './bootstrap';

describe('migration seed bootstrap', () => {
  it('normalizes tags to major minor versions', () => {
    expect(toMinorVersion('4.10.0')).toBe('4.10');
    expect(toMinorVersion('4.11.0-alpha.3')).toBe('4.11');
    expect(toMinorVersion('4.12')).toBe('4.12');
  });
});
