import { Component } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-renew-client-secret',
  templateUrl: './renew-client-secret.component.html',
  styleUrl: '../secrets-certificates.component.scss',
})
export class RenewClientSecretComponent {
  constructor(public dialogRef: MatDialogRef<RenewClientSecretComponent>) {}
}
