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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-domain-bot-detections',
  templateUrl: './bot-detections.component.html',
  styleUrls: ['./bot-detections.component.scss'],
  standalone: false,
})
export class DomainSettingsBotDetectionsComponent implements OnInit {
  private detectionTypes: any = {
    'google-recaptcha-v3-am-bot-detection': 'Google reCAPTHCA v3',
  };
  detections: any[];
  domainId: any;

  constructor(private route: ActivatedRoute) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.detections = this.route.snapshot.data['detections'];
  }

  isEmpty() {
    return !this.detections || this.detections.length === 0;
  }

  displayType(type) {
    if (this.detectionTypes[type]) {
      return this.detectionTypes[type];
    }
    return type;
  }
}
