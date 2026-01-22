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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { find, findIndex, remove } from 'lodash';

import { SnackbarService } from '../../../services/snackbar.service';

import { TimeConverterService } from '../../../services/time-converter.service';
import { NgForm } from '@angular/forms';

@Component({
  selector: 'app-tokens-settings',
  templateUrl: './tokens.component.html',
  styleUrls: ['./tokens.component.scss'],
  standalone: false,
})
export class TokensComponent implements OnInit {
  @Input() oauthSettings: any;
  @Input() readonly = false;
  @Input() context: 'Application' | 'McpServer' = 'Application';

  @Output() settingsChange = new EventEmitter<any>();
  @Output() formChanged = new EventEmitter<boolean>();

  @ViewChild('claimsTable') table: any;
  editing: any = {};

  constructor(
    public dialog: MatDialog,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit() {
    this.oauthSettings = this.oauthSettings || {};
    this.oauthSettings.tokenCustomClaims = this.oauthSettings.tokenCustomClaims || [];
    this.initCustomClaims();
  }

  addClaim(claim) {
    if (claim) {
      if (!this.claimExits(claim)) {
        claim.id = Math.random().toString(36).substring(7);
        this.oauthSettings.tokenCustomClaims.push(claim);
        this.oauthSettings.tokenCustomClaims = [...this.oauthSettings.tokenCustomClaims];
        this.table.groupHeader.collapseAllGroups();
        this.modelChanged();
      } else {
        this.snackbarService.open('Claim already exists');
      }
    }
  }

  claimExits(claim): boolean {
    return find(this.oauthSettings.tokenCustomClaims, function (el) {
      return el.tokenType === claim.tokenType && el.claimName === claim.claimName;
    });
  }

  updateClaim(tokenType, event, cell, rowIndex) {
    const claim = event.target.value;
    if (claim) {
      this.editing[rowIndex + '-' + cell] = false;
      const index = findIndex(this.oauthSettings.tokenCustomClaims, { id: rowIndex });
      this.oauthSettings.tokenCustomClaims[index][cell] = claim;
      this.oauthSettings.tokenCustomClaims = [...this.oauthSettings.tokenCustomClaims];
      this.modelChanged();
    }
  }

  deleteClaim(tokenType, key, event) {
    event.preventDefault();
    remove(this.oauthSettings.tokenCustomClaims, function (el: any) {
      return el.tokenType === tokenType && el.claimName === key;
    });
    this.oauthSettings.tokenCustomClaims = [...this.oauthSettings.tokenCustomClaims];
    this.modelChanged();
  }

  claimsIsEmpty() {
    return this.oauthSettings.tokenCustomClaims.length === 0;
  }

  toggleExpandGroup(group) {
    this.table.groupHeader.toggleExpandGroup(group);
  }

  openDialog(event) {
    event.preventDefault();
    this.dialog.open(ClaimsInfoDialogComponent, {});
  }

  modelChanged() {
    this.formChanged.emit(true);
    // clean claims before emitting
    const settingsToEmit = { ...this.oauthSettings };
    if (settingsToEmit.tokenCustomClaims && settingsToEmit.tokenCustomClaims.length > 0) {
      // We create a deep copy or map to avoid mutating displayed objects with 'id' if possible
      // but here we just emit the settings. Parent should handle cleaning 'id' before save if strictly needed,
      // or we can clean it here on a copy.
      settingsToEmit.tokenCustomClaims = settingsToEmit.tokenCustomClaims.map((c) => {
        const { id, ...rest } = c;
        return rest;
      });
    }
    this.settingsChange.emit(settingsToEmit);
  }

  private initCustomClaims() {
    if (this.oauthSettings.tokenCustomClaims.length > 0) {
      this.oauthSettings.tokenCustomClaims.forEach((claim) => {
        if (!claim.id) {
          claim.id = Math.random().toString(36).substring(7);
        }
      });
    }
  }
}

@Component({
  selector: 'app-create-claim',
  templateUrl: './claims/add-claim.component.html',
  standalone: false,
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
  selector: 'claims-info-dialog',
  templateUrl: './dialog/claims-info.component.html',
  standalone: false,
})
export class ClaimsInfoDialogComponent {
  constructor(public dialogRef: MatDialogRef<ClaimsInfoDialogComponent>) {}
}
