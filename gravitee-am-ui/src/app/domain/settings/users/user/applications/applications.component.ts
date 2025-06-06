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
import { find, groupBy, sortBy, uniqBy } from 'lodash';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { UserService } from '../../../../../services/user.service';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'app-user-applications',
  templateUrl: './applications.component.html',
  styleUrls: ['./applications.component.scss'],
  standalone: false,
})
export class UserApplicationsComponent implements OnInit {
  private domainId: string;
  private user: any;
  private consents: any[];
  private appConsentsGrouped: any = {};
  appConsents: any[];
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
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.user = this.route.snapshot.data['user'];
    this.consents = sortBy(this.route.snapshot.data['consents'], 'updatedAt').reverse();
    this.appConsentsGrouped = groupBy(this.consents, 'clientId');
    this.appConsents = uniqBy(this.consents, 'clientId');
    this.canRevoke = this.authService.hasPermissions(['domain_user_update']);
  }

  get isEmpty() {
    return !this.consents || this.consents.length === 0;
  }

  revoke(event, consent) {
    event.preventDefault();
    this.dialogService
      .confirm('Revoke access', 'Are you sure you want to revoke authorization ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.revokeConsents(this.domainId, this.user.id, consent.clientId)),
        tap(() => {
          this.snackbarService.open('Access for application ' + consent.clientEntity.name + ' revoked');
          this.loadConsents();
        }),
      )
      .subscribe();
  }

  revokeAll(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Revoke access', 'Are you sure you want to revoke access to all these applications ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.revokeConsents(this.domainId, this.user.id, null)),
        tap(() => {
          this.snackbarService.open('Access revoked');
          this.loadConsents();
        }),
      )
      .subscribe();
  }

  loadConsents() {
    this.userService.consents(this.domainId, this.user.id, null).subscribe((consents) => {
      this.consents = consents;
      this.appConsents = uniqBy(this.consents, 'clientId');
    });
  }

  rowClass = (row) => {
    return {
      'row-disabled': !this.canRevokeAccessForClient(row.clientId),
    };
  };

  canRevokeAccessForClient(clientId) {
    return find(this.appConsentsGrouped[clientId], { status: 'approved' });
  }

  canRevokeAllAccess() {
    return find(this.consents, { status: 'approved' });
  }
}
