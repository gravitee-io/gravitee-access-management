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

import { AmFeature, stringFeature } from './gio-license-data';

describe('GIO license features', () => {
  it('should convert string to Feature', () => {
    expect(stringFeature('am-idp-saml2')).toEqual(AmFeature.AM_IDP_SAML2);
  });

  it('should throw error with unknown Feature string', () => {
    expect(() => stringFeature('unknown feature')).toThrow();
  });
});
