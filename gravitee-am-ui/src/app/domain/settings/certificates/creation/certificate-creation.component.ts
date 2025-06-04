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
import { AfterViewChecked, ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { CertificateService } from '../../../../services/certificate.service';
import { SnackbarService } from '../../../../services/snackbar.service';

@Component({
  selector: 'app-certificate-creation',
  templateUrl: './certificate-creation.component.html',
  styleUrls: ['./certificate-creation.component.scss'],
  standalone: false,
})
export class CertificateCreationComponent implements OnInit, AfterViewChecked {
  public certificate: any = {};
  private domainId: string;
  configurationIsValid = false;
  submissionPending = false;
  @ViewChild('stepper', { static: true }) stepper: MatStepper;

  constructor(
    private certificateService: CertificateService,
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
  }

  create(): void {
    this.certificate.configuration = JSON.stringify(this.certificate.configuration);
    this.submissionPending = true;
    this.certificateService
      .create(this.domainId, this.certificate)
      .pipe(finalize(() => (this.submissionPending = false)))
      .subscribe(
        (data) => {
          this.snackbarService.open('The certificate ' + data.name + ' has been successfully created.');
          this.router.navigate(['..', data.id], { relativeTo: this.route });
        },
        (_: unknown) => {
          this.certificate = { ...this.certificate, name: '', configuration: null };
        },
      );
  }

  stepperValid() {
    return this.certificate?.name && this.configurationIsValid;
  }

  ngAfterViewChecked(): void {
    this.changeDetector.detectChanges();
  }
}
