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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { duration } from 'moment/moment';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { DomainService } from '../../../services/domain.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { TimeConverterService } from '../../../services/time-converter.service';

@Component({
  selector: 'app-domain-secrets',
  templateUrl: './secrets.component.html',
  styleUrl: './secrets.component.scss',
})
export class DomainSettingsSecretsComponent implements OnInit {
  domainId: string;
  domain: any = {};
  secretSettings: any;
  form: FormGroup;

  constructor(
    private domainService: DomainService,
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private timeConverterService: TimeConverterService,
  ) {}

  ngOnInit(): void {
    this.domain = this.route.snapshot.data['domain'];
    this.secretSettings = this.domain.secretExpirationSettings ? this.domain.secretExpirationSettings : { enabled: false };
    const time = this.secretSettings?.expiryTimeSeconds ? this.secretSettings.expiryTimeSeconds : 7776000;
    const expirationTime = this.timeConverterService.getTime(time, 'seconds');
    const expirationUnit = this.timeConverterService.getUnitTime(time, 'seconds');
    this.form = new FormGroup({
      enabled: new FormControl(this.secretSettings.enabled),
      expirationTime: new FormControl(expirationTime),
      expirationUnit: new FormControl(expirationUnit),
    });

    this.form.get('enabled')?.valueChanges.subscribe((enabled) => {
      if (enabled) {
        this.form.get('expirationTime')?.setValidators([Validators.required, Validators.min(1)]);
        this.form.get('expirationUnit')?.setValidators([Validators.required]);
      } else {
        this.form.get('expirationTime')?.clearValidators();
        this.form.get('expirationUnit')?.clearValidators();
      }

      this.form.get('expirationTime')?.updateValueAndValidity();
      this.form.get('expirationUnit')?.updateValueAndValidity();
    });

    this.form.valueChanges.subscribe(() => {
      this.form.markAsDirty();
    });

    this.form.get('enabled')?.updateValueAndValidity();
  }

  save(): void {
    if (this.form.invalid) return;
    const toPatch = {
      enabled: this.form.value.enabled,
      expiryTimeSeconds: duration(this.form.value.expirationTime, this.form.value.expirationUnit).asSeconds(),
    };
    this.domainService.patch(this.domain.id, { secretSettings: toPatch }).subscribe(() => {
      this.snackbarService.open('Secrets configuration updated');
      this.form.markAsPristine();
    });
  }
}
