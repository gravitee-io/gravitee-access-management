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
import { Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { duration } from 'moment';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { UntypedFormControl } from '@angular/forms';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { difference, find, map, remove } from 'lodash';

import { AuthService } from '../../../../../../services/auth.service';
import { SnackbarService } from '../../../../../../services/snackbar.service';
import { ApplicationService } from '../../../../../../services/application.service';
import { TimeConverterService } from '../../../../../../services/time-converter.service';

@Component({
  selector: 'application-scopes',
  templateUrl: './application-scopes.component.html',
  styleUrls: ['./application-scopes.component.scss'],
  standalone: false,
})
export class ApplicationScopesComponent implements OnInit {
  private domainId: string;
  private defaultScopes: string[];
  formChanged: boolean;
  application: any;
  applicationOauthSettings: any = {};
  selectedScopes: any[];
  selectedScopeApprovals: any;
  scopes: any[] = [];
  readonly = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private dialog: MatDialog,
    private timeConverterService: TimeConverterService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = structuredClone(this.route.snapshot.data['application']);
    this.scopes = structuredClone(this.route.snapshot.data['scopes']);
    this.applicationOauthSettings = this.application.settings == null ? {} : this.application.settings.oauth || {};
    this.applicationOauthSettings.scopes = this.applicationOauthSettings.scopes || [];
    this.readonly = !this.authService.hasPermissions(['application_openid_update']);
    this.initScopes();
  }

  initScopes() {
    // Shared component handles the merging logic, but we still need some init if we use it in patch or elsewhere
    // Actually, ScopesComponent handles the display of selected scopes.
  }

  updateSettings(settings: any) {
    this.applicationOauthSettings = settings;
    this.formChanged = true;
  }

  onFormChanged(changed: boolean) {
    this.formChanged = changed;
  }
}

