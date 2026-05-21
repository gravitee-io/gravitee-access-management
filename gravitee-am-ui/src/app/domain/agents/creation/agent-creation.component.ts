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
import { SnackbarService } from '../../../services/snackbar.service';
import {
  CopyClientSecretComponent,
  CopyClientSecretCopyDialogData,
} from '../../../components/client-secrets-management/dialog/copy-client-secret/copy-client-secret.component';

@Component({
  selector: 'app-agent-creation',
  templateUrl: './agent-creation.component.html',
  styleUrls: ['./agent-creation.component.scss'],
  standalone: false,
})
export class AgentCreationComponent implements OnInit {
  public application: any = {};
  public validating = false;
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
    this.application.creationMode = 'manual';
    this.application.type = 'AGENT';
  }

  isCimd(): boolean {
    return this.application?.creationMode === 'cimd';
  }

  validateCimd(): void {
    if (!this.application.cimdUrl) {
      return;
    }
    this.validating = true;
    this.applicationService.validateCimd(this.application.domain, this.application.cimdUrl).subscribe(
      (preview) => {
        this.validating = false;
        this.application.cimdPreview = preview;
        this.application.cimdClientName = null;
        this.application.name = preview?.metadata?.client_name ?? null;
        this.stepper.next();
      },
      (err: unknown) => {
        this.validating = false;
        const message = (err as { error?: { message?: string } })?.error?.message ?? 'Unable to validate CIMD URL';
        this.snackbarService.open(message);
      },
    );
  }

  create() {
    if (this.isCimd()) {
      this.createFromCimd();
      return;
    }

    const app: any = {
      name: this.application.name,
      description: this.application.description,
      clientId: this.application.clientId,
      clientSecret: this.application.clientSecret,
      redirectUris: this.application.redirectUri ? [this.application.redirectUri] : null,
      type: 'AGENT',
      kind: this.application.agentType,
    };

    this.applicationService
      .create(this.application.domain, app)
      .pipe(
        switchMap((data) =>
          this.matDialog
            .open<CopyClientSecretComponent, CopyClientSecretCopyDialogData, void>(CopyClientSecretComponent, {
              width: GIO_DIALOG_WIDTH.MEDIUM,
              disableClose: true,
              data: {
                secret: data.settings.oauth.clientSecret,
                renew: false,
              },
              role: 'alertdialog',
              id: 'applicationClientSecretCopyDialog',
            })
            .afterClosed()
            .pipe(
              tap(() => {
                this.snackbarService.open('Agent ' + data.name + ' created');
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

  private createFromCimd(): void {
    const resolvedName = this.application?.cimdPreview?.metadata?.client_name || this.application.cimdClientName;
    const payload: any = {
      name: resolvedName,
      type: 'AGENT',
      kind: this.application.agentType,
      description: this.application.description,
      cimdUrl: this.application.cimdUrl,
    };
    if (this.application?.cimdPreview?.missing?.clientName && this.application.cimdClientName) {
      payload.clientName = this.application.cimdClientName;
    }
    this.applicationService.createFromCimd(this.application.domain, payload).subscribe(
      (data) => {
        this.snackbarService.open('Agent ' + data.name + ' created');
        this.router.navigate(['..', data.id], { relativeTo: this.route });
      },
      (err: unknown) => {
        const message = (err as { error?: { message?: string } })?.error?.message ?? 'Unable to create agent from CIMD URL';
        this.snackbarService.open(message);
      },
    );
  }

  stepperValid(): boolean {
    if (!this.application?.agentType || !this.application.domain) {
      return false;
    }
    if (this.isCimd()) {
      const preview = this.application?.cimdPreview;
      const resolvedName = preview?.metadata?.client_name || this.application.cimdClientName;
      return !!preview && !!resolvedName;
    }
    if (!this.application.name) {
      return false;
    }
    if (this.application.agentType === 'AUTONOMOUS') {
      return true;
    }
    return !!this.application.redirectUri;
  }

  step2Valid(): boolean {
    if (this.isCimd()) {
      return !!this.application.cimdUrl;
    }
    if (!this.application?.name) {
      return false;
    }
    if (this.application.agentType === 'AUTONOMOUS') {
      return true;
    }
    return !!this.application.redirectUri;
  }
}
