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
import {Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import * as moment from "moment";

@Component({
  selector: 'app-account-settings',
  templateUrl: './account-settings.component.html',
  styleUrls: ['./account-settings.component.scss']
})
export class AccountSettingsComponent implements OnInit {
  @Output() onSavedAccountSettings = new EventEmitter<any>();
  @Input() accountSettings: any = {};
  @Input() inheritMode: boolean = false;
  @ViewChild('accountForm') form: any;
  formChanged: boolean = false;
  localAccountSettings: any;
  private defaultMaxAttempts: number = 10;
  private defaultLoginAttemptsResetTime: number = 12;
  private defaultLoginAttemptsResetTimeUnit: string = "hours";
  private defaultAccountBlockedDuration: number = 2;
  private defaultAccountBlockedDurationUnit: string = "hours";

  constructor() {}

  save() {
    let accountSettings = Object.assign({}, this.localAccountSettings);
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
    this.form.reset(this.localAccountSettings);
  }

  ngOnInit(): void {
    this.localAccountSettings = Object.assign({}, this.accountSettings || { 'inherited' : this.inheritMode });
    this.initDateValues();
  }

  enableInheritMode(event) {
    this.localAccountSettings.inherited = event.checked;
    this.formChanged = true;
  }

  isInherited() {
    return this.localAccountSettings && this.localAccountSettings.inherited;
  }

  enableBrutForceAuthenticationDetection(event) {
    this.localAccountSettings.loginAttemptsDetectionEnabled = event.checked;
    this.formChanged = true;

    // apply default values
    this.localAccountSettings.maxLoginAttempts = this.localAccountSettings.maxLoginAttempts || this.defaultMaxAttempts;
    this.localAccountSettings.loginAttemptsResetTime = this.localAccountSettings.loginAttemptsResetTime || this.defaultLoginAttemptsResetTime;
    this.localAccountSettings.loginAttemptsResetTimeUnitTime = this.localAccountSettings.loginAttemptsResetTimeUnitTime || this.defaultLoginAttemptsResetTimeUnit;
    this.localAccountSettings.accountBlockedDuration = this.localAccountSettings.accountBlockedDuration || this.defaultAccountBlockedDuration;
    this.localAccountSettings.accountBlockedDurationUnitTime = this.localAccountSettings.accountBlockedDurationUnitTime || this.defaultAccountBlockedDurationUnit;
  }

  isBrutForceAuthenticationEnabled() {
    return this.localAccountSettings && this.localAccountSettings.loginAttemptsDetectionEnabled;
  }

  formIsValid() {
    if (this.localAccountSettings.loginAttemptsDetectionEnabled) {
      if (this.localAccountSettings.maxLoginAttempts < 1) {
        return false;
      }
      if (this.localAccountSettings.loginAttemptsResetTime < 1) {
        return false;
      } else if (!this.localAccountSettings.loginAttemptsResetTimeUnitTime) {
        return false;
      }
      if (this.localAccountSettings.accountBlockedDuration < 1) {
        return false;
      } else if (!this.localAccountSettings.accountBlockedDurationUnitTime) {
        return false;
      }
    }
    return true;
  }

  private initDateValues() {
    if (this.localAccountSettings.loginAttemptsResetTime > 0) {
      let loginAttemptsResetTime = this.getHumanizeDuration(this.localAccountSettings.loginAttemptsResetTime);
      this.localAccountSettings.loginAttemptsResetTime = loginAttemptsResetTime[0];
      this.localAccountSettings.loginAttemptsResetTimeUnitTime = loginAttemptsResetTime[1];
    }

    if (this.localAccountSettings.accountBlockedDuration > 0) {
      let accountBlockedDuration = this.getHumanizeDuration(this.localAccountSettings.accountBlockedDuration);
      this.localAccountSettings.accountBlockedDuration = accountBlockedDuration[0];
      this.localAccountSettings.accountBlockedDurationUnitTime = accountBlockedDuration[1];
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
