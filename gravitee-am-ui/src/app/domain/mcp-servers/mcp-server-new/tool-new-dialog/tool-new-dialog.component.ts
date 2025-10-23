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
import { Component, ElementRef, Inject, Injectable, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { UntypedFormControl } from '@angular/forms';
import { COMMA, ENTER } from '@angular/cdk/keycodes';

interface NewTool {
  name?: string;
  description?: string;
  scopes?: string[];
}

export interface DialogResult extends NewTool {
  cancel: boolean;
}

export interface DialogData {
  scopes: {
    key: string;
    name: string;
    description: string;
  }[];
}

export type DialogCallback = (data: DialogResult) => void;

@Component({
  templateUrl: './tool-new-dialog.component.html',
  styleUrl: './tool-new-dialog.component.scss',

  standalone: false,
})
export class DomainNewMcpServerToolDialogComponent {
  @ViewChild('scopeInput', { static: true }) scopeInput: ElementRef<HTMLInputElement>;
  matChipInputSeparatorKeyCodes = [ENTER, COMMA];
  newTool: NewTool = {
    scopes: [],
  };
  filteredScopes: any[];
  scopeCtrl = new UntypedFormControl();

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: DialogData,
    public dialogRef: MatDialogRef<DomainNewMcpServerToolDialogComponent, NewTool>,
  ) {
    this.scopeCtrl.valueChanges.subscribe((searchTerm: string) => {
      if (typeof searchTerm === 'string') {
        this.filteredScopes = data.scopes.filter((scope) => {
          return scope.key.includes(searchTerm) && this.newTool.scopes.indexOf(scope.key) === -1;
        });
      }
    });
  }

  accept(): void {
    this.dialogRef.close(this.newTool);
  }

  close(): void {
    this.dialogRef.close();
  }

  remove(scope: string): void {
    const index = this.newTool.scopes.indexOf(scope);
    if (index !== -1) {
      this.newTool.scopes.splice(index, 1);
    }
  }

  onSelectionChanged(event): void {
    this.newTool.scopes.push(event.option.value);
    this.scopeInput.nativeElement.value = '';
    this.scopeInput.nativeElement.blur();
    this.scopeCtrl.setValue(null);
    this.filteredScopes = this.loadFilteredScopes();
  }

  private loadFilteredScopes(): any[] {
    return this.data.scopes.filter((domainScope) => {
      return this.newTool.scopes.indexOf(domainScope.key) === -1;
    });
  }
}

@Injectable()
export class DomainNewMcpServerToolDialogFactory {
  constructor(private dialog: MatDialog) {}

  public openDialog(data: DialogData, callback: DialogCallback): void {
    const cfg = {
      width: '540px',
      data: data,
    };
    const dialogRef = this.dialog.open(DomainNewMcpServerToolDialogComponent, cfg);

    dialogRef.afterClosed().subscribe((result?: NewTool) =>
      callback({
        cancel: result?.name === undefined,
        name: result?.name,
        description: result?.description,
        scopes: result?.scopes,
      }),
    );
  }
}
