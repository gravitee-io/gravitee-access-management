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
import { ActivatedRoute } from '@angular/router';

import { PasswordPolicyService } from '../../../services/password-policy.service';
import { SnackbarService } from '../../../services/snackbar.service';

import { PasswordPolicy } from './domain-password-policies.model';

@Component({
  selector: 'domain-password-policies',
  templateUrl: './domain-password-policies.component.html',
  styleUrls: ['./domain-password-policies.component.scss'],
})
export class PasswordPoliciesComponent implements OnInit {
  domain: any = {};

  constructor(
    private route: ActivatedRoute,
    private passwordPolicyService: PasswordPolicyService,
    private snackbarService: SnackbarService,
  ) {}

  rows: PasswordPolicy[] = [];

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.loadPasswordPolicies();
  }

  isEmpty(): boolean {
    return this.rows.length === 0;
  }

  private loadPasswordPolicies() {
    this.passwordPolicyService.list(this.domain.id).subscribe((policies) => {
      this.rows = policies;
    });
  }

  protected getTooltipText(id: string): string {
    const idpsNames = this.rows.find((pp) => pp.id === id).idpsNames;
    if (idpsNames === undefined || idpsNames.length === 0) {
      return null;
    }
    return 'Used in following Identity Providers: ' + idpsNames.join(', ');
  }

  protected selectDefault(id: string): void {
    this.passwordPolicyService.setDefaultPolicy(this.domain.id, id).subscribe({
      complete: () => this.snackbarService.open('Updated default Password policy'),
      error: () => this.snackbarService.open("Couldn't set default Password policy"),
    });
  }
}
