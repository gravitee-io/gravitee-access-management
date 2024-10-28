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
import { Component, OnInit, ViewChild, ViewContainerRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgForm } from '@angular/forms';
import { filter, map, switchMap, tap } from 'rxjs/operators';
import { isObject } from 'lodash';
import { MatDialog } from '@angular/material/dialog';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { DialogService } from '../../../../../services/dialog.service';
import { UserService } from '../../../../../services/user.service';
import { UserClaimComponent } from '../../creation/user-claim.component';
import { AuthService } from '../../../../../services/auth.service';
import { OrganizationService } from '../../../../../services/organization.service';

import {
  AccountTokenCreationDialogComponent,
  AccountTokenCreationDialogData,
  AccountTokenCreationDialogResult,
} from './token/account-token-creation-dialog.component';
import {
  AccountTokenCopyDialogComponent,
  AccountTokenCopyDialogData,
  AccountTokenCopyDialogResult,
} from './token/account-token-copy-dialog.component';
import {
  AccountTokenRevokationDialogComponent,
  AccountTokenRevokationDialogData,
  AccountTokenRevokationDialogResult,
} from './token/account-token-revokation-dialog.component';

@Component({
  selector: 'app-user-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss'],
})
export class UserProfileComponent implements OnInit {
  organizationContext: boolean;
  @ViewChild('passwordForm') passwordForm: any;
  @ViewChild('usernameForm') usernameForm: NgForm;
  @ViewChild('dynamic', { read: ViewContainerRef }) viewContainerRef: ViewContainerRef;
  user: any;
  userClaims: any = {};
  password: any;
  formChanged: boolean = false;
  canEdit: boolean;
  canDelete: boolean;
  accountTokens: any[] = [];
  emailRequired: boolean = true;
  private domainId: string;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
    private userService: UserService,
    private organizationService: OrganizationService,
    private authService: AuthService,
    private readonly matDialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
      this.canEdit = this.authService.hasPermissions(['organization_user_update']);
      this.canDelete = this.authService.hasPermissions(['organization_user_delete']);
    } else {
      this.canEdit = this.authService.hasPermissions(['domain_user_update']);
      this.canDelete = this.authService.hasPermissions(['domain_user_delete']);
      this.userService.isEmailRequired().subscribe((response: boolean) => {
        this.emailRequired = response;
      });
    }
    this.user = this.route.snapshot.data['user'];
    this.organizationService.getAccountTokens(this.route.snapshot.data['user'].id).subscribe((tokens) => {
      this.accountTokens = tokens;
    });
  }

  update() {
    // TODO we should be able to update platform users
    this.user.additionalInformation = this.user.additionalInformation || {};
    Object.keys(this.userClaims).forEach((key) => (this.user.additionalInformation[key] = this.userClaims[key]));
    if (this.user.firstName || this.user.lastName) {
      this.user.displayName = [this.user.firstName, this.user.lastName].filter(Boolean).join(' ');
    } else {
      this.user.displayName = this.user.username;
    }
    this.userService.update(this.domainId, this.user.id, this.user, this.organizationContext).subscribe((data) => {
      this.user = data;
      this.userClaims = {};
      this.viewContainerRef?.clear();
      this.formChanged = false;
      this.snackbarService.open(`${this.isServiceAccount() ? 'Service' : ''} User updated`);
    });
  }

  delete(event: Event): void {
    event.preventDefault();
    this.dialogService
      .confirm(`Delete ${this.getProfileType()}`, `Are you sure you want to delete this ${this.getProfileType().toLowerCase()}?`)
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.delete(this.domainId, this.user.id, this.organizationContext)),
        tap(() => {
          this.snackbarService.open(`${this.getProfileType().toLowerCase()} ${this.user.username} deleted`);
          this.router.navigate(['../..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  resendConfirmationRegistration(event: Event): void {
    event.preventDefault();
    this.dialogService
      .confirm('Send Email', 'Are you sure you want to send the confirmation registration email ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.resendRegistrationConfirmation(this.domainId, this.user.id)),
        tap(() => {
          this.snackbarService.open('Email sent');
        }),
      )
      .subscribe();
  }

  resetPassword(): void {
    this.dialogService
      .confirm('Reset Password', 'Are you sure you want to reset the password ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.resetPassword(this.domainId, this.user.id, this.password, this.organizationContext)),
        tap(() => {
          this.password = null;
          this.passwordForm.reset();
          // reset the errors of all the controls
          for (const name in this.passwordForm.controls) {
            this.passwordForm.controls[name].setErrors(null);
          }
          this.snackbarService.open('Password reset');
        }),
      )
      .subscribe();
  }

  unlock(event: Event): void {
    event.preventDefault();
    this.dialogService
      .confirm('Unlock User', 'Are you sure you want to unlock the user ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.unlock(this.domainId, this.user.id)),
        tap(() => {
          this.unlockUser(this.user);
          this.snackbarService.open('User unlocked');
        }),
      )
      .subscribe();
  }

  isOrganizationUserAction(): boolean {
    return this.organizationContext;
  }

  editMode(): any {
    return this.user.internal;
  }

  isEmptyObject(obj: any): boolean {
    return obj && Object.keys(obj).length === 0;
  }

  enableUser(event: any): void {
    // TODO we should be able to update platform users
    this.dialogService
      .confirm(
        (event.checked ? 'Enable' : 'Disable') + ' User',
        'Are you sure you want to ' + (event.checked ? 'enable' : 'disable') + ' the user ?',
      )
      .pipe(
        filter((res) => {
          if (res === false) {
            event.source.checked = true;
          }
          return res;
        }),
        switchMap(() => this.userService.updateStatus(this.domainId, this.user.id, event.checked)),
        tap(() => {
          this.user.enabled = event.checked;
          this.snackbarService.open('User ' + (event.checked ? 'enabled' : 'disabled'));
        }),
      )
      .subscribe();
  }

  asObject(value: any): boolean {
    return value && isObject(value);
  }

  isUserEnabled(): boolean {
    return this.user.enabled;
  }

  addDynamicComponent(): void {
    const component = this.viewContainerRef.createComponent(UserClaimComponent);

    component.instance.addClaimChange.subscribe((claim) => {
      if (claim.name && claim.value) {
        this.userClaims[claim.name] = claim.value;
        this.formChanged = true;
      }
    });

    component.instance.removeClaimChange.subscribe((claim) => {
      this.viewContainerRef.remove(this.viewContainerRef.indexOf(component.hostView));
      if (claim.name && claim.value) {
        this.formChanged = true;
      }
    });
  }

  removeExistingClaim(claimKey: string, event: Event): void {
    event.preventDefault();
    this.user.additionalInformation = Object.keys(this.user.additionalInformation).reduce((obj, key) => {
      if (key !== claimKey) {
        obj[key] = this.user.additionalInformation[key];
      }
      return obj;
    }, {});
    this.formChanged = true;
  }

  onAppSelectionChanged(event: any): void {
    this.user.client = event.id;
    this.formChanged = true;
  }

  onAppDeleted(): void {
    this.user.client = null;
    this.formChanged = true;
  }

  displayClientName() {
    return this.user.applicationEntity != null ? this.user.applicationEntity.name : '';
  }

  accountLocked(user: any): boolean {
    return !user.accountNonLocked && (user.accountLockedUntil === null || !user.accountLockedUntil || user.accountLockedUntil > new Date());
  }

  updateUsername(): void {
    this.dialogService
      .confirm(`Update ${this.getProfileNameType()}`, `Are you sure you want to update this ${this.getProfileNameType().toLowerCase()}?`)
      .pipe(
        filter((res) => res),
        switchMap(() => this.userService.updateUsername(this.domainId, this.user.id, this.organizationContext, this.user.username)),
        tap(() => {
          this.usernameForm.resetForm({ username: this.user.username });
          this.unlockUser(this.user);
          this.snackbarService.open(`${this.getProfileNameType()} updated`);
        }),
      )
      .subscribe((data) => this.usernameForm.resetForm({ username: data.username }));
  }

  createToken(): void {
    this.matDialog
      .open<AccountTokenCreationDialogComponent, AccountTokenCreationDialogData, AccountTokenCreationDialogResult>(
        AccountTokenCreationDialogComponent,
        {
          width: '640px',
          disableClose: true,
          role: 'alertdialog',
          id: 'accountTokenCreateDialog',
        },
      )
      .afterClosed()
      .pipe(
        switchMap((result: AccountTokenCreationDialogResult) => {
          if (result) {
            return this.organizationService.createAccountToken(this.user.id, result.name);
          } else {
            const message = 'Failed to generate account token';
            this.snackbarService.open(message);
            throw new Error(message);
          }
        }),
        tap((data) => {
          this.accountTokens = [...this.accountTokens, data];
          this.snackbarService.open('Account token generated');
          return data;
        }),
        switchMap((data) =>
          this.matDialog
            .open<AccountTokenCopyDialogComponent, AccountTokenCopyDialogData, AccountTokenCopyDialogResult>(
              AccountTokenCopyDialogComponent,
              {
                width: '640px',
                disableClose: true,
                data: {
                  token: data.token,
                  orgId: this.user.referenceId,
                },
                role: 'alertdialog',
                id: 'accountTokenCopyDialog',
              },
            )
            .afterClosed(),
        ),
      )
      .subscribe();
  }

  revokeToken(token: any): void {
    this.matDialog
      .open<AccountTokenRevokationDialogComponent, AccountTokenRevokationDialogData, AccountTokenRevokationDialogResult>(
        AccountTokenRevokationDialogComponent,
        {
          data: token,
          width: '600px',
          role: 'alertdialog',
          id: 'accountTokenRevokeDialog',
        },
      )
      .afterClosed()
      .pipe(
        switchMap((result: AccountTokenRevokationDialogResult) => {
          if (result.tokenId) {
            return this.organizationService.revokeAccountToken(this.user.id, result.tokenId).pipe(map((_) => result.tokenId));
          }
        }),
        tap((revokedTokenId: string) => {
          const idx = this.accountTokens.map((t) => t.tokenId).indexOf(revokedTokenId);
          this.accountTokens.splice(idx, 1);
        }),
      )
      .subscribe();
  }

  private unlockUser(user: any): void {
    user.accountNonLocked = true;
    user.accountLockedAt = null;
    user.accountLockedUntil = null;
  }

  setForceResetPassword(e: any): void {
    this.user.forceResetPassword = e.checked;
    this.formChanged = true;
  }

  isServiceAccount(): boolean {
    return this.user?.serviceAccount ?? false;
  }

  getProfileNameType(): string {
    return this.isServiceAccount() ? 'Service Name' : 'Username';
  }

  getProfileType(): string {
    return this.isServiceAccount() ? 'Service User' : 'User';
  }
}
