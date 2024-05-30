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
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatStepper } from '@angular/material/stepper';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { GIO_DIALOG_WIDTH } from '@gravitee/ui-particles-angular';
import { map, switchMap, takeUntil, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';

import { ApplicationService } from '../../../services/application.service';
import {
  ApplicationClientSecretCopyDialogComponent,
  ApplicationClientSecretCopyDialogData,
} from '../client-secret/application-client-secret-copy-dialog.component';
import { SnackbarService } from '../../../services/snackbar.service';

@Component({
  selector: 'app-creation',
  templateUrl: './application-creation.component.html',
  styleUrls: ['./application-creation.component.scss'],
})
export class ApplicationCreationComponent implements OnInit {
  public application: any = {};
  private unsubscribe$: Subject<boolean> = new Subject<boolean>();
  @ViewChild('stepper', { static: true }) stepper: MatStepper;

  constructor(
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
    private router: Router,
    private route: ActivatedRoute,
    private readonly matDialog: MatDialog,
  ) {}

  ngOnInit() {
    this.application.domain = this.route.snapshot.parent.data['domain'].id;
  }

  create() {
    const app: any = {};
    app.name = this.application.name;
    app.type = this.application.type;
    app.description = this.application.description;
    app.clientId = this.application.clientId;
    app.clientSecret = this.application.clientSecret;
    app.redirectUris = this.application.redirectUri ? [this.application.redirectUri] : null;

    this.applicationService
      .create(this.application.domain, app)
      .pipe(
        switchMap((data) =>
          this.matDialog
            .open<ApplicationClientSecretCopyDialogComponent, ApplicationClientSecretCopyDialogData, void>(
              ApplicationClientSecretCopyDialogComponent,
              {
                width: GIO_DIALOG_WIDTH.MEDIUM,
                disableClose: true,
                data: {
                  secret: data.settings.oauth.clientSecret,
                  renew: false,
                },
                role: 'alertdialog',
                id: 'applicationClientSecretCopyDialog',
              },
            )
            .afterClosed()
            .pipe(
              tap(() => {
                this.snackbarService.open('Application ' + data.name + ' created');
              }),
              map(() => data),
              takeUntil(this.unsubscribe$),
            ),
        ),
      )
      .subscribe((data) => {
        this.router.navigate(['..', data.id], { relativeTo: this.route });
      });
  }

  stepperValid(): boolean {
    return (
      this.application?.type &&
      this.application.domain &&
      this.application.name &&
      (this.application.type !== 'SERVICE' ? this.application.redirectUri : true)
    );
  }
}
