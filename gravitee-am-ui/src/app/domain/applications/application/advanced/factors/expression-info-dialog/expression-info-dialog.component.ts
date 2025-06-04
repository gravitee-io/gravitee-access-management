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

@Component({
  selector: 'expression-mfa-dialog',
  templateUrl: './expression-info-dialog.component.html',
  standalone: false,
})
export class ExpressionInfoDialogComponent {
  private readonly DEFAULT_INFO = `{#request.params['scope'][0] == 'write'}
{#request.params['audience'][0] == 'https://myapi.com'}
{#context.attributes['geoip']['country_iso_code'] == 'US'}
{#context.attributes['geoip']['country_name'] == 'United States'}
{#context.attributes['geoip']['continent_name'] == 'North America'}
{#context.attributes['geoip']['region_name'] == 'Washington'}
{#context.attributes['geoip']['city_name'] == 'Seattle'}
{#context.attributes['geoip']['timezone'] == 'America/Los_Angeles'}
{#context.attributes['geoip']['lon'] == 47.54}
{#context.attributes['geoip']['lat'] == -122.3032}
{#context.attributes['login_attempts'] < 3}
`;
  constructor(
    public dialogRef: MatDialogRef<ExpressionInfoDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any,
  ) {}

  getInfo(): string {
    return this.data?.info ? this.data.info : this.DEFAULT_INFO;
  }
}
