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
import { Component, OnInit } from '@angular/core';

import { PasswordPolicy } from './domain-password-policies.model';

@Component({
  selector: 'domain-password-policies',
  templateUrl: './domain-password-policies.component.html',
  styleUrls: ['./domain-password-policies.component.scss'],
})
export class PasswordPoliciesComponent implements OnInit {
  rows: PasswordPolicy[] = [];

  ngOnInit() {
    this.init();
  }

  private init() {
    this.rows = [
      {
        id: '1',
        name: 'aaa',
        idpCount: 0,
        isDefault: false,
      },
      {
        id: '2',
        name: 'bb',
        idpCount: 0,
        isDefault: true,
      },
    ];
  }

  isEmpty(): boolean {
    return this.rows.length === 0;
  }
}
