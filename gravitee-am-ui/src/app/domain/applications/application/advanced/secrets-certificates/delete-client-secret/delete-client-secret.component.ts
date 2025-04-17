import { Component, Inject } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface DeleteClientSecretData {
  description: string;
}

@Component({
  selector: 'app-delete-client-secret',
  templateUrl: './delete-client-secret.component.html',
  styleUrl: '../secrets-certificates.component.scss',
})
export class DeleteClientSecretComponent {
  descriptionControl = new FormControl('');
  description: string;
  constructor(
    @Inject(MAT_DIALOG_DATA) dialogData: DeleteClientSecretData,
    public dialogRef: MatDialogRef<DeleteClientSecretComponent>,
  ) {
    this.description = dialogData.description;
    this.descriptionControl.setValidators([Validators.required, Validators.pattern(`^${this.description}`)]);
  }
}
