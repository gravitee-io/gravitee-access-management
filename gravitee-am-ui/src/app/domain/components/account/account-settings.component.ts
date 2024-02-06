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
import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import moment, { duration, unitOfTime } from 'moment';

import { BotDetectionService } from '../../../services/bot-detection.service';
import { ProviderService } from '../../../services/provider.service';

interface Duration {
  time: number;
  unit: string;
}

@Component({
  selector: 'app-account-settings',
  templateUrl: './account-settings.component.html',
  styleUrls: ['./account-settings.component.scss'],
})
export class AccountSettingsComponent implements OnInit, OnChanges {
  // eslint-disable-next-line @angular-eslint/no-output-on-prefix
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
  private defaultLoginAttemptsResetTimeInSecond = 43200; // 12 hours
  private defaultAccountBlockedDurationInSecond = 7200; // 2 hours
  private defaultMFAChallengeAttemptsResetTimeInSecond = 60; // 1 minutes
  private defaultRememberMeDurationInSecond = 1209600; // 14 days
  private defaultMFAChallengeMaxAttempts = 3;

  loginAttemptsResetTime: Duration = { time: null, unit: null };
  accountBlockedDuration: Duration = { time: null, unit: null };
  mfaChallengeAttemptsResetTime: Duration = { time: null, unit: null };
  rememberMeDuration: Duration = { time: null, unit: null };

  availableFields = [
    { key: 'email', label: 'Email', type: 'email' },
    { key: 'username', label: 'Username', type: 'text' },
  ];

  newField: any = {};

  selectedFields = [];

  public units = [
    { id: 'seconds', name: 'SECONDS' },
    { id: 'minutes', name: 'MINUTES' },
    { id: 'hours', name: 'HOURS' },
    { id: 'days', name: 'DAYS' },
    { id: 'weeks', name: 'WEEKS' },
    { id: 'months', name: 'MONTHS' },
    { id: 'years', name: 'YEARS' },
  ];

  constructor(private route: ActivatedRoute, private providerService: ProviderService, private botDetectionService: BotDetectionService) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.initDateValues();
    this.initSelectedFields();
    this.providerService.findUserProvidersByDomain(this.domainId).subscribe((response) => {
      this.userProviders = response;
    });

