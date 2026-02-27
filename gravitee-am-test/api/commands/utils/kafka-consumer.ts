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

import { Consumer, EachMessageHandler, Kafka, logLevel } from 'kafkajs';
import { uniqueName } from '@utils-commands/misc';

const DEFAULT_KAFKA_WAIT_TIMEOUT_MS = 20000;
const DEFAULT_KAFKA_ASSERT_NO_WINDOW_MS = 5000;
const MAX_KAFKA_CONSUMER_ATTEMPTS = 10;
const KAFKA_CONSUMER_RETRY_DELAY_MS = 500;

export interface KafkaAuditPayload {
  type: string;
  referenceId?: string;
  domainId?: string;
  [key: string]: unknown;
}

export interface KafkaWaitOptions {
  timeoutMs?: number;
  predicate: (msg: KafkaAuditPayload) => boolean;
}

export interface KafkaAssertNoOptions {
  windowMs?: number;
  predicate: (msg: KafkaAuditPayload) => boolean;
}

function createKafkaClient(clientId: string) {
  return new Kafka({
    clientId,
    brokers: [(process.env.KAFKA_BOOTSTRAP_URL ?? 'localhost:9092')],
    logLevel: logLevel.ERROR,
  });
}

/**
 * Ensures the topic exists before subscribing.
 * createTopics is idempotent: returns false without error when the topic already exists.
 */
async function ensureTopicExists(kafka: Kafka, topic: string): Promise<void> {
  const admin = kafka.admin();
  await admin.connect();
  try {
    await admin.createTopics({
      topics: [{ topic, numPartitions: 1, replicationFactor: 1 }],
      waitForLeaders: true,
    });
  } finally {
    await admin.disconnect();
  }
}

/**
 * Connects a consumer, subscribes, and starts the run loop.
 */
async function startConsumer(kafka: Kafka, topic: string, baseGroupId: string, eachMessage: EachMessageHandler): Promise<Consumer> {
  let lastErr: unknown;
  const start = Date.now();

  for (let attempt = 1; attempt <= MAX_KAFKA_CONSUMER_ATTEMPTS; attempt++) {
    const consumer = kafka.consumer({ groupId: `${baseGroupId}-${attempt}` });
    try {
      await consumer.connect();
      await consumer.subscribe({ topic, fromBeginning: true });
      await consumer.run({ eachMessage });
      console.log(`Kafka consumer for topic "${topic}" ready after ${(Date.now() - start) / 1000}s (attempt ${attempt})`);
      return consumer;
    } catch (err) {
      await consumer.disconnect().catch(() => {});
      lastErr = err;
      if (attempt < MAX_KAFKA_CONSUMER_ATTEMPTS) {
        await new Promise<void>((r) => setTimeout(r, KAFKA_CONSUMER_RETRY_DELAY_MS));
      }
    }
  }

  throw lastErr;
}

/**
 * Subscribes to a Kafka topic, calls trigger(), and returns the first message
 * matching options.predicate. Throws if no match is found within options.timeoutMs.
 */
export async function waitForKafkaMessage(
  topic: string,
  options: KafkaWaitOptions,
  trigger: () => Promise<void>,
): Promise<KafkaAuditPayload> {
  const { timeoutMs = DEFAULT_KAFKA_WAIT_TIMEOUT_MS, predicate } = options;
  const id = uniqueName('kafka-consumer', true);
  const kafka = createKafkaClient(id);

  await ensureTopicExists(kafka, topic);

  let consumer: Consumer;
  let resolve: (value: KafkaAuditPayload) => void;
  let reject: (reason: unknown) => void;

  const resultPromise = new Promise<KafkaAuditPayload>((res, rej) => {
    resolve = res;
    reject = rej;
  });

  consumer = await startConsumer(kafka, topic, id, async ({ message }) => {
    try {
      const payload: KafkaAuditPayload = JSON.parse(message.value?.toString() ?? '{}');
      if (predicate(payload)) {
        clearTimeout(timer);
        // Resolve immediately — do not await disconnect() first, as it can deadlock
        // when called from inside the eachMessage handler (disconnect waits for the
        // handler to finish, but the handler is waiting for disconnect).
        resolve(payload);
        consumer.disconnect().catch(() => {});
      }
    } catch {
      // ignore parse errors
    }
  });

  const timer = setTimeout(() => {
    // Reject immediately — do not await disconnect() first; from outside the run loop
    // disconnect() blocks until the active poll fetch resolves (up to several seconds),
    // delaying rejection well past the intended timeout.
    reject(new Error(`Timeout: no Kafka message on topic "${topic}" matching predicate within ${timeoutMs}ms`));
    consumer.disconnect().catch(() => {});
  }, timeoutMs);

  try {
    await trigger();
  } catch (err) {
    clearTimeout(timer);
    await consumer.disconnect().catch(() => {});
    reject(err);
  }

  return resultPromise;
}

/**
 * Subscribes to a Kafka topic, calls trigger(), waits windowMs, then asserts
 * no message matched options.predicate during that window.
 */
export async function assertNoKafkaMessage(
  topic: string,
  options: KafkaAssertNoOptions,
  trigger: () => Promise<void>,
): Promise<void> {
  const { windowMs = DEFAULT_KAFKA_ASSERT_NO_WINDOW_MS, predicate } = options;
  const id = uniqueName('kafka-assert-no', true);
  const kafka = createKafkaClient(id);

  await ensureTopicExists(kafka, topic);

  let matched: KafkaAuditPayload | null = null;

  const consumer = await startConsumer(kafka, topic, id, async ({ message }) => {
    try {
      const payload: KafkaAuditPayload = JSON.parse(message.value?.toString() ?? '{}');
      if (predicate(payload)) {
        matched = payload;
      }
    } catch {
      // ignore parse errors
    }
  });

  try {
    await trigger();
    await new Promise<void>((r) => setTimeout(r, windowMs));
  } finally {
    await consumer.disconnect().catch(() => {});
  }

  if (matched !== null) {
    throw new Error(
      `Expected no Kafka message on topic "${topic}" matching predicate, but received one: ${JSON.stringify(matched)}`,
    );
  }
}
