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


import { z } from 'zod';

export const configSchema = z.object({
  PORT: z.string().transform((val) => parseInt(val, 10)).pipe(z.number().int().positive()).default('3001'),
  AM_GATEWAY_URL: z.string().url(),
  DOMAIN_HRID: z.string().min(1),
  AUTHZEN_URL: z.string().url().optional(),
  LOG_LEVEL: z.enum(['error', 'warn', 'info', 'debug']).default('info'),
});

export type ConfigEnv = z.infer<typeof configSchema>;
