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
import { ActivatedRoute, Router } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';
import { find } from 'lodash';

import { SnackbarService } from '../../../../../../services/snackbar.service';
import { DialogService } from '../../../../../../services/dialog.service';
import { UserService } from '../../../../../../services/user.service';
import { AuthService } from '../../../../../../services/auth.service';

@Component({
  selector: 'app-user-application',
  templateUrl: './application.component.html',
  styleUrls: ['./application.component.scss'],
  standalone: false,
})
export class UserApplicationComponent implements OnInit {
  private domainId: string;
  private userId: string;
  application: any;
  clientId: string;
  consents: any[];
  canRevoke: boolean;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private userService: UserService,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.application = this.route.snapshot.data['application'];
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.userId = this.route.snapshot.params['userId'];
    this.consents = this.route.snapshot.data['consents'];
    this.clientId = this.route.snapshot.queryParamMap.get('clientId');
    this.canRevoke = this.authService.hasPermissions(['domain_user_update']);
  }

  revokeApplication(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Revoke access', 'Are you sure you want to revoke application access ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.revokeConsents(this.domainId, this.userId, this.clientId)),
        tap(() => {
          this.snackbarService.open('Access for application ' + this.application.name + ' revoked');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  revokeConsent(event, consent) {
    event.preventDefault();
    this.dialogService
      .confirm('Revoke access', 'Are you sure you want to revoke this permission ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.revokeConsent(this.domainId, this.userId, consent.id)),
        tap(() => {
          this.snackbarService.open('Permission ' + consent.scopeEntity.name + ' revoked');
          if (this.consents.length === 1) {
            this.router.navigate(['..'], { relativeTo: this.route });
          } else {
            this.loadConsents();
          }
        }),
      )
      .subscribe();
  }

  loadConsents() {
    this.userService.consents(this.domainId, this.userId, null).subscribe((consents) => {
      this.consents = consents.filter((consent) => consent.clientId === this.clientId);
      this.consents = [...this.consents];
    });
  }

  canRevokeAccess() {
    return find(this.consents, { status: 'approved' });
  }

  getRowClass(row) {
    return {
      'row-disabled': row.status !== 'approved',
    };
  }
}
