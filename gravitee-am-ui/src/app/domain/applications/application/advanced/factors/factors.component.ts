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
import moment from "moment";

@Component({
  selector: 'app-application-factors',
  templateUrl: './factors.component.html',
  styleUrls: ['./factors.component.scss']
})
export class ApplicationFactorsComponent implements OnInit {
  private domainId: string;

  private factorTypes: any = {
    'OTP': 'TOTP',
    'SMS': 'SMS',
    'EMAIL': 'EMAIL',
    'CALL': 'CALL',
    'HTTP': 'HTTP',
    'RECOVERY_CODE' : 'Recovery Code',
    'FIDO2' : 'FIDO2'
  };

  private factorIcons: any = {
    'OTP': 'mobile_friendly',
    'SMS': 'sms',
    'EMAIL': 'email',
    'CALL': 'call',
    'HTTP': 'http',
    'RECOVERY_CODE': 'autorenew',
    'FIDO2': 'fingerprint'
  };

  application: any;
  formChanged = false;
  factors: any[];
  editMode: boolean;
  mfaStepUpRule: string;
  adaptiveMfaRule: string;
  rememberDevice: any;
  enrollment: any;
  rememberDeviceTime: any;
  enrollmentTime: any;
  deviceIdentifiers: any[];

  constructor(private route: ActivatedRoute,
              private router: Router,
              private applicationService: ApplicationService,
              private factorService: FactorService,
              private authService: AuthService,
              private snackbarService: SnackbarService,
              public dialog: MatDialog) {
  }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.deviceIdentifiers = this.route.snapshot.data['deviceIdentifiers'] || [];
    const applicationMfaSettings = this.application.settings == null ? {} : this.application.settings.mfa || {};
    this.mfaStepUpRule = applicationMfaSettings.stepUpAuthenticationRule;
    this.adaptiveMfaRule = applicationMfaSettings.adaptiveAuthenticationRule;
    this.rememberDevice = applicationMfaSettings.rememberDevice || {};
    this.enrollment = applicationMfaSettings.enrollment || {};
    this.rememberDeviceTime = {
      'expirationTime': this.getTime(this.rememberDevice.expirationTimeSeconds),
      'expirationTimeUnit': this.getUnitTime(this.rememberDevice.expirationTimeSeconds)
    }
    this.enrollmentTime = {
      'skipTime': this.getTime(this.enrollment.skipTimeSeconds),
      'skipTimeUnit': this.getUnitTime(this.enrollment.skipTimeSeconds)
    }
    this.editMode = this.authService.hasPermissions(['application_settings_update']);
    this.factorService.findByDomain(this.domainId).subscribe(response => this.factors = [...response]);
  }

  patch(): void {
    const data: any = {};
    data.factors = this.application.factors;
    data.settings = {};

    if (this.rememberDevice.active) {
      if (!this.rememberDeviceTime.expirationTime) {
        this.rememberDevice.expirationTimeSeconds = null;
      } else {
        this.rememberDeviceTime.expirationTime = Math.abs(this.rememberDeviceTime.expirationTime);
        this.rememberDevice.expirationTimeSeconds =
          moment.duration(this.rememberDeviceTime.expirationTime, this.rememberDeviceTime.expirationTimeUnit).asSeconds();
      }
    }

    if (!this.enrollmentTime.skipTime) {
      this.enrollment.skipTimeSeconds = null;
    } else {
      this.enrollmentTime.skipTime = Math.abs(this.enrollmentTime.skipTime);
      this.enrollment.skipTimeSeconds =
        moment.duration(this.enrollmentTime.skipTime, this.enrollmentTime.skipTimeUnit).asSeconds();
    }

    data.settings.mfa = {
      'stepUpAuthenticationRule': this.mfaStepUpRule,
      'adaptiveAuthenticationRule': this.adaptiveMfaRule,
      'rememberDevice': this.rememberDevice,
      'enrollment': this.enrollment
    };
    this.applicationService.patch(this.domainId, this.application.id, data).subscribe(data => {
      this.application = data;
      this.formChanged = false;
      this.snackbarService.open('Application updated');
      this.router.navigate(['.'], {relativeTo: this.route, queryParams: {'reload': true}});
    });
  }

  selectFactor(event, factorId) {
    if (event.checked) {
      this.application.factors = this.application.factors || [];
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

  hasSelectedFactors() {
    return this.application.factors && this.application.factors.length > 0;
  }

  getFactorTypeIcon(type) {
    const factorType = type.toUpperCase();
    if (this.factorIcons[factorType]) {
      return this.factorIcons[factorType];
    }
    return 'donut_large';
  }

  displayFactorType(type) {
    const factorType = type.toUpperCase();
    if (this.factorTypes[factorType]) {
      return this.factorTypes[factorType];
    }
    return 'Custom';
  }

  openStepUpDialog($event) {
    $event.preventDefault();
    this.dialog.open(MfaStepUpDialog, {width: '700px'});
  }

  openAMFADialog($event) {
    $event.preventDefault();
    this.dialog.open(AdaptiveMfaDialog, {width: '700px'});
  }

  onExpiresInEvent($event) {
    this.rememberDeviceTime.expirationTime = $event.target.value;
    this.formChanged = true;
  }

  onUnitTimeEvent($event) {
    this.rememberDeviceTime.expirationTimeUnit = $event.value;
    this.formChanged = true;
  }

  displayExpiresIn() {
    return this.rememberDeviceTime.expirationTime;
  }

  displayUnitTime() {
    return this.rememberDeviceTime.expirationTimeUnit;
  }

  displaySkipTime() {
    return this.enrollmentTime.skipTime;
  }

  displaySkipTimeUnit() {
    return this.enrollmentTime.skipTimeUnit;
  }

  onSkipTimeInEvent($event) {
    this.enrollmentTime.skipTime = $event.target.value;
    this.formChanged = true;
  }

  onSkipTimeUnitEvent($event) {
    this.enrollmentTime.skipTimeUnit = $event.value;
    this.formChanged = true;
  }

  private getTime(value) {
    if (value) {
      const humanizeDate = moment.duration(value, 'seconds').humanize().split(' ');
      const humanizeDateValue = (humanizeDate.length === 2)
        ? (humanizeDate[0] === 'a' || humanizeDate[0] === 'an') ? 1 : humanizeDate[0]
        : value;
      return humanizeDateValue;
    }
    return null;
  }

  private getUnitTime(value) {
    if (value) {
      const humanizeDate = moment.duration(value, 'seconds').humanize().split(' ');
      const humanizeDateUnit = (humanizeDate.length === 2)
        ? humanizeDate[1].endsWith('s') ? humanizeDate[1] : humanizeDate[1] + 's'
        : humanizeDate[2].endsWith('s') ? humanizeDate[2] : humanizeDate[2] + 's';
      return humanizeDateUnit;
    }
    return 'seconds'
  }

  changeForceEnrollment(){
    this.enrollment.forceEnrollment = !this.enrollment.forceEnrollment;
    this.formChanged = true;
  }

  changeRememberDeviceActive() {
    this.rememberDevice.active = !this.rememberDevice.active
    this.formChanged = true;
    if (!this.rememberDevice.deviceIdentifierId) {
      this.rememberDevice.deviceIdentifierId = this.deviceIdentifiers[0].id;
    }
  }

  updateDeviceIdentifierId($event) {
    this.rememberDevice.deviceIdentifierId = $event.value
    this.formChanged = true;
  }

  hasDeviceIdentifierPlugins() {
    return this.deviceIdentifiers && this.deviceIdentifiers.length > 0;
  }
}

@Component({
  selector: 'mfa-step-up-dialog',
  templateUrl: './dialog/mfa-step-up-info.component.html',
})
export class MfaStepUpDialog {
  constructor(public dialogRef: MatDialogRef<MfaStepUpDialog>) {
  }
}

@Component({
  selector: 'adaptive-mfa-dialog',
  templateUrl: './dialog/adaptive-mfa-info.component.html',
})
export class AdaptiveMfaDialog {
  constructor(public dialogRef: MatDialogRef<AdaptiveMfaDialog>) {
  }
}
