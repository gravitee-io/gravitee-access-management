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
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {OrganizationService} from '../../../../services/organization.service';
import {SnackbarService} from '../../../../services/snackbar.service';
import {DialogService} from '../../../../services/dialog.service';
import {AuthService} from '../../../../services/auth.service';
import { BotDetectionService } from 'app/services/bot-detection.service';

@Component({
  selector: 'app-bot-detection',
  templateUrl: './bot-detection.component.html',
  styleUrls: ['./bot-detection.component.scss']
})
export class BotDetectionComponent implements OnInit {
  private domainId: string;
  formChanged = false;
  configurationIsValid = true;
  configurationPristine = true;
  botDetection: any;
  botDetectionSchema: any;
  botDetectionConfiguration: any;
  updatebotDetectionConfiguration: any;
  editMode: boolean;

  codeMirrorConfig: any = { lineNumbers: false, readOnly: true, lineWrapping: true};

  constructor(private route: ActivatedRoute,
              private router: Router,
              private organizationService: OrganizationService,
              private botDetectionService: BotDetectionService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private authService: AuthService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.botDetection = this.route.snapshot.data['botDetection'];
    this.botDetectionConfiguration = JSON.parse(this.botDetection.configuration);
    this.updatebotDetectionConfiguration = this.botDetectionConfiguration;
    this.editMode = this.authService.hasPermissions(['domain_bot_detection_update']);

    this.organizationService.botDetectionsSchema(this.botDetection.type).subscribe(data => {
      this.botDetectionSchema = data;
    });
  }

  update() {
    this.botDetection.configuration = JSON.stringify(this.updatebotDetectionConfiguration);
    this.botDetectionService.update(this.domainId, this.botDetection.id, this.botDetection).subscribe(data => {
      this.snackbarService.open('Bot detection updated');
    })
  }

  enablebotDetectionUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.botDetection.configuration === JSON.stringify(configurationWrapper.configuration);
      this.configurationIsValid = configurationWrapper.isValid;
      this.updatebotDetectionConfiguration = configurationWrapper.configuration;
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete bot detection', 'Are you sure you want to delete this Bot Detection ?')
      .subscribe(res => {
        if (res) {
          this.botDetectionService.delete(this.domainId, this.botDetection.id).subscribe(() => {
            this.snackbarService.open('Bot detection deleted');
            this.router.navigate(['..'], { relativeTo: this.route });
          });
        }
      });
  }

  valueCopied(message: string) {
    this.snackbarService.open(message);
  }

  getSnippetImportJS() {
    if (this.botDetection.type == 'google-recaptcha-v3-am-bot-detection') {
      return '<script src="https://www.google.com/recaptcha/api.js?render=' + this.botDetectionConfiguration.siteKey + '"></script>'
    }
  }

  getSnippetCallService() {
    if (this.botDetection.type == 'google-recaptcha-v3-am-bot-detection') {
      return `
<script>
function onClick(event) {
  event.preventDefault();
  grecaptcha.ready(function() {
    grecaptcha.execute('`+ this.botDetectionConfiguration.siteKey +`', {action: 'submit'}).then(function(token) {
      // Add your logic to submit 
      // to your backend server here
      // and assign the token variable
      // to the configured parameter ` + this.botDetectionConfiguration.tokenParameterName + `
    });
  });
}
</script>
    `
    }
  }

}
