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
import { Component, ElementRef, EventEmitter, Inject, Input, OnInit, Output, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { UntypedFormControl } from '@angular/forms';
import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { difference, find, map, remove } from 'lodash';
import { duration } from 'moment';

import { TimeConverterService } from '../../../services/time-converter.service';

@Component({
  selector: 'app-scopes-settings',
  templateUrl: './scopes.component.html',
  styleUrls: ['./scopes.component.scss'],
  standalone: false,
})
export class ScopesComponent implements OnInit {
  @Input() oauthSettings: any;
  @Input() scopes: any[] = [];
  @Input() readonly = false;
  @Input() context: 'Application' | 'McpServer' = 'Application';

  @Output() settingsChange = new EventEmitter<any>();
  @Output() formChanged = new EventEmitter<boolean>();

  private defaultScopes: string[];
  selectedScopes: any[];
  selectedScopeApprovals: any;

  constructor(
    private dialog: MatDialog,
    private timeConverterService: TimeConverterService,
  ) {}

  ngOnInit() {
    this.oauthSettings = this.oauthSettings || {};
    this.oauthSettings.scopes = this.oauthSettings.scopes || [];
    this.initScopes();
  }

  private initScopes() {
    // Merge with existing scope
    this.selectedScopes = [];
    this.defaultScopes = [];
    this.selectedScopeApprovals = {};
    if (this.oauthSettings.scopeSettings) {
      this.oauthSettings.scopeSettings.forEach((scopeSettings) => {
        const definedScope = find(this.scopes, { key: scopeSettings.scope });
        if (definedScope) {
          this.selectedScopes.push(definedScope);
          if (scopeSettings.defaultScope) {
            this.defaultScopes.push(scopeSettings.scope);
          }
          if (scopeSettings.scopeApproval) {
            this.selectedScopeApprovals[scopeSettings.scope] = {
              expiresIn: this.timeConverterService.getTime(scopeSettings.scopeApproval),
              unitTime: this.timeConverterService.getUnitTime(scopeSettings.scopeApproval),
            };
          }
        }
      });
    }

    this.scopes = difference(this.scopes, this.selectedScopes);
  }

  add(event) {
    event.preventDefault();
    const applicationScopes = map(this.selectedScopes, (scope) => scope.key);
    const dialogRef = this.dialog.open(AddScopeComponent, {
      minWidth: '600px',
      data: { domainScopes: this.scopes, applicationScopes: applicationScopes },
    });
    dialogRef.afterClosed().subscribe((scopes) => {
      if (scopes) {
        scopes.forEach((scope) => {
          const selectedScope = find(this.scopes, { key: scope });
          if (selectedScope) {
            this.selectedScopes.push(selectedScope);
          }
        });
        this.selectedScopes = [...this.selectedScopes];
        this.modelChanged();
      }
    });
  }

  removeScope(scopeKey, event) {
    event.preventDefault();
    this.scopes = this.scopes.concat(
      remove(this.selectedScopes, function (selectPermission) {
        return selectPermission.key === scopeKey;
      }),
    );
    this.selectedScopes = [...this.selectedScopes];
    delete this.selectedScopeApprovals[scopeKey];
    this.modelChanged();
  }

  enhanceScopesWithUserPermissions(event) {
    this.oauthSettings.enhanceScopesWithUserPermissions = event.checked;
    this.modelChanged();
  }

  isScopesEnhanceWithUserPermissions() {
    return this.oauthSettings.enhanceScopesWithUserPermissions;
  }

  scopeApprovalExists(scopeKey) {
    // eslint-disable-next-line no-prototype-builtins
    return this.selectedScopeApprovals.hasOwnProperty(scopeKey);
  }

  removeScopeApproval(event, scopeKey, expiresInInput, unitTimeInput) {
    event.preventDefault();
    delete this.selectedScopeApprovals[scopeKey];
    expiresInInput.value = '';
    unitTimeInput.value = '';
    this.modelChanged();
  }

  toggleDefaultScope(event, scope) {
    if (event.checked) {
      this.defaultScopes.push(scope);
    } else {
      this.defaultScopes.splice(this.defaultScopes.indexOf(scope), 1);
    }
    this.modelChanged();
  }

  isDefaultScope(scope) {
    return this.defaultScopes.indexOf(scope) !== -1;
  }

  onExpiresInEvent(event, scope) {
    this.selectedScopeApprovals[scope] = this.selectedScopeApprovals[scope] || {};
    this.selectedScopeApprovals[scope].expiresIn = event.target.value;
    this.modelChanged();
  }

  onUnitTimeEvent(event, scope) {
    this.selectedScopeApprovals[scope] = this.selectedScopeApprovals[scope] || {};
    this.selectedScopeApprovals[scope].unitTime = event.value;
    this.modelChanged();
  }

  displayExpiresIn(scopeKey) {
    return this.selectedScopeApprovals[scopeKey] ? this.selectedScopeApprovals[scopeKey].expiresIn : null;
  }

  displayUnitTime(scopeKey) {
    return this.selectedScopeApprovals[scopeKey] ? this.selectedScopeApprovals[scopeKey].unitTime : null;
  }

  modelChanged() {
    this.formChanged.emit(true);

    // logic from patch()
    const updatedSettings = { ...this.oauthSettings };
    updatedSettings.scopeSettings = [];
    this.selectedScopes.forEach((s) => {
      const setting = {
        scope: s.key,
        defaultScope: this.defaultScopes.indexOf(s.key) !== -1,
      };

      const approval = this.selectedScopeApprovals[s.key];
      if (approval) {
        setting['scopeApproval'] = duration(approval.expiresIn, approval.unitTime).asSeconds();
      }

      updatedSettings.scopeSettings.push(setting);
    });
    this.settingsChange.emit(updatedSettings);
  }
}

@Component({
  selector: 'add-scope',
  templateUrl: './add/add-scope.component.html',
  standalone: false,
})
export class AddScopeComponent {
  @ViewChild('scopeInput', { static: true }) scopeInput: ElementRef<HTMLInputElement>;
  @ViewChild(MatAutocompleteTrigger, { static: true }) trigger;
  scopeCtrl = new UntypedFormControl();
  filteredScopes: any[];
  selectedScopes: any[] = [];
  removable = true;
  addOnBlur = true;
  separatorKeysCodes: number[] = [ENTER, COMMA];
  private applicationScopes: string[] = [];

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    public dialogRef: MatDialogRef<AddScopeComponent>,
  ) {
    this.applicationScopes = data.applicationScopes || [];
    this.filteredScopes = this.loadFilteredScopes();
    this.scopeCtrl.valueChanges.subscribe((searchTerm) => {
      if (typeof searchTerm === 'string' || searchTerm instanceof String) {
        this.filteredScopes = data.domainScopes.filter((domainScope) => {
          return (
            domainScope.key.includes(searchTerm) &&
            this.selectedScopes.indexOf(domainScope.key) === -1 &&
            this.applicationScopes.indexOf(domainScope.key) === -1
          );
        });
      }
    });
  }

  onSelectionChanged(event) {
    this.selectedScopes.push(event.option.value);
    this.scopeInput.nativeElement.value = '';
    this.scopeInput.nativeElement.blur();
    this.scopeCtrl.setValue(null);
    this.filteredScopes = this.loadFilteredScopes();
  }

  remove(scope: string): void {
    const index = this.selectedScopes.indexOf(scope);

    if (index >= 0) {
      this.selectedScopes.splice(index, 1);
    }
  }

  private loadFilteredScopes(): any[] {
    return this.data.domainScopes.filter((domainScope) => {
      return this.selectedScopes.indexOf(domainScope.key) === -1 && this.applicationScopes.indexOf(domainScope.key) === -1;
    });
  }
}
