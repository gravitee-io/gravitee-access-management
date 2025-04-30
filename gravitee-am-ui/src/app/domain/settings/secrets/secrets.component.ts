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
  formChanged = false;
  defaultExpiryTime = false;
  secretSettings: any;
  humanTime: any;

  constructor(
    private domainService: DomainService,
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private timeConverterService: TimeConverterService,
  ) {}

  ngOnInit(): void {
    this.domain = this.route.snapshot.data['domain'];
    this.secretSettings = this.domain.secretSettings ? this.domain.secretSettings : { enabled: false };
    this.defaultExpiryTime = !!this.secretSettings.expiryTimeSeconds;
    const time = this.secretSettings?.expiryTimeSeconds ? this.secretSettings.expiryTimeSeconds : 7776000;
    this.humanTime = {
      expirationTime: this.timeConverterService.getTime(time, 'seconds'),
      expirationUnit: this.timeConverterService.getUnitTime(time, 'seconds'),
    };
  }

  updateFormState(): void {
    this.formChanged = true;
  }

  save(): void {
    const toPatch = {
      enabled: this.secretSettings.enabled,
      expiryTimeSeconds: this.defaultExpiryTime ? this.humanTimeToSeconds() : 0,
    };
    this.domainService.patch(this.domain.id, { secretSettings: toPatch }).subscribe(() => {
      this.snackbarService.open('Secrets configuration updated');
    });
    this.formChanged = false;
  }

  onUnitChange($event: any): void {
    this.humanTime.expirationUnit = $event.value;
    this.formChanged = true;
  }

  onTimeChange($event: any): void {
    this.humanTime.expirationTime = Math.abs($event.target.value);
    this.formChanged = true;
  }

  private humanTimeToSeconds(): number {
    return duration(this.humanTime.expirationTime, this.humanTime.expirationUnit).asSeconds();
  }
}
