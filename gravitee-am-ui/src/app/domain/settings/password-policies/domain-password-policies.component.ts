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
import { filter, switchMap, tap } from 'rxjs/operators';

import { PasswordPolicyService } from '../../../services/password-policy.service';
import { AuthService } from '../../../services/auth.service';
import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';

import { PasswordPolicy } from './domain-password-policies.model';

@Component({
  selector: 'domain-password-policies',
  templateUrl: './domain-password-policies.component.html',
  styleUrls: ['./domain-password-policies.component.scss'],
  standalone: false,
})
export class PasswordPoliciesComponent implements OnInit {
  domainId: string;
  canDelete = false;
  rows: PasswordPolicy[] = [];

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private passwordPolicyService: PasswordPolicyService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain'].id;
    this.loadPasswordPolicies();
    this.canDelete = this.authService.hasPermissions(['domain_settings_update']);
  }

  isEmpty(): boolean {
    return this.rows.length === 0;
  }

  private loadPasswordPolicies(): void {
    this.passwordPolicyService.list(this.domainId).subscribe((policies) => {
      this.rows = policies || [];
    });
  }

  protected getTooltipText(id: string): string {
    const idpsNames = this.rows.find((pp) => pp.id === id).idpsNames;
    if (idpsNames === undefined || idpsNames.length === 0) {
      return null;
    }
    return idpsNames.join('\n');
  }

  protected selectDefault(id: string): void {
    this.passwordPolicyService.setDefaultPolicy(this.domainId, id).subscribe({
      complete: () => {
        this.rows.forEach((i) => {
          i.isDefault = i.id === id;
        });
        this.rows = [...this.rows];
        this.snackbarService.open('Updated default Password policy');
      },
      error: () => this.snackbarService.open("Couldn't set default Password policy"),
    });
  }

  deletePolicy(event: Event, policyId: string): void {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Password Policy', 'Are you sure you want to delete this password policy?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.passwordPolicyService.delete(this.domainId, policyId)),
        tap(() => {
          this.snackbarService.open('Policy ' + policyId + ' deleted');
          this.loadPasswordPolicies();
        }),
      )
      .subscribe();
  }
}
