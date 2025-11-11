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
import { Component, Inject, ViewChild, ElementRef, OnInit, OnDestroy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-certificate-enrollment-dialog',
  templateUrl: './certificate-enrollment-dialog.component.html',
  styleUrls: ['./certificate-enrollment-dialog.component.scss'],
  standalone: false,
})
export class CertificateEnrollmentDialogComponent implements OnInit, OnDestroy {
  enrollmentForm: UntypedFormGroup;
  certificateError: string = '';
  loading: boolean = false;
  private certificateValueSubscription?: Subscription;

  @ViewChild('fileInput', { static: false }) fileInput: ElementRef<HTMLInputElement>;

  constructor(
    @Inject(MAT_DIALOG_DATA) public readonly data: { domainId: string; userId: string },
    public readonly dialogRef: MatDialogRef<CertificateEnrollmentDialogComponent>,
  ) {
    this.enrollmentForm = new UntypedFormGroup({
      certificatePem: new UntypedFormControl('', [Validators.required]),
      deviceName: new UntypedFormControl(''),
    });
  }

  ngOnInit(): void {
    // Subscribe to form control value changes to handle paste/input
    this.certificateValueSubscription = this.enrollmentForm
      .get('certificatePem')
      ?.valueChanges.pipe(debounceTime(100))
      .subscribe(() => {
        this.enrollmentForm.get('certificatePem')?.markAsTouched();
        this.validateCertificate();
      });
  }

  ngOnDestroy(): void {
    if (this.certificateValueSubscription) {
      this.certificateValueSubscription.unsubscribe();
    }
  }

  async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];
      try {
        const content = await file.text();
        this.certificateError = '';
        this.enrollmentForm.patchValue({ certificatePem: content });
        this.validateCertificate();
      } catch {
        this.certificateError = 'Failed to read certificate file';
        this.enrollmentForm.patchValue({ certificatePem: '' });
      }
    }
  }

  onCertificateInput(): void {
    // Mark the control as touched when user types/pastes
    // Validation will be triggered by valueChanges subscription
    this.enrollmentForm.get('certificatePem')?.markAsTouched();
  }

  onCertificatePaste(): void {
    // Handle paste event explicitly
    setTimeout(() => {
      this.enrollmentForm.get('certificatePem')?.markAsTouched();
      this.validateCertificate();
    }, 0);
  }

  validateCertificate(): void {
    const certificatePem = this.enrollmentForm.get('certificatePem')?.value || '';
    
    if (!certificatePem) {
      this.certificateError = 'Certificate is required';
      return;
    }

    // Basic PEM format validation
    const pemPattern = /-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/;
    if (!pemPattern.test(certificatePem)) {
      this.certificateError = 'Invalid certificate format. Please provide a valid PEM certificate.';
      return;
    }

    // Check for minimum required content
    const base64Content = certificatePem
      .replaceAll('-----BEGIN CERTIFICATE-----', '')
      .replaceAll('-----END CERTIFICATE-----', '')
      .replace(/\s/g, '');

    if (base64Content.length < 100) {
      this.certificateError = 'Certificate appears to be too short. Please provide a valid certificate.';
      return;
    }

    this.certificateError = '';
  }

  onSubmit(): void {
    if (this.enrollmentForm.valid && !this.certificateError) {
      this.loading = true;
      this.dialogRef.close(this.enrollmentForm.value);
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  triggerFileInput(): void {
    if (this.fileInput?.nativeElement) {
      this.fileInput.nativeElement.click();
    }
  }
}
