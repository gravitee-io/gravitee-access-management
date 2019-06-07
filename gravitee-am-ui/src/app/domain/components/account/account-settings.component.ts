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
import * as moment from "moment";

@Component({
  selector: 'app-account-settings',
  templateUrl: './account-settings.component.html',
  styleUrls: ['./account-settings.component.scss']
})
export class AccountSettingsComponent implements OnInit, OnChanges {
  @Output() onSavedAccountSettings = new EventEmitter<any>();
  @Input() accountSettings: any = {};
  @Input() inheritMode: boolean = false;
  @ViewChild('accountForm') form: any;
  formChanged: boolean = false;
  private defaultMaxAttempts: number = 10;
  private defaultLoginAttemptsResetTime: number = 12;
  private defaultLoginAttemptsResetTimeUnit: string = "hours";
  private defaultAccountBlockedDuration: number = 2;
  private defaultAccountBlockedDurationUnit: string = "hours";

  constructor() {}

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
    this.form.reset(this.accountSettings);
  }

  ngOnInit(): void {
    this.accountSettings = this.accountSettings || { 'inherited' : this.inheritMode };
    this.initDateValues();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.accountSettings.previousValue && changes.accountSettings.currentValue) {
      this.accountSettings = changes.accountSettings.currentValue;
      this.initDateValues();
    }
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

  enableCompleteRegistration(event) {
    this.accountSettings.completeRegistrationWhenResetPassword = event.checked;
    this.formChanged = true;
  }

  isCompleteRegistrationEnabled() {
    return this.accountSettings && this.accountSettings.completeRegistrationWhenResetPassword;
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
      let loginAttemptsResetTime = this.getHumanizeDuration(this.accountSettings.loginAttemptsResetTime);
      this.accountSettings.loginAttemptsResetTime = loginAttemptsResetTime[0];
      this.accountSettings.loginAttemptsResetTimeUnitTime = loginAttemptsResetTime[1];
    }

    if (this.accountSettings.accountBlockedDuration > 0) {
      let accountBlockedDuration = this.getHumanizeDuration(this.accountSettings.accountBlockedDuration);
      this.accountSettings.accountBlockedDuration = accountBlockedDuration[0];
      this.accountSettings.accountBlockedDurationUnitTime = accountBlockedDuration[1];
    }
  }

  private getHumanizeDuration(value) {
    let humanizeDate = moment.duration(value, 'seconds').humanize().split(' ');
    let humanizeDateValue = humanizeDate[0] === 'a' ? 1 : humanizeDate[0];
    let humanizeDateUnit = humanizeDate[1].endsWith('s') ? humanizeDate[1] : humanizeDate[1] + 's';
    return new Array(humanizeDateValue, humanizeDateUnit);
  }

  private getDuration(value, unit) {
    return moment.duration(parseInt(value), unit).asSeconds();
  }
}
