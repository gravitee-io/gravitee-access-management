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
import {Component, ViewChild} from '@angular/core';
import {MatStepper} from '@angular/material';
import {ActivatedRoute, Router} from '@angular/router';
import {CertificateService} from '../../../../services/certificate.service';
import {SnackbarService} from '../../../../services/snackbar.service';

@Component({
  selector: 'app-certificate-creation',
  templateUrl: './certificate-creation.component.html',
  styleUrls: ['./certificate-creation.component.scss']
})
export class CertificateCreationComponent {
  public certificate: any = {};
  private domainId: string;
  configurationIsValid = false;
  @ViewChild ('stepper') stepper: MatStepper;

  constructor(private certificateService: CertificateService,
              private snackbarService: SnackbarService,
              private router: Router,
              private route: ActivatedRoute) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
  }

  create() {
    this.certificate.configuration = JSON.stringify(this.certificate.configuration);
    this.certificateService.create(this.domainId, this.certificate).subscribe(data => {
      this.snackbarService.open('Certificate ' + data.name + ' created');
      this.router.navigate(['..', data.id], { relativeTo: this.route });
    });
  }

  stepperValid() {
    return this.certificate && this.certificate.name && this.configurationIsValid;
  }
}
