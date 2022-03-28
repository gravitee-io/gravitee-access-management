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
import moment from 'moment';
import { filter } from 'lodash';
import { BotDetectionService } from 'app/services/bot-detection.service';

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
  botDetectionPlugins: any[];

  private domainId: string;
  private defaultMaxAttempts = 10;
  private defaultLoginAttemptsResetTime = 12;
  private defaultLoginAttemptsResetTimeUnit = 'hours';
  private defaultAccountBlockedDuration = 2;
  private defaultAccountBlockedDurationUnit = 'hours';

  availableFields = [
    {"key" : "email", "label" : "Email", "type" : "email"},
    {"key" : "username", "label" : "Username", "type" : "text"},
  ];

  newField = {};

  selectedFields = [];

  constructor(private route: ActivatedRoute,
              private providerService: ProviderService,
              private botDetectionService: BotDetectionService) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.initDateValues();
    this.initSelectedFields();
    this.providerService.findUserProvidersByDomain(this.domainId).subscribe(response => {
      this.userProviders = response;
    });

    this.botDetectionService.findByDomain(this.domainId).subscribe(response => {
      this.botDetectionPlugins = response;
    })
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.accountSettings.previousValue && changes.accountSettings.currentValue) {
      this.accountSettings = changes.accountSettings.currentValue;
      this.initDateValues();
      this.initSelectedFields();
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

      // set list of fields that will be used by the ForgotPassword Form
      if (accountSettings.resetPasswordCustomForm) {
        accountSettings['resetPasswordCustomFormFields'] = this.selectedFields;
      } else {
        delete accountSettings.resetPasswordCustomFormFields;
        delete accountSettings.resetPasswordConfirmIdentity;
      }

      if (!accountSettings.useBotDetection) {
        delete accountSettings.botDetectionPlugin;
      }
    }

    this.onSavedAccountSettings.emit(accountSettings);
    this.formChanged = false;
  }

  onFieldSelected(event) {
    this.newField = {...this.availableFields.filter(f=> f.key === event.value)[0]};
  }

  addField() {
    this.selectedFields.push({...this.newField});
    this.selectedFields = [...this.selectedFields];
    this.newField = {};
    this.formChanged = true;
  }

  removeField(key) {
    let idx = this.selectedFields.findIndex(item => item.key === key);
    if (idx >= 0) {
        this.selectedFields.splice(idx, 1);
        this.selectedFields = [...this.selectedFields];
        this.formChanged = true;
    }
  }

  isFieldSelected(key) {
    return this.selectedFields.findIndex(item => item.key === key) >= 0;
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

  enableDeletePasswordlessDevicesAfterResetPassword(event) {
    this.accountSettings.deletePasswordlessDevicesAfterResetPassword = event.checked;
    this.formChanged = true;
  }

  isDeletePasswordlessDevicesAfterResetPasswordEnabled() {
    return this.accountSettings && this.accountSettings.deletePasswordlessDevicesAfterResetPassword;
  }

  enableBotDetection(event) {
    this.accountSettings.useBotDetection = event.checked;
    this.formChanged = true;
  }

  isBotDetectionEnabled() {
    return this.accountSettings && this.accountSettings.useBotDetection;
  }

  hasBotDetectionPlugins() {
    return this.botDetectionPlugins && this.botDetectionPlugins.length > 0;
  }

  enableResetPasswordCustomForm(event) {
    this.accountSettings.resetPasswordCustomForm = event.checked;
    this.formChanged = true;
  }

  isResetPasswordCustomFormEnabled() {
    return this.accountSettings && this.accountSettings.resetPasswordCustomForm;
  }

  enableResetPasswordConfirmIdentity(event) {
    this.accountSettings.resetPasswordConfirmIdentity = event.checked;
    this.formChanged = true;
  }

  isResetPasswordConfirmIdentityEnabled() {
    return this.accountSettings && this.accountSettings.resetPasswordConfirmIdentity;
  }

  updateModel() {
    this.formChanged = true;
  }

  enableInvalidateTokens(event) {
    this.accountSettings.resetPasswordInvalidateTokens = event.checked;
    this.formChanged = true;
  }

  isInvalidateTokensEnabled() {
    return this.accountSettings && this.accountSettings.resetPasswordInvalidateTokens;
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

    if (this.accountSettings.resetPasswordCustomForm) {
      return this.selectedFields && this.selectedFields.length > 0;
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

  private initSelectedFields() {
    if (this.accountSettings.resetPasswordCustomForm) {
      this.selectedFields = this.accountSettings.resetPasswordCustomFormFields || [];
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
