import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogActions, MatDialogClose, MatDialogContent, MatDialogModule, MatDialogTitle } from '@angular/material/dialog';
import { MatFormField, MatFormFieldModule, MatHint, MatLabel } from '@angular/material/form-field';
import { MatOption } from '@angular/material/autocomplete';
import { MatSelect } from '@angular/material/select';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatIcon, MatIconModule } from '@angular/material/icon';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatInput, MatInputModule } from '@angular/material/input';
import { MatButton, MatButtonModule, MatIconButton } from '@angular/material/button';
import { ClipboardModule } from 'ngx-clipboard';
import { MatTooltip, MatTooltipModule } from '@angular/material/tooltip';

import { RenewClientSecretComponent } from './renew-client-secret/renew-client-secret.component';
import { NewClientSecretComponent } from './new-client-secret/new-client-secret.component';
import { CopyClientSecretComponent } from './copy-client-secret/copy-client-secret.component';
import { ClientSecretsSettingsComponent } from './client-secrets-settings/client-secrets-settings.component';
import { DeleteClientSecretComponent } from './delete-client-secret/delete-client-secret.component';

@NgModule({
  declarations: [
    ClientSecretsSettingsComponent,
    CopyClientSecretComponent,
    NewClientSecretComponent,
    RenewClientSecretComponent,
    DeleteClientSecretComponent,
  ],
  exports: [
    ClientSecretsSettingsComponent,
    CopyClientSecretComponent,
    NewClientSecretComponent,
    RenewClientSecretComponent,
    DeleteClientSecretComponent,
  ],
  imports: [
    CommonModule,
    MatDialogActions,
    MatHint,
    MatLabel,
    MatFormField,
    MatOption,
    MatSelect,
    MatSlideToggle,
    MatIcon,
    MatDialogContent,
    ReactiveFormsModule,
    MatInput,
    MatButton,
    MatDialogTitle,
    ClipboardModule,
    MatDialogClose,
    FormsModule,
    MatIconButton,
    MatTooltip,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatTooltipModule,
  ],
})
export class SecretsCertificatesModule {}
