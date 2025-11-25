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
import { Component, Input, OnInit } from '@angular/core';
import { JsonSchemaFormService } from '@ajsf/core';
import { MatSelectChange } from '@angular/material/select';
import { HttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';

import { AppConfig } from '../../../config/app.config';

@Component({
  selector: 'material-multiselect-widget',
  template: `
    <div class="mat-input-wrapper mat-form-field-wrapper">
      <mat-card appearance="outlined" class="mat-multiselect">
        <mat-form-field appearance="outline" floatLabel="always">
          <mat-label>{{ options.title }}</mat-label>

          <mat-select placeholder="Click to select" [value]="selected" multiple (selectionChange)="onSelectionChange($event)">
            <mat-select-trigger>Click to select</mat-select-trigger>

            <mat-form-field>
              <input matInput type="text" placeholder="Search" (input)="onSearch($event)" />
              <mat-icon matPrefix>search</mat-icon>
            </mat-form-field>

            <mat-checkbox
              class="select-all-option no-hover mat-primary"
              (click)="$event.stopPropagation(); toggleSelectVisible()"
              [disabled]="visibleItems.length === 0"
              [checked]="selectAllState === 'checked'"
              [indeterminate]="selectAllState === 'indeterminate'"
            >
              {{ selectAllLabel }}
            </mat-checkbox>

            <mat-option *ngFor="let item of allItems || []" [value]="item" [class.hide]="!visibleItems.includes(item)">
              <span>{{ item }}</span>
            </mat-option>
          </mat-select>
          <mat-hint>{{ options.description }}</mat-hint>
        </mat-form-field>

        <mat-list *ngIf="selected.length">
          <mat-list-item *ngFor="let item of selected">
            <div class="list-row">
              <span class="label">{{ item }}</span>
              <button mat-icon-button (click)="onUnselect(item)">
                <mat-icon>clear</mat-icon>
              </button>
            </div>
          </mat-list-item>
        </mat-list>
        <mat-card-content *ngIf="!selected.length && this.options.onEmptySelectionMessage">
          {{ this.options.onEmptySelectionMessage }}
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .mat-multiselect {
      padding: 10px;
    }
    .no-hover:hover {
      background: none !important;
    }
    .select-all-option {
      padding-left: 5px;
    }
    .hide {
      display: none;
    }
    .list-row {
      width: 100%;
      display: flex;
      align-items: center;
    }
    .label {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `,
  standalone: false,
})
export class MaterialMultiselectComponent implements OnInit {
  @Input() layoutNode: any;
  @Input() controlName: string;

  options: any;
  searchText = '';
  allItems: string[] = [];
  visibleItems: string[] = [];
  selectAllLabel = 'Select all';
  selectAllState: 'checked' | 'unchecked' | 'indeterminate' = 'unchecked';

  private baseURL = AppConfig.settings.baseURL;

  private _selected: string[] = [];

  get selected(): string[] {
    return this._selected;
  }

  set selected(value: string[]) {
    this._selected = value || [];
    const updatedValues = this._selected.map((t: string) => ({
      name: t,
      value: t,
      checked: true,
    }));

    this.jsf.updateArrayCheckboxList(this, updatedValues);
    this.refreshOptions();
  }

  constructor(
    private jsf: JsonSchemaFormService,
    private http: HttpClient,
  ) {}

  ngOnInit() {
    this.options = this.layoutNode.options;
    this.jsf.initializeControl(this);
    this.selected = this.jsf.data[this.controlName] || [];

    if (this.options.itemsDictionaryEndpoint) {
      const url = this.baseURL + this.options.itemsDictionaryEndpoint;
      this.http
        .get<any>(url)
        .pipe(take(1))
        .subscribe((data) => {
          this.allItems = data;
          this.refreshOptions();
        });
    } else {
      this.allItems = this.options.enum;
      this.refreshOptions();
    }
  }

  onSearch(event: Event) {
    this.searchText = (event.target as HTMLInputElement).value;
    this.refreshOptions();
  }

  onUnselect(item: string) {
    this.selected = this.selected.filter((i) => i !== item);
  }

  onSelectionChange(event: MatSelectChange<string[]>) {
    this.selected = event.value;
  }

  toggleSelectVisible() {
    if (this.visibleItems.length === 0) return;

    const selectedVisible = this.visibleItems.filter((i) => this.selected.includes(i));

    const allVisibleSelected = selectedVisible.length === this.visibleItems.length;

    if (allVisibleSelected) {
      this.selected = this.selected.filter((i) => !this.visibleItems.includes(i));
    } else {
      const merged = new Set([...this.selected, ...this.visibleItems]);
      this.selected = Array.from(merged);
    }
  }

  private refreshOptions() {
    const query = this.searchText.toLowerCase();

    this.visibleItems = this.allItems.filter((item) => item.toLowerCase().includes(query));

    this.selectAllLabel = query ? 'Select visible' : 'Select all';

    if (this.visibleItems.length === 0) {
      this.selectAllState = 'unchecked';
      return;
    }

    const selectedVisible = this.visibleItems.filter((i) => this.selected.includes(i));

    if (selectedVisible.length === 0) {
      this.selectAllState = 'unchecked';
    } else if (selectedVisible.length === this.visibleItems.length) {
      this.selectAllState = 'checked';
    } else {
      this.selectAllState = 'indeterminate';
    }
  }
}
