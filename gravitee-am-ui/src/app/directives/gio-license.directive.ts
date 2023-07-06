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
import {Directive, ElementRef, Input, OnDestroy, OnInit} from '@angular/core';
import {tap} from 'rxjs/operators';
import {action} from '@storybook/addon-actions';
import {MatDialog} from '@angular/material/dialog';
import {Subject} from 'rxjs';


import {GioEeUnlockDialogComponent, GioEeUnlockDialogData} from '../components/gio-ee-unlock-dialog/gio-ee-unlock-dialog.component';
import {GioLicenseService} from '../services/gio-license.service';

@Directive({
  selector: '[gioLicense]',
})
export class GioLicenseDirective implements OnInit, OnDestroy {
  @Input()
  public gioLicense: any = {};

  private featureMoreInformation: any;
  private unsubscribe$: Subject<boolean> = new Subject<boolean>();
  private onClick = this.click.bind(this);

  constructor(private readonly licenseService: GioLicenseService, private readonly matDialog: MatDialog, private elRef: ElementRef) {}

  ngOnInit(): void {
    if (!this.gioLicense.deployed) {
      this.elRef.nativeElement.removeEventListener('click', this.onClick, true);
      this.featureMoreInformation = this.licenseService.getFeatureMoreInformation(this.gioLicense.feature);
      this.elRef.nativeElement.addEventListener('click', this.onClick, true);
    }
  }

  ngOnDestroy(): void {
    this.elRef.nativeElement.removeEventListener('click', this.onClick, true);
    this.unsubscribe$.next(false);
    this.unsubscribe$.unsubscribe();
  }

  private click($event: PointerEvent) {
    $event.preventDefault();
    $event.stopPropagation();
    this.matDialog
      .open<GioEeUnlockDialogComponent, GioEeUnlockDialogData, boolean>(GioEeUnlockDialogComponent, {
        data: {
          featureMoreInformation: this.featureMoreInformation,
        },
        role: 'alertdialog',
        id: 'gioLicenseDialog',
      })
      .afterClosed()
      .pipe(
        tap((confirmed) => {
          action('confirmed?')(confirmed);
        }),
      )
      .subscribe();
    return false;
  }
}
