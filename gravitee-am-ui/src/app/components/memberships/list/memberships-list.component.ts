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
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { MembershipsDialogComponent, MembershipsDialogData } from '../dialog/memberships-dialog.component';

@Component({
  selector: 'app-memberships-list',
  templateUrl: './memberships-list.component.html',
  styleUrls: ['./memberships-list.component.scss'],
  standalone: false,
})
export class MembershipsListComponent {
  @Input() members: any;
  @Input() editable = false;
  @Input() userContextLabel: string;
  @Input() dialogData: MembershipsDialogData;
  @Output() dialogClosed = new EventEmitter<void>();

  constructor(private dialog: MatDialog) {}

  openDialog(): void {
    if (!this.dialogData) {
      return;
    }

    const dialogRef = this.dialog.open(MembershipsDialogComponent, {
      panelClass: 'no-padding-dialog-container',
      minWidth: '100vw',
      height: '100vh',
      data: this.dialogData,
    });

    dialogRef.afterClosed().subscribe(() => {
      this.dialogClosed.emit();
    });
  }
}
