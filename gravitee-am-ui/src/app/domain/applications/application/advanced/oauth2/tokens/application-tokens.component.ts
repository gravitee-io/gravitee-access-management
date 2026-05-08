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
import { Component, EventEmitter, OnInit, Output, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NgForm } from '@angular/forms';
import { find, findIndex, remove } from 'lodash';

import { ApplicationService } from '../../../../../../services/application.service';
import { SnackbarService } from '../../../../../../services/snackbar.service';
import { AuthService } from '../../../../../../services/auth.service';

@Component({
  selector: 'application-tokens',
  templateUrl: './application-tokens.component.html',
  styleUrls: ['./application-tokens.component.scss'],
})
export class ApplicationTokensComponent implements OnInit {
  @ViewChild('claimsTable') table: any;
  @ViewChild('userinfoClaimsTable') userinfoTable: any;
  private domainId: string;
  formChanged: boolean;
  application: any;
  applicationOauthSettings: any = {};
  readonly = false;
  editing: any = {};

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    public dialog: MatDialog,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.applicationOauthSettings = this.application.settings == null ? {} : this.application.settings.oauth || {};
    this.applicationOauthSettings.tokenCustomClaims = this.applicationOauthSettings.tokenCustomClaims || [];
    this.applicationOauthSettings.userinfoCustomClaims = this.applicationOauthSettings.userinfoCustomClaims || [];
    this.migrateLegacyUserProfileClaims();
    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
    this.initCustomClaims();
    this.initUserInfoCustomClaims();
  }

  patch() {
    this.cleanCustomClaims();
    this.cleanUserInfoCustomClaims();
    const oauthSettings: any = {};
    oauthSettings.tokenCustomClaims = this.applicationOauthSettings.tokenCustomClaims;
    oauthSettings.userinfoCustomClaims = this.applicationOauthSettings.userinfoCustomClaims;
    oauthSettings.accessTokenValiditySeconds = this.applicationOauthSettings.accessTokenValiditySeconds;
    oauthSettings.refreshTokenValiditySeconds = this.applicationOauthSettings.refreshTokenValiditySeconds;
    oauthSettings.idTokenValiditySeconds = this.applicationOauthSettings.idTokenValiditySeconds;
    this.applicationService.patch(this.domainId, this.application.id, { settings: { oauth: oauthSettings } }).subscribe(() => {
      this.clearLegacyUserProfileStorage();
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      this.formChanged = false;
      this.initCustomClaims();
      this.initUserInfoCustomClaims();
    });
  }

  addClaim(claim) {
    if (claim) {
      if (!this.claimExits(claim)) {
        claim.id = Math.random().toString(36).substring(7);
        this.applicationOauthSettings.tokenCustomClaims.push(claim);
        this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
        this.table.groupHeader.collapseAllGroups();
        this.formChanged = true;
      } else {
        this.snackbarService.open('Claim already exists');
      }
    }
  }

  claimExits(claim): boolean {
    return find(this.applicationOauthSettings.tokenCustomClaims, function (el) {
      return el.tokenType === claim.tokenType && el.claimName === claim.claimName;
    });
  }

  updateClaim(tokenType, event, cell, rowIndex) {
    const claim = event.target.value;
    if (claim) {
      this.editing[rowIndex + '-' + cell] = false;
      const index = findIndex(this.applicationOauthSettings.tokenCustomClaims, { id: rowIndex });
      this.applicationOauthSettings.tokenCustomClaims[index][cell] = claim;
      this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
      this.formChanged = true;
    }
  }

  deleteClaim(tokenType, key, event) {
    event.preventDefault();
    remove(this.applicationOauthSettings.tokenCustomClaims, function (el: any) {
      return el.tokenType === tokenType && el.claimName === key;
    });
    this.applicationOauthSettings.tokenCustomClaims = [...this.applicationOauthSettings.tokenCustomClaims];
    this.formChanged = true;
  }

  claimsIsEmpty() {
    return this.applicationOauthSettings.tokenCustomClaims.length === 0;
  }

  toggleExpandGroup(group) {
    this.table.groupHeader.toggleExpandGroup(group);
  }

  addUserInfoClaim(claim) {
    if (claim) {
      if (!this.userInfoClaimExists(claim)) {
        claim.id = Math.random().toString(36).substring(7);
        this.applicationOauthSettings.userinfoCustomClaims.push(claim);
        this.applicationOauthSettings.userinfoCustomClaims = [...this.applicationOauthSettings.userinfoCustomClaims];
        this.formChanged = true;
      } else {
        this.snackbarService.open('Claim already exists');
      }
    }
  }

  userInfoClaimExists(claim): boolean {
    return find(this.applicationOauthSettings.userinfoCustomClaims, function (el) {
      return el.claimName === claim.claimName;
    });
  }

  updateUserInfoClaim(event, cell, rowIndex) {
    const claim = event.target.value;
    if (claim) {
      this.editing[rowIndex + '-userinfo-' + cell] = false;
      const index = findIndex(this.applicationOauthSettings.userinfoCustomClaims, { id: rowIndex });
      this.applicationOauthSettings.userinfoCustomClaims[index][cell] = claim;
      this.applicationOauthSettings.userinfoCustomClaims = [...this.applicationOauthSettings.userinfoCustomClaims];
      this.formChanged = true;
    }
  }

  deleteUserInfoClaim(key, event) {
    event.preventDefault();
    remove(this.applicationOauthSettings.userinfoCustomClaims, function (el: any) {
      return el.claimName === key;
    });
    this.applicationOauthSettings.userinfoCustomClaims = [...this.applicationOauthSettings.userinfoCustomClaims];
    this.formChanged = true;
  }

  userInfoClaimsIsEmpty() {
    return this.applicationOauthSettings.userinfoCustomClaims.length === 0;
  }

  openDialog(event) {
    event.preventDefault();
    this.dialog.open(ClaimsInfoDialogComponent, {});
  }

  modelChanged() {
    this.formChanged = true;
  }

  private cleanCustomClaims() {
    if (this.applicationOauthSettings.tokenCustomClaims.length > 0) {
      this.applicationOauthSettings.tokenCustomClaims.forEach((claim) => {
        delete claim.id;
      });
    }
  }

  private initCustomClaims() {
    if (this.applicationOauthSettings.tokenCustomClaims.length > 0) {
      this.applicationOauthSettings.tokenCustomClaims.forEach((claim) => {
        claim.id = Math.random().toString(36).substring(7);
      });
    }
  }

  private cleanUserInfoCustomClaims() {
    if (this.applicationOauthSettings.userinfoCustomClaims.length > 0) {
      this.applicationOauthSettings.userinfoCustomClaims.forEach((claim) => {
        delete claim.id;
      });
    }
  }

  private initUserInfoCustomClaims() {
    if (this.applicationOauthSettings.userinfoCustomClaims.length > 0) {
      this.applicationOauthSettings.userinfoCustomClaims.forEach((claim) => {
        claim.id = Math.random().toString(36).substring(7);
      });
    }
  }

  private legacyUserProfileStorageKey(): string {
    return `am.tokenCustomClaims.userProfile:${this.domainId}:${this.application.id}`;
  }

  private migrateLegacyUserProfileClaims(): void {
    let legacy: any[];
    try {
      const raw = window.localStorage.getItem(this.legacyUserProfileStorageKey());
      legacy = raw ? JSON.parse(raw) : [];
    } catch {
      return;
    }
    if (!legacy || legacy.length === 0) {
      return;
    }
    const existingNames = new Set(this.applicationOauthSettings.userinfoCustomClaims.map((c: any) => c.claimName));
    const migrated = legacy
      .filter((c: any) => c && c.claimName && !existingNames.has(c.claimName))
      .map((c: any) => ({ claimName: c.claimName, claimValue: c.claimValue }));
    if (migrated.length > 0) {
      this.applicationOauthSettings.userinfoCustomClaims = [...this.applicationOauthSettings.userinfoCustomClaims, ...migrated];
      this.formChanged = true;
    }
  }

  private clearLegacyUserProfileStorage(): void {
    try {
      window.localStorage.removeItem(this.legacyUserProfileStorageKey());
    } catch {
      /* ignore */
    }
  }
}

@Component({
  selector: 'app-create-claim',
  templateUrl: './claims/add-claim.component.html',
})
export class CreateClaimComponent {
  claim: any = {};
  tokenTypes: any[] = ['id_token', 'access_token'];
  @Output() addClaimChange = new EventEmitter();
  @ViewChild('claimForm', { static: true }) form: NgForm;

  addClaim() {
    this.addClaimChange.emit(this.claim);
    this.claim = {};
    this.form.reset(this.claim);
  }
}

@Component({
  selector: 'app-create-userinfo-claim',
  templateUrl: './claims/add-userinfo-claim.component.html',
})
export class CreateUserinfoClaimComponent {
  claim: any = {};
  @Output() addClaimChange = new EventEmitter();
  @ViewChild('claimForm', { static: true }) form: NgForm;

  addClaim() {
    this.addClaimChange.emit(this.claim);
    this.claim = {};
    this.form.reset(this.claim);
  }
}

@Component({
  selector: 'claims-info-dialog',
  templateUrl: './dialog/claims-info.component.html',
})
export class ClaimsInfoDialogComponent {
  constructor(public dialogRef: MatDialogRef<ClaimsInfoDialogComponent>) {}
}
