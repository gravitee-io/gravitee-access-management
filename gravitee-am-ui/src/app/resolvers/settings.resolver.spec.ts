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
import { TestBed, inject } from '@angular/core/testing';

import { SettingsResolver } from './settings.resolver';

describe('SettingsResolver', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SettingsResolver],
    });
  });

  it('should ...', inject([SettingsResolver], (service: SettingsResolver) => {
    expect(service).toBeTruthy();
  }));
});
