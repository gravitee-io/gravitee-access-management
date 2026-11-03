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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { duration, unitOfTime } from 'moment';

import { AuthService } from '../../../../services/auth.service';

import { AddAuthorizationDetailTypeComponent, AddAuthorizationDetailTypeData } from './add-authorization-detail-type.component';

export const EXPIRY_UNITS: unitOfTime.Base[] = ['seconds', 'minutes', 'hours', 'days', 'weeks', 'months', 'years'];

export interface AuthorizationDetailTypeRow {
  type: string;
  name?: string;
  /** The domain type's own consent expiry, in seconds. */
  typeExpiresIn?: number;
  expiresIn?: number;
  unitTime?: unitOfTime.Base;
}

/**
 * Expresses seconds in the largest unit that divides them exactly.
 */
export function toDuration(seconds: number): { value: number; unit: unitOfTime.Base } {
  const unit = [...EXPIRY_UNITS].reverse().find((u) => seconds % duration(1, u).asSeconds() === 0) ?? 'seconds';
  return { value: seconds / duration(1, unit).asSeconds(), unit };
}

@Component({
  selector: 'app-authorization-details-settings',
  templateUrl: './authorization-details.component.html',
  styleUrls: ['./authorization-details.component.scss'],
  standalone: false,
})
export class AuthorizationDetailsComponent implements OnInit {
  @Input() oauthSettings: any;
  @Input() authorizationDetailTypes: any[] | null = [];
  @Input() domain: any;
  @Input() readonly = false;

  @Output() settingsChange = new EventEmitter<any>();
  @Output() formChanged = new EventEmitter<boolean>();

  readonly units = EXPIRY_UNITS;
  rows: AuthorizationDetailTypeRow[] = [];

  constructor(
    private dialog: MatDialog,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    const domainTypes = new Map((this.authorizationDetailTypes || []).map((t) => [t.type, t]));
    this.rows = (this.oauthSettings?.authorizationDetailTypes || []).map((settings) => {
      const row = this.newRow(domainTypes.get(settings.type) ?? { type: settings.type });
      if (settings.expiresIn != null) {
        const { value, unit } = toDuration(settings.expiresIn);
        row.expiresIn = value;
        row.unitTime = unit;
      }
      return row;
    });
  }

  add(event: Event) {
    event.preventDefault();
    const enabled = new Set(this.rows.map((row) => row.type));
    const data: AddAuthorizationDetailTypeData = {
      types: (this.authorizationDetailTypes || []).filter((t) => !enabled.has(t.type)),
      loadFailed: this.authorizationDetailTypes === null,
      domainHasTypes: this.authorizationDetailTypes?.length > 0,
      createLink: this.authService.hasPermissions(['domain_authorization_detail_type_create'])
        ? ['/environments', this.domain?.referenceId, 'domains', this.domain?.id, 'settings', 'authorization-detail-types']
        : null,
    };
    this.dialog
      .open(AddAuthorizationDetailTypeComponent, { minWidth: '600px', data })
      .afterClosed()
      .subscribe((types: any[]) => {
        if (types?.length) {
          this.rows = [...this.rows, ...types.map((t) => this.newRow(t))];
          this.modelChanged();
        }
      });
  }

  remove(row: AuthorizationDetailTypeRow, event: Event) {
    event.preventDefault();
    this.rows = this.rows.filter((r) => r !== row);
    this.modelChanged();
  }

  onExpiresIn(row: AuthorizationDetailTypeRow, event: Event) {
    const value = (event.target as HTMLInputElement).value;
    row.expiresIn = value === '' ? undefined : Number(value);
    if (row.expiresIn !== undefined && !row.unitTime) {
      row.unitTime = row.typeExpiresIn ? toDuration(row.typeExpiresIn).unit : 'seconds';
    }
    this.modelChanged();
  }

  onUnitTime(row: AuthorizationDetailTypeRow, unit: unitOfTime.Base) {
    row.unitTime = unit;
    this.modelChanged();
  }

  clearExpiry(row: AuthorizationDetailTypeRow, event: Event) {
    event.preventDefault();
    row.expiresIn = undefined;
    row.unitTime = undefined;
    this.modelChanged();
  }

  hasExpiry(row: AuthorizationDetailTypeRow): boolean {
    return row.expiresIn !== undefined || row.unitTime !== undefined;
  }

  isExpiryInvalid(row: AuthorizationDetailTypeRow): boolean {
    const seconds = this.expirySeconds(row);
    return seconds !== undefined && seconds < 1;
  }

  durationPlaceholder(row: AuthorizationDetailTypeRow): string {
    return row.typeExpiresIn ? String(toDuration(row.typeExpiresIn).value) : 'Duration';
  }

  unitPlaceholder(row: AuthorizationDetailTypeRow): string {
    return row.typeExpiresIn ? toDuration(row.typeExpiresIn).unit.toUpperCase() : 'Unit';
  }

  private expirySeconds(row: AuthorizationDetailTypeRow): number | undefined {
    if (row.expiresIn === undefined) {
      return undefined;
    }
    return Math.round(duration(row.expiresIn, row.unitTime ?? 'seconds').asSeconds());
  }

  private newRow(type: any): AuthorizationDetailTypeRow {
    return { type: type.type, name: type.name, typeExpiresIn: type.expiresIn ?? undefined };
  }

  private modelChanged() {
    this.formChanged.emit(true);
    this.settingsChange.emit({
      ...this.oauthSettings,
      authorizationDetailTypes: this.rows.map((row) => {
        const expiresIn = this.expirySeconds(row);
        return expiresIn === undefined ? { type: row.type } : { type: row.type, expiresIn };
      }),
    });
  }
}
