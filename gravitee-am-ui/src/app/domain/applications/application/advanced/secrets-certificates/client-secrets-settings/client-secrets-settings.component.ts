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
