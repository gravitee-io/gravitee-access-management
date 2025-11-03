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
import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, ViewChild } from '@angular/core';
import { Subject, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, switchMap, tap } from 'rxjs/operators';

import { McpServersService, McpServer } from '../../mcp-servers.service';
import { Page, Sort } from '../../../../services/api.model';

@Component({
  selector: 'app-mcp-server-settings',
  templateUrl: './mcp-server-settings.component.html',
  standalone: false,
})
export class McpServerSettingsComponent implements OnInit, OnChanges, OnDestroy {
  @Input() domain: any;
  @Input() name: string;
  @Input() resourceIdentifier: string;
  @Input() description: string;
  @Input() readonly = false;
  @Input() existingResourceId?: string; // For duplicate name checking in edit mode
  @Input() domainId: string; // For duplicate name checking
  // eslint-disable-next-line @angular-eslint/no-output-on-prefix
  @Output() onSettingsChange = new EventEmitter<{ name: string; resourceIdentifier: string; description: string }>();
  @ViewChild('settingsForm', { static: true }) form: any;

  // Name validation pattern: letters, numbers, hyphens, and underscores only
  // Escape hyphen in character class to avoid regex parsing issues
  readonly NAME_PATTERN = '^[a-zA-Z0-9_\\-]+$';

  nameDuplicateError = false; // Exposed for parent component to check
  private readonly nameCheckSubject = new Subject<string>();
  private initialName: string; // Store initial name for comparison in edit mode

  constructor(private readonly mcpServersService: McpServersService) {}

  ngOnInit(): void {
    // Store initial name for comparison in edit mode
    this.initialName = this.name || '';

    // Set up duplicate name checking
    this.nameCheckSubject
      .pipe(
        debounceTime(500),
        distinctUntilChanged(),
        switchMap((name) => {
          // In edit mode, if name matches initial value, no duplicate check needed
          if (!name || (this.existingResourceId && name === this.initialName)) {
            this.nameDuplicateError = false;
            // Clear duplicate error when name matches initial value or is empty
            this.clearDuplicateError();
            return of(null);
          }
          if (!this.domainId) {
            return of(null);
          }
          const sort: Sort = { prop: 'name', dir: 'asc' };
          return this.mcpServersService.findByDomain(this.domainId, 0, 100, sort).pipe(
            tap((page: Page<McpServer>) => {
              const duplicate = page.data?.some((server) => server.name === name && server.id !== this.existingResourceId);
              this.nameDuplicateError = duplicate || false;
              // Mark form control as invalid if duplicate name found
              if (this.form?.controls?.name) {
                if (duplicate) {
                  // Mark as touched so error message shows immediately
                  if (!this.form.controls.name.touched) {
                    this.form.controls.name.markAsTouched();
                  }
                  // Set duplicate error, preserving other errors
                  const existingErrors = this.form.controls.name.errors || {};
                  this.form.controls.name.setErrors({ ...existingErrors, duplicate: true });
                } else {
                  // Remove duplicate error if no longer duplicate
                  this.clearDuplicateError();
                }
              }
            }),
            catchError(() => {
              this.nameDuplicateError = false;
              this.clearDuplicateError();
              return of(null);
            }),
          );
        }),
      )
      .subscribe();

    // Check for duplicates on initial load if name is already set
    if (this.name && this.domainId && !this.existingResourceId) {
      // In create mode, check immediately if name is already set
      setTimeout(() => {
        if (this.name && this.domainId) {
          this.nameCheckSubject.next(this.name);
        }
      }, 100);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.name) {
      // Update initial name if this is the first change (component initialization)
      if (!this.initialName && changes.name.currentValue) {
        this.initialName = changes.name.currentValue;
      }
      // Trigger duplicate check when name changes
      if (this.form?.controls?.name) {
        this.onNameChange();
      }
    }
  }

  ngOnDestroy(): void {
    this.nameCheckSubject.complete();
  }

  onNameChange(): void {
    // Always check for duplicates if we have a domainId (for both create and edit modes)
    if (this.name && this.domainId) {
      this.nameCheckSubject.next(this.name);
    } else if (!this.name) {
      // Clear duplicate error if name is empty
      this.nameDuplicateError = false;
      this.clearDuplicateError();
    }
    this.emitChange();
  }

  onResourceIdentifierChange(): void {
    this.emitChange();
  }

  onDescriptionChange(): void {
    this.emitChange();
  }

  private emitChange(): void {
    this.onSettingsChange.emit({
      name: this.name || '',
      resourceIdentifier: this.resourceIdentifier || '',
      description: this.description || '',
    });
  }

  isFormValid(): boolean {
    return this.form?.valid && !this.nameDuplicateError;
  }

  private clearDuplicateError(): void {
    if (this.form?.controls?.name?.errors?.duplicate) {
      const errors = { ...this.form.controls.name.errors };
      delete errors.duplicate;
      const hasErrors = Object.keys(errors).length > 0;
      this.form.controls.name.setErrors(hasErrors ? errors : null);
    }
  }
}
