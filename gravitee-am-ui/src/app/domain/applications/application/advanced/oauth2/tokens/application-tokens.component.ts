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
  standalone: false,
})
export class ApplicationTokensComponent implements OnInit {
  @ViewChild('claimsTable') table: any;
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
    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
    this.initCustomClaims();
  }

  patch() {
    const oauthSettings: any = {};
    oauthSettings.tokenCustomClaims = this.applicationOauthSettings.tokenCustomClaims;
    oauthSettings.accessTokenValiditySeconds = this.applicationOauthSettings.accessTokenValiditySeconds;
    oauthSettings.refreshTokenValiditySeconds = this.applicationOauthSettings.refreshTokenValiditySeconds;
    oauthSettings.idTokenValiditySeconds = this.applicationOauthSettings.idTokenValiditySeconds;
    this.applicationService.patch(this.domainId, this.application.id, { settings: { oauth: oauthSettings } }).subscribe(() => {
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { reload: true } });
      this.formChanged = false;
      this.initCustomClaims();
    });
  }

  private initCustomClaims() {
    if (this.applicationOauthSettings.tokenCustomClaims && this.applicationOauthSettings.tokenCustomClaims.length > 0) {
      this.applicationOauthSettings.tokenCustomClaims.forEach((claim) => {
        claim.id = Math.random().toString(36).substring(7);
      });
    }
  }

  updateSettings(settings: any) {
    this.applicationOauthSettings = settings;
    this.formChanged = true;
  }

  onFormChanged(changed: boolean) {
    this.formChanged = changed;
  }
}

