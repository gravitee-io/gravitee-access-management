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
import { FormControl, FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-client-secrets-settings',
  templateUrl: './client-secrets-settings.component.html',
  styleUrl: '../secrets-certificates.component.scss',
})
export class ClientSecretsSettingsComponent {
  settingsForm: FormGroup;
  useDomainRules = new FormControl(true);
  domainRules: boolean;

  constructor(
    public dialogRef: MatDialogRef<ClientSecretsSettingsComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
  ) {
    this.settingsForm = new FormGroup({
      expiryUnit: new FormControl({ value: 'Days', disabled: this.useDomainRules.value }),
      expiryDuration: new FormControl({ value: 180, disabled: this.useDomainRules.value }),
    });

    this.useDomainRules.valueChanges.subscribe((enabled) => {
      this.domainRules = enabled;
      if (enabled) {
        this.settingsForm.get('expiryUnit')?.disable();
        this.settingsForm.get('expiryDuration')?.disable();
      } else {
        this.settingsForm.get('expiryUnit')?.enable();
        this.settingsForm.get('expiryDuration')?.enable();
      }
    });
  }

  closeDialog(save: boolean): void {
    if (save && !this.domainRules) {
      this.dialogRef.close(this.settingsForm.value);
    } else {
      this.dialogRef.close();
    }
  }
}
