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
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';

export interface GioEeUnlockDialogData {
  featureMoreInformation: any;
  trialURL: string;
}

@Component({
  selector: 'app-gio-ee-unlock-dialog',
  templateUrl: './gio-ee-unlock-dialog.component.html',
  styleUrls: ['./gio-ee-unlock-dialog.component.scss'],
})
export class GioEeUnlockDialogComponent {
  public featureMoreInformation: any;
  public trialURL: string;

  constructor(private readonly dialogRef: MatDialogRef<GioEeUnlockDialogData>, @Inject(MAT_DIALOG_DATA) dialogData: GioEeUnlockDialogData) {
    this.featureMoreInformation = dialogData?.featureMoreInformation;
    this.trialURL = dialogData?.trialURL;
  }

  onClose() {
    this.dialogRef.close();
  }
}
