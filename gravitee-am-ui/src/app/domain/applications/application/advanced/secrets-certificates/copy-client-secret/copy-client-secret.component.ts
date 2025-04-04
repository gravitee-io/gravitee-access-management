import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { SnackbarService } from '../../../../../../services/snackbar.service';

export interface CopyClientSecretCopyDialogData {
  secret: string;
  renew: boolean;
}

@Component({
  selector: 'app-copy-client-secret',
  templateUrl: './copy-client-secret.component.html',
  styleUrl: '../secrets-certificates.component.scss',
})
export class CopyClientSecretComponent {
  notCopied = true;
  clientSecret: string;
  renew: boolean;

  constructor(
    @Inject(MAT_DIALOG_DATA) dialogData: CopyClientSecretCopyDialogData,
    public dialogRef: MatDialogRef<CopyClientSecretCopyDialogData, void>,
    private snackbarService: SnackbarService,
  ) {
    this.clientSecret = dialogData.secret;
    this.renew = dialogData.renew;
  }

  valueCopied(message: string): void {
    this.notCopied = false;
    this.snackbarService.open(message);
  }

  onSubmit() {
    this.dialogRef.close();
  }
}
