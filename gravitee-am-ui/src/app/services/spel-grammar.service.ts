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
import { Injectable } from '@angular/core';

import { OrganizationService } from './organization.service';

@Injectable({
  providedIn: 'root',
})
export class SpelGrammarService {
  spelGrammar: any;

  constructor(private organizationService: OrganizationService) {}

  init(): void {
    this.organizationService
      .spelGrammar()
      .toPromise()
      .then((response) => {
        this.spelGrammar = response;
      });
  }
  getGrammar() {
    if (this.spelGrammar != null) {
      return Promise.resolve(this.spelGrammar);
    }

    return this.organizationService
      .spelGrammar()
      .toPromise()
      .then((response) => {
        this.spelGrammar = response;
        return this.spelGrammar;
      });
  }
}
