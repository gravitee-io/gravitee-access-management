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
import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { duration } from 'moment';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { TimeConverterService } from '../../../../services/time-converter.service';

@Component({
  selector: 'app-client-secrets-settings',
  templateUrl: './client-secrets-settings.component.html',
  styleUrl: '../../client-secrets-management.component.scss',
  standalone: false,
})
export class ClientSecretsSettingsComponent implements OnInit {
  domainSettingsUrl: string;
  secretSettings: any;
  form: FormGroup;
  domainRules = true;
  domainSettings: any;
  domainExpirationTime: number;
  domainExpirationUnit: string;
  tempTime: number;
  tempUnit: string;

  constructor(
    public dialogRef: MatDialogRef<ClientSecretsSettingsComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
    private router: Router,
    private timeConverterService: TimeConverterService,
  ) {}

  ngOnInit(): void {
    this.domainSettingsUrl = this.data.domainSettingsUrl;
    this.secretSettings = this.data.secretSettings ? this.data.secretSettings : { enabled: false };
    this.domainRules = !this.data.secretSettings?.enabled;
    this.domainSettings = this.data.domainSettings;
    const domainTime = this.domainSettings?.expiryTimeSeconds ? this.domainSettings.expiryTimeSeconds : 0;
    this.domainExpirationTime = this.domainSettings?.enabled ? this.timeConverterService.getTime(domainTime, 'seconds') : 0;
    this.domainExpirationUnit =
      domainTime > 0 && this.domainSettings?.enabled ? this.timeConverterService.getUnitTime(domainTime, 'seconds') : 'none';

    if (!this.secretSettings.enabled && this.domainSettings?.enabled && domainTime) {
      this.secretSettings.expiryTimeSeconds = this.domainSettings.expiryTimeSeconds;
    }

    const time = this.secretSettings?.expiryTimeSeconds ? this.secretSettings.expiryTimeSeconds : 0;

    this.tempTime = this.timeConverterService.getTime(time, 'seconds');
    this.tempUnit = time > 0 ? this.timeConverterService.getUnitTime(time, 'seconds') : 'none';

    if (!this.secretSettings.enabled && !this.domainSettings?.enabled) {
      this.tempTime = 0;
      this.tempUnit = 'none';
    }

    this.form = new FormGroup({
      domainRules: new FormControl(!this.secretSettings.enabled),
      expirationTime: new FormControl({ value: this.tempTime, disabled: !this.secretSettings.enabled }),
      expirationUnit: new FormControl({ value: this.tempUnit, disabled: !this.secretSettings.enabled }),
    });

    this.form.get('domainRules')?.valueChanges.subscribe((enabled) => {
      if (enabled) {
        this.tempTime = this.form.get('expirationTime')?.value;
        this.form.get('expirationTime')?.setValue(this.domainExpirationTime);
        this.form.get('expirationTime')?.clearValidators();
        this.form.get('expirationTime')?.disable();
        this.tempUnit = this.form.get('expirationUnit')?.value;
        this.form.get('expirationUnit')?.setValue(this.domainExpirationUnit);
        this.form.get('expirationUnit')?.clearValidators();
        this.form.get('expirationUnit')?.disable();
      } else {
        this.form.get('expirationTime')?.setValue(this.tempTime);
        this.form.get('expirationTime')?.setValidators([Validators.required, Validators.min(1)]);
        this.form.get('expirationTime')?.enable();
        this.form.get('expirationUnit')?.setValue(this.tempUnit);
        this.form.get('expirationUnit')?.setValidators([Validators.required]);
        this.form.get('expirationUnit')?.enable();
      }

      this.form.get('expirationTime')?.updateValueAndValidity();
      this.form.get('expirationUnit')?.updateValueAndValidity();
    });

    this.form.get('expirationUnit')?.valueChanges.subscribe((unit) => {
      this.determinateTimeDuration(unit);
    });

    if (this.form.get('expirationUnit')?.value === 'none') {
      this.determinateTimeDuration('none');
    }

    this.form.valueChanges.subscribe(() => {
      this.form.markAsDirty();
    });
  }

  determinateTimeDuration(unit: string) {
    if (!this.form.get('domainRules')?.value) {
      if (unit === 'none') {
        this.form.get('expirationTime')?.disable();
        this.form.get('expirationTime')?.clearValidators();
      } else {
        this.form.get('expirationTime')?.setValidators([Validators.required, Validators.min(1)]);
        this.form.get('expirationTime')?.enable();
      }
      this.form.get('expirationTime')?.updateValueAndValidity();
    }
  }

  closeDialog(save: boolean): void {
    if (save && this.form.valid) {
      const settings = {
        enabled: !this.form.get('domainRules')?.value,
        expiryTimeSeconds: duration(this.form.value.expirationTime, this.form.value.expirationUnit).asSeconds(),
      };
      this.dialogRef.close(settings);
    } else {
      this.dialogRef.close();
    }
  }

  goToDomainSettings(): void {
    this.dialogRef.close();
    this.router.navigate([this.domainSettingsUrl]);
  }
}
