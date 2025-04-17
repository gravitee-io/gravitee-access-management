import { Component } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-new-client-secret',
  templateUrl: './new-client-secret.component.html',
  styleUrl: '../secrets-certificates.component.scss',
})
export class NewClientSecretComponent {
  descriptionControl = new FormControl('', [Validators.required, this.noWhitespaceValidator]);
  constructor(public dialogRef: MatDialogRef<NewClientSecretComponent>) {}

  closeDialog() {
    if (this.descriptionControl.invalid) {
      this.descriptionControl.markAsTouched();
      return;
    }
    this.dialogRef.close(this.descriptionControl.value);
  }
  public noWhitespaceValidator(control: FormControl) {
    return (control.value || '').trim().length ? null : { whitespace: true };
  }
}
