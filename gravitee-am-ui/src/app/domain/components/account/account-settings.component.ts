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
import moment, { unitOfTime } from 'moment';

import { BotDetectionService } from '../../../services/bot-detection.service';
import { ProviderService } from '../../../services/provider.service';
import { TimeConverterService } from '../../../services/time-converter.service';

interface Duration {
  time: number;
  unit: string;
}

@Component({
  selector: 'app-account-settings',
  templateUrl: './account-settings.component.html',
  styleUrls: ['./account-settings.component.scss'],
  standalone: false,
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

  constructor(
    private route: ActivatedRoute,
    private providerService: ProviderService,
    private botDetectionService: BotDetectionService,
    private timeConverterService: TimeConverterService,
  ) {}

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

  save(): void {
    let accountSettings = { ...this.accountSettings };
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

  isInherited(): boolean {
    return this.accountSettings?.inherited;
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

  isBrutForceAuthenticationEnabled(): boolean {
    return this.accountSettings?.loginAttemptsDetectionEnabled;
  }

  enableDynamicUserRegistration(event) {
    this.accountSettings.dynamicUserRegistration = event.checked;
    this.formChanged = true;
  }

  isDynamicUserRegistrationEnabled(): boolean {
    return this.accountSettings?.dynamicUserRegistration;
  }

  enableCompleteRegistration(event) {
    this.accountSettings.completeRegistrationWhenResetPassword = event.checked;
    this.formChanged = true;
  }

  isSendVerifyRegistrationAccountEmail(): boolean {
    return this.accountSettings?.sendVerifyRegistrationAccountEmail;
  }

  enableSendVerifyRegistrationAccountEmail(event) {
    this.accountSettings.sendVerifyRegistrationAccountEmail = event.checked;
    this.formChanged = true;
  }

  isCompleteRegistrationEnabled(): boolean {
    return this.accountSettings?.completeRegistrationWhenResetPassword;
  }

  enableAutoLoginAfterRegistration(event) {
    this.accountSettings.autoLoginAfterRegistration = event.checked;
    this.formChanged = true;
  }

  isAutoLoginAfterRegistrationEnabled(): boolean {
    return this.accountSettings?.autoLoginAfterRegistration;
  }

  enableAutoLoginAfterResetPassword(event) {
    this.accountSettings.autoLoginAfterResetPassword = event.checked;
    this.formChanged = true;
  }

  isAutoLoginAfterResetPasswordEnabled(): boolean {
    return this.accountSettings?.autoLoginAfterResetPassword;
  }

  enableSendRecoverAccountEmail(event) {
    this.accountSettings.sendRecoverAccountEmail = event.checked;
    this.formChanged = true;
  }

  isSendRecoverAccountEmailEnabled(): boolean {
    return this.accountSettings?.sendRecoverAccountEmail;
  }

  enableRememberMe(event) {
    this.accountSettings.rememberMe = event.checked;
    this.formChanged = true;

    this.rememberMeDuration = this.getHumanizeDuration(this.getDuration(this.rememberMeDuration) || this.defaultRememberMeDurationInSecond);
  }

  isRememberMeEnabled(): boolean {
    return this.accountSettings?.rememberMe;
  }

  enableDeletePasswordlessDevicesAfterResetPassword(event) {
    this.accountSettings.deletePasswordlessDevicesAfterResetPassword = event.checked;
    this.formChanged = true;
  }

  isDeletePasswordlessDevicesAfterResetPasswordEnabled(): boolean {
    return this.accountSettings?.deletePasswordlessDevicesAfterResetPassword;
  }

  enableBotDetection(event) {
    this.accountSettings.useBotDetection = event.checked;
    this.formChanged = true;
  }

  isBotDetectionEnabled(): boolean {
    return this.accountSettings?.useBotDetection;
  }

  hasBotDetectionPlugins() {
    return this.botDetectionPlugins && this.botDetectionPlugins.length > 0;
  }

  enableResetPasswordCustomForm(event) {
    this.accountSettings.resetPasswordCustomForm = event.checked;
    this.formChanged = true;
  }

  isResetPasswordCustomFormEnabled(): boolean {
    return this.accountSettings?.resetPasswordCustomForm;
  }

  enableResetPasswordConfirmIdentity(event) {
    this.accountSettings.resetPasswordConfirmIdentity = event.checked;
    this.formChanged = true;
  }

  isResetPasswordConfirmIdentityEnabled(): boolean {
    return this.accountSettings?.resetPasswordConfirmIdentity;
  }

  updateModel() {
    this.formChanged = true;
  }

  enableInvalidateTokens(event) {
    this.accountSettings.resetPasswordInvalidateTokens = event.checked;
    this.formChanged = true;
  }

  isInvalidateTokensEnabled(): boolean {
    return this.accountSettings?.resetPasswordInvalidateTokens;
  }

  formIsValid(): boolean {
    if (this.accountSettings.loginAttemptsDetectionEnabled) {
      if (
        this.accountSettings.maxLoginAttempts < 1 ||
        this.loginAttemptsResetTime.time < 1 ||
        !this.loginAttemptsResetTime.unit ||
        this.accountBlockedDuration.time < 1 ||
        !this.accountBlockedDuration.unit
      ) {
        return false;
      }
    }
    if (this.accountSettings.resetPasswordCustomForm) {
      return this.selectedFields?.length > 0;
    }
    if (this.accountSettings.mfaChallengeAttemptsDetectionEnabled) {
      if (
        this.accountSettings.mfaChallengeMaxAttempts < 1 ||
        this.mfaChallengeAttemptsResetTime.time < 1 ||
        !this.mfaChallengeAttemptsResetTime.unit
      ) {
        return false;
      }
    }
    if (this.accountSettings.rememberMe) {
      if (this.rememberMeDuration.time < 1 || !this.rememberMeDuration.unit) {
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
    const time = this.timeConverterService.getTime(value);
    const unit = this.timeConverterService.getUnitTime(value);
    return { time, unit };
  }

  private getDuration(duration: Duration) {
    return moment.duration(duration.time, <unitOfTime.DurationConstructor>duration.unit).asSeconds();
  }

  isMFAChallengeBrutForceAuthenticationEnabled(): boolean {
    return this.accountSettings?.mfaChallengeAttemptsDetectionEnabled;
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

  isMFAChallengeSendVerifyAlertEmailEnabled(): boolean {
    return this.accountSettings?.mfaChallengeSendVerifyAlertEmail;
  }
}
