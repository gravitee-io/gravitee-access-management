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
import {Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {ProviderService} from '../../../services/provider.service';
import * as moment from 'moment';

@Component({
  selector: 'app-account-settings',
  templateUrl: './account-settings.component.html',
  styleUrls: ['./account-settings.component.scss']
})
export class AccountSettingsComponent implements OnInit, OnChanges {
  @Output() onSavedAccountSettings = new EventEmitter<any>();
  @Input() accountSettings: any;
  @Input() inheritMode = false;
  @Input() readonly = false;
  @ViewChild('accountForm', { static: true }) form: any;
  formChanged = false;
  userProviders: any[];
  private domainId: string;
  private defaultMaxAttempts = 10;
  private defaultLoginAttemptsResetTime = 12;
  private defaultLoginAttemptsResetTimeUnit = 'hours';
  private defaultAccountBlockedDuration = 2;
  private defaultAccountBlockedDurationUnit = 'hours';

  constructor(private route: ActivatedRoute,
              private providerService: ProviderService) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.parent.parent.params['domainId']
        ? this.route.snapshot.parent.parent.params['domainId']
        : this.route.snapshot.parent.parent.parent.params['domainId'];
    this.initDateValues();
    this.providerService.findUserProvidersByDomain(this.domainId).subscribe(response => {
      this.userProviders = response;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.accountSettings.previousValue && changes.accountSettings.currentValue) {
      this.accountSettings = changes.accountSettings.currentValue;
      this.initDateValues();
    }
  }

  save() {
    let accountSettings = Object.assign({}, this.accountSettings);
    if (accountSettings.inherited) {
      accountSettings = { 'inherited' : true };
    } else {
      // set duration values
      accountSettings.loginAttemptsResetTime = this.getDuration(accountSettings.loginAttemptsResetTime, accountSettings.loginAttemptsResetTimeUnitTime);
      accountSettings.accountBlockedDuration = this.getDuration(accountSettings.accountBlockedDuration, accountSettings.accountBlockedDurationUnitTime);
      delete accountSettings.loginAttemptsResetTimeUnitTime;
      delete accountSettings.accountBlockedDurationUnitTime;
    }

    this.onSavedAccountSettings.emit(accountSettings);
    this.formChanged = false;
  }

  enableInheritMode(event) {
    this.accountSettings.inherited = event.checked;
    this.formChanged = true;
  }

  isInherited() {
    return this.accountSettings && this.accountSettings.inherited;
  }

  enableBrutForceAuthenticationDetection(event) {
    this.accountSettings.loginAttemptsDetectionEnabled = event.checked;
    this.formChanged = true;

    // apply default values
    this.accountSettings.maxLoginAttempts = this.accountSettings.maxLoginAttempts || this.defaultMaxAttempts;
    this.accountSettings.loginAttemptsResetTime = this.accountSettings.loginAttemptsResetTime || this.defaultLoginAttemptsResetTime;
    this.accountSettings.loginAttemptsResetTimeUnitTime = this.accountSettings.loginAttemptsResetTimeUnitTime || this.defaultLoginAttemptsResetTimeUnit;
    this.accountSettings.accountBlockedDuration = this.accountSettings.accountBlockedDuration || this.defaultAccountBlockedDuration;
    this.accountSettings.accountBlockedDurationUnitTime = this.accountSettings.accountBlockedDurationUnitTime || this.defaultAccountBlockedDurationUnit;
  }

  isBrutForceAuthenticationEnabled() {
    return this.accountSettings && this.accountSettings.loginAttemptsDetectionEnabled;
  }

  enableDynamicUserRegistration(event) {
    this.accountSettings.dynamicUserRegistration = event.checked;
    this.formChanged = true;
  }

  isDynamicUserRegistrationEnabled() {
    return this.accountSettings && this.accountSettings.dynamicUserRegistration;
  }

  enableCompleteRegistration(event) {
    this.accountSettings.completeRegistrationWhenResetPassword = event.checked;
    this.formChanged = true;
  }

  isCompleteRegistrationEnabled() {
    return this.accountSettings && this.accountSettings.completeRegistrationWhenResetPassword;
  }

  enableAutoLoginAfterRegistration(event) {
    this.accountSettings.autoLoginAfterRegistration = event.checked;
    this.formChanged = true;
  }

  isAutoLoginAfterRegistrationEnabled() {
    return this.accountSettings && this.accountSettings.autoLoginAfterRegistration;
  }

  enableAutoLoginAfterResetPassword(event) {
    this.accountSettings.autoLoginAfterResetPassword = event.checked;
    this.formChanged = true;
  }

  isAutoLoginAfterResetPasswordEnabled() {
    return this.accountSettings && this.accountSettings.autoLoginAfterResetPassword;
  }

  enableSendRecoverAccountEmail(event) {
    this.accountSettings.sendRecoverAccountEmail = event.checked;
    this.formChanged = true;
  }

  isSendRecoverAccountEmailEnabled() {
    return this.accountSettings && this.accountSettings.sendRecoverAccountEmail;
  }

  updateModel() {
    this.formChanged = true;
  }

  formIsValid() {
    if (this.accountSettings.loginAttemptsDetectionEnabled) {
      if (this.accountSettings.maxLoginAttempts < 1) {
        return false;
      }
      if (this.accountSettings.loginAttemptsResetTime < 1) {
        return false;
      } else if (!this.accountSettings.loginAttemptsResetTimeUnitTime) {
        return false;
      }
      if (this.accountSettings.accountBlockedDuration < 1) {
        return false;
      } else if (!this.accountSettings.accountBlockedDurationUnitTime) {
        return false;
      }
    }
    return true;
  }

  private initDateValues() {
    if (this.accountSettings.loginAttemptsResetTime > 0) {
      const loginAttemptsResetTime = this.getHumanizeDuration(this.accountSettings.loginAttemptsResetTime);
      this.accountSettings.loginAttemptsResetTime = loginAttemptsResetTime[0];
      this.accountSettings.loginAttemptsResetTimeUnitTime = loginAttemptsResetTime[1];
    }

    if (this.accountSettings.accountBlockedDuration > 0) {
      const accountBlockedDuration = this.getHumanizeDuration(this.accountSettings.accountBlockedDuration);
      this.accountSettings.accountBlockedDuration = accountBlockedDuration[0];
      this.accountSettings.accountBlockedDurationUnitTime = accountBlockedDuration[1];
    }
  }

  private getHumanizeDuration(value) {
    const humanizeDate = moment.duration(value, 'seconds').humanize().split(' ');
    const humanizeDateValue = (humanizeDate.length === 2)
      ? (humanizeDate[0] === 'a' || humanizeDate[0] === 'an') ? 1 : humanizeDate[0]
      : value;
    const humanizeDateUnit = (humanizeDate.length === 2)
      ? humanizeDate[1].endsWith('s') ? humanizeDate[1] : humanizeDate[1] + 's'
      : humanizeDate[2].endsWith('s') ? humanizeDate[2] : humanizeDate[2] + 's';
    return new Array(humanizeDateValue, humanizeDateUnit);
  }

  private getDuration(value, unit) {
    return moment.duration(parseInt(value), unit).asSeconds();
  }
}
