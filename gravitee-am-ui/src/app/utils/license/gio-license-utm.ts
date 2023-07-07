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
export type UTMSource = 'oss_am';

export type UTMCampaign = 'oss_am_to_ee_am';

export class UTM {
  constructor(private readonly source: UTMSource, private readonly medium: string, private readonly campaign: UTMCampaign) {}

  public buildURL(): string {
    return `https://gravitee.io/self-hosted-trial?utm_source=${this.source}&utm_medium=${this.medium}&utm_campaign=${this.campaign}`;
  }

  public static ossEnterpriseV4(medium: string): UTM {
    return new UTM('oss_am', medium, 'oss_am_to_ee_am');
  }
}