    this.botDetectionService.findByDomain(this.domainId).subscribe((response) => {
      this.botDetectionPlugins = response;
    });
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
      accountSettings = { inherited: true };
    } else {
      // set duration values
      accountSettings.loginAttemptsResetTime = this.getDuration(this.loginAttemptsResetTime);
      accountSettings.accountBlockedDuration = this.getDuration(this.accountBlockedDuration);
      accountSettings.mfaChallengeAttemptsResetTime = this.getDuration(this.mfaChallengeAttemptsResetTime);
      accountSettings.rememberMeDuration = this.getDuration(this.rememberMeDuration);

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
    this.newField = { ...this.availableFields.filter((f) => f.key === event.value)[0] };
  }

  addField() {
    this.selectedFields.push({ ...this.newField });
    this.selectedFields = [...this.selectedFields];
    this.newField = {};
    this.formChanged = true;
  }

  removeField(key) {
    const idx = this.selectedFields.findIndex((item) => item.key === key);
    if (idx >= 0) {
      this.selectedFields.splice(idx, 1);
      this.selectedFields = [...this.selectedFields];
      this.formChanged = true;
    }
  }

  isFieldSelected(key) {
    return this.selectedFields.findIndex((item) => item.key === key) >= 0;
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
    this.loginAttemptsResetTime = this.getHumanizeDuration(
      this.getDuration(this.loginAttemptsResetTime) || this.defaultLoginAttemptsResetTimeInSecond,
    );
    this.accountBlockedDuration = this.getHumanizeDuration(
      this.getDuration(this.accountBlockedDuration) || this.defaultAccountBlockedDurationInSecond,
    );
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

  isSendVerifyRegistrationAccountEmail() {
    return this.accountSettings && this.accountSettings.sendVerifyRegistrationAccountEmail;
  }

  enableSendVerifyRegistrationAccountEmail(event) {
    this.accountSettings.sendVerifyRegistrationAccountEmail = event.checked;
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

  enableRememberMe(event) {
    this.accountSettings.rememberMe = event.checked;
    this.formChanged = true;

    this.rememberMeDuration = this.getHumanizeDuration(this.getDuration(this.rememberMeDuration) || this.defaultRememberMeDurationInSecond);
  }

  isRememberMeEnabled() {
    return this.accountSettings && this.accountSettings.rememberMe;
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
      if (this.loginAttemptsResetTime.time < 1) {
        return false;
      } else if (!this.loginAttemptsResetTime.unit) {
        return false;
      }
      if (this.accountBlockedDuration.time < 1) {
        return false;
      } else if (!this.accountBlockedDuration.unit) {
        return false;
      }
    }

    if (this.accountSettings.resetPasswordCustomForm) {
      return this.selectedFields && this.selectedFields.length > 0;
    }

    if (this.accountSettings.mfaChallengeAttemptsDetectionEnabled) {
      if (this.accountSettings.mfaChallengeMaxAttempts < 1) {
        return false;
      }
      if (this.mfaChallengeAttemptsResetTime.time < 1) {
        return false;
      }
      if (!this.mfaChallengeAttemptsResetTime.unit) {
        return false;
      }
    }

    if (this.accountSettings.rememberMe) {
      if (this.rememberMeDuration.time < 1) {
        return false;
      } else if (!this.rememberMeDuration.unit) {
        return false;
      }
    }

    return true;
  }

  private initDateValues() {
    if (this.accountSettings.loginAttemptsResetTime > 0) {
      this.loginAttemptsResetTime = this.getHumanizeDuration(
        this.accountSettings.loginAttemptsResetTime || this.defaultLoginAttemptsResetTimeInSecond,
      );
    }

    if (this.accountSettings.accountBlockedDuration > 0) {
      this.accountBlockedDuration = this.getHumanizeDuration(
        this.accountSettings.accountBlockedDuration || this.defaultAccountBlockedDurationInSecond,
      );
    }

    if (this.accountSettings.rememberMeDuration > 0) {
      this.rememberMeDuration = this.getHumanizeDuration(this.accountSettings.rememberMeDuration || this.defaultRememberMeDurationInSecond);
    }

    if (this.accountSettings.mfaChallengeAttemptsResetTime > 0) {
      this.mfaChallengeAttemptsResetTime = this.getHumanizeDuration(
        this.accountSettings.mfaChallengeAttemptsResetTime || this.defaultMFAChallengeAttemptsResetTimeInSecond,
      );
    }
  }

  private initSelectedFields() {
    if (this.accountSettings.resetPasswordCustomForm) {
      this.selectedFields = this.accountSettings.resetPasswordCustomFormFields || [];
    }
  }

  private getHumanizeDuration(value): Duration {
    const humanizeDate = duration(value, 'seconds').humanize().split(' ');
    const time = humanizeDate.length === 2 ? (humanizeDate[0] === 'a' || humanizeDate[0] === 'an' ? 1 : humanizeDate[0]) : value;
    const unit =
      humanizeDate.length === 2
        ? humanizeDate[1].endsWith('s')
          ? humanizeDate[1]
          : humanizeDate[1] + 's'
        : humanizeDate[2].endsWith('s')
        ? humanizeDate[2]
        : humanizeDate[2] + 's';
    return { time, unit };
  }

  private getDuration(duration: Duration) {
    return moment.duration(duration.time, <unitOfTime.DurationConstructor>duration.unit).asSeconds();
  }

  isMFAChallengeBrutForceAuthenticationEnabled() {
    return this.accountSettings && this.accountSettings.mfaChallengeAttemptsDetectionEnabled;
  }

  enableMFABrutForceAuthenticationDetection(event) {
    this.accountSettings.mfaChallengeAttemptsDetectionEnabled = event.checked;
    this.formChanged = true;

    // apply default values
    this.accountSettings.mfaChallengeMaxAttempts = this.defaultMFAChallengeMaxAttempts;
    this.mfaChallengeAttemptsResetTime = this.getHumanizeDuration(
      this.getDuration(this.mfaChallengeAttemptsResetTime) || this.defaultMFAChallengeAttemptsResetTimeInSecond,
    );
  }

  enableMFAChallengeSendVerifyAlertEmail(event) {
    this.accountSettings.mfaChallengeSendVerifyAlertEmail = event.checked;
    this.formChanged = true;
  }

  isMFAChallengeSendVerifyAlertEmailEnabled() {
    return this.accountSettings && this.accountSettings.mfaChallengeSendVerifyAlertEmail;
  }
}
