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
import {Component, OnInit} from '@angular/core';
import {MatDialog, MatDialogRef} from "@angular/material/dialog";
import {ActivatedRoute, Router} from '@angular/router';
import {ApplicationService} from '../../../../../services/application.service';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {FactorService} from '../../../../../services/factor.service';
import {AuthService} from '../../../../../services/auth.service';

@Component({
  selector: 'app-application-factors',
  templateUrl: './factors.component.html',
  styleUrls: ['./factors.component.scss']
})
export class ApplicationFactorsComponent implements OnInit {
  private domainId: string;

  private factorTypes: any = {
    'TOTP' : 'OTP',
    'SMS' : 'SMS',
    'EMAIL' : 'EMAIL'
  };

  private factorIcons: any = {
    'TOTP' : 'mobile_friendly',
    'SMS' : 'sms',
    'EMAIL' : 'email'
  };

  application: any;
  formChanged = false;
  factors: any[];
  editMode: boolean;
  mfaSelectionRule: string;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private applicationService: ApplicationService,
              private factorService: FactorService,
              private authService: AuthService,
              private snackbarService: SnackbarService,
              public dialog: MatDialog) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    const applicationAdvancedSettings = this.application.settings == null ? {} : this.application.settings.advanced || {};
    this.mfaSelectionRule = applicationAdvancedSettings.mfaSelectionRule;
    this.editMode = this.authService.hasPermissions(['application_settings_update']);
    this.factorService.findByDomain(this.domainId).subscribe(response => this.factors = [...response]);
  }

  patch(): void {
    const data: any = {};
    data.factors = this.application.factors;
    if (this.mfaSelectionRule) {
      data.settings = {};
      data.settings.advanced = { 'mfaSelectionRule': this.mfaSelectionRule };
    }
    this.applicationService.patch(this.domainId, this.application.id, data).subscribe(data => {
      this.application = data;
      this.formChanged = false;
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], { relativeTo: this.route, queryParams: { 'reload': true }});
    });
  }

  selectFactor(event, factorId) {
    if (event.checked) {
      this.application.factors.push(factorId);
    } else {
      this.application.factors.splice(this.application.factors.indexOf(factorId), 1);
    }
    this.formChanged = true;
  }

  isFactorSelected(factorId) {
    return this.application.factors !== undefined && this.application.factors.includes(factorId);
  }

  hasFactors() {
    return this.factors && this.factors.length > 0;
  }

  getFactorTypeIcon(type) {
    if (this.factorIcons[type]) {
      return this.factorIcons[type];
    }
    return 'donut_large';
  }

  displayFactorType(type) {
    if (this.factorTypes[type]) {
      return this.factorTypes[type];
    }
    return 'Custom';
  }

  openDialog(event) {
    event.preventDefault();
    this.dialog.open(MfaStepUpDialog, { width : '700px' });
  }
}

@Component({
  selector: 'mfa-step-up-dialog',
  templateUrl: './dialog/mfa-step-up-info.component.html',
})
export class MfaStepUpDialog {
  constructor(public dialogRef: MatDialogRef<MfaStepUpDialog>) {}
}
