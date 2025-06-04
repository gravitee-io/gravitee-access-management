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
import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { UserService } from '../../../../../services/user.service';
import { AuthService } from '../../../../../services/auth.service';

@Component({
  selector: 'app-user-identities',
  templateUrl: './identities.component.html',
  styleUrls: ['./identities.component.scss'],
  standalone: false,
})
export class UserIdentitiesComponent implements OnInit {
  private domainId: string;
  private user: any;
  identities: any[];
  canRevoke: boolean;
  config: any = { lineWrapping: true, lineNumbers: true, readOnly: true, mode: 'application/json' };
  @ViewChild('identitiesTable', { static: false }) table: any;

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
    this.identities = this.route.snapshot.data['identities'];
    this.canRevoke = this.authService.hasPermissions(['domain_user_update']);
  }

  identityDetails(row) {
    return JSON.stringify(row.additionalInformation, null, '  ');
  }

  remove(event, identity) {
    event.preventDefault();
    this.dialogService
      .confirm('Unlink identity', 'Are you sure you want to unlink this identity ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.removeIdentity(this.domainId, this.user.id, identity.userId)),
        tap(() => {
          this.snackbarService.open('Identity ' + identity.providerName + ' unlinked');
          this.loadIdentities();
        }),
      )
      .subscribe();
  }

  toggleExpandRow(row) {
    this.table.rowDetail.toggleExpandRow(row);
  }

  get isEmpty() {
    return !this.identities || this.identities.length === 0;
  }

  private loadIdentities() {
    this.userService.identities(this.domainId, this.user.id).subscribe((identities) => {
      this.identities = identities;
    });
  }
}
