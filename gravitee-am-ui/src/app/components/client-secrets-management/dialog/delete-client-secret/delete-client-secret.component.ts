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
import { FormControl, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface DeleteClientSecretData {
  description: string;
}

@Component({
  selector: 'app-delete-client-secret',
  templateUrl: './delete-client-secret.component.html',
  styleUrl: '../../client-secrets-management.component.scss',
  standalone: false,
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
