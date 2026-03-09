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
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

import { FormService } from '../../../../../../services/form.service';
import { SnackbarService } from '../../../../../../services/snackbar.service';

@Component({
  selector: 'app-create-custom-form-dialog',
  templateUrl: './create-custom-form-dialog.component.html',
  styleUrls: ['./create-custom-form-dialog.component.scss'],
  standalone: false,
})
export class CreateCustomFormDialogComponent {
  formGroup: FormGroup;
  isSubmitting = false;

  constructor(
    public dialogRef: MatDialogRef<CreateCustomFormDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
    private formBuilder: FormBuilder,
    private formService: FormService,
    private snackBarService: SnackbarService,
  ) {
    this.formGroup = this.formBuilder.group({
      template: ['', [Validators.required, Validators.minLength(3)]],
      enabled: [true],
      content: ['', Validators.required],
    });
  }

  submit() {
    if (this.formGroup.valid) {
      this.isSubmitting = true;
      const newForm = {
        ...this.formGroup.value,
      };

      this.formService.create(this.data.domainId, this.data.applicationId, newForm, false).subscribe(
        (response) => {
          this.snackBarService.open('Custom form created successfully');
          this.dialogRef.close(response);
        },
        (error: unknown) => {
          const errorMessage = error instanceof Error ? error.message : String(error);
          this.snackBarService.open('Error creating custom form: ' + errorMessage);
          this.isSubmitting = false;
        },
      );
    }
  }

  cancel() {
    this.dialogRef.close();
  }
}
