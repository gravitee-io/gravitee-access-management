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
import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { duration } from 'moment';

import { TimeConverterService } from '../../../../../../services/time-converter.service';
import { SnackbarService } from '../../../../../../services/snackbar.service';

@Component({
  selector: 'app-client-secrets-settings',
  templateUrl: './client-secrets-settings.component.html',
  styleUrl: '../secrets-certificates.component.scss',
})
export class ClientSecretsSettingsComponent {
  domainRules: boolean;
  domainSettingsUrl: string;
  secretSettings: any;
  humanTime: any;
  formChanged = false;
  domainSettings: any;

  constructor(
    public dialogRef: MatDialogRef<ClientSecretsSettingsComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
    private router: Router,
    private timeConverterService: TimeConverterService,
    private snackbarService: SnackbarService,
  ) {
    this.domainSettingsUrl = this.data.domainSettingsUrl;
    this.secretSettings = this.data.secretSettings ? this.data.secretSettings : { enabled: false };
    this.domainRules = !this.secretSettings?.enabled;
    this.domainSettings = this.data.domainSettings;
    if (!this.secretSettings.enabled && this.domainSettings.enabled && this.domainSettings?.expiryTimeSeconds) {
      this.secretSettings.expiryTimeSeconds = this.domainSettings.expiryTimeSeconds;
    }

    const time = this.secretSettings?.expiryTimeSeconds ? this.secretSettings.expiryTimeSeconds : 0;
    this.humanTime = {
      expirationTime: this.timeConverterService.getTime(time, 'seconds'),
      expirationUnit: time > 0 ? this.timeConverterService.getUnitTime(time, 'seconds') : 'none',
    };

    if (!this.secretSettings.enabled && !this.domainSettings.enabled) {
      this.humanTime.expirationUnit = 'none';
      this.humanTime.expirationTime = 0;
    }
  }

  closeDialog(save: boolean): void {
    if (save) {
      if (
        this.humanTime.expirationUnit !== 'none' &&
        (this.humanTime.expirationTime === null || this.humanTime.expirationTime === undefined || this.humanTime.expirationTime <= 0)
      ) {
        this.snackbarService.open('Please enter a valid expiry time duration');
      } else {
        this.secretSettings.expiryTimeSeconds = this.humanTimeToSeconds();
        this.secretSettings.enabled = !this.domainRules;
        this.dialogRef.close(this.secretSettings);
      }
    } else {
      this.dialogRef.close();
    }
  }

  goToDomainSettings(): void {
    this.dialogRef.close();
    this.router.navigate([this.domainSettingsUrl]);
  }

  onUnitChange($event: any): void {
    this.humanTime.expirationUnit = $event.value;
    this.formChanged = true;
    if ($event.value === 'none') {
      this.humanTime.expirationTime = null;
    }
  }

  setFormChanged(): void {
    this.formChanged = true;
  }

  onTimeChange($event: any): void {
    this.humanTime.expirationTime = Math.abs($event.target.value);
    this.formChanged = true;
  }

  private humanTimeToSeconds(): number {
    return duration(this.humanTime.expirationTime, this.humanTime.expirationUnit).asSeconds();
  }
}
