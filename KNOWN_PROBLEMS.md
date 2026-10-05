# Saga Outbox Demo: Known Problems and Failure Analysis

Findings from a code review of order, inventory, payment, notification and analytics services,
plus `docker-compose.yml`. Based on reading the code only: nothing was fault-injected, and the
project has no automated tests.

Severity: **High** = money/stock/order state can be wrong or stuck. **Med** = degraded
or risky. **Low** = minor or demo-only.
Status: **Fixed** = changed in this repo. **Mitigated** = partly addressed. **Open** = not addressed.

---

## 1. Already addressed

| Item | Change | Status |
|---|---|---|
| Inventory release after a failed payment was fire-and-forget (lost if Kafka/service failed, never confirmed) | Release command now written to the **outbox** in the same transaction as the state change; new `COMPENSATING` saga state; inventory replies on `inventory.release.reply`; `CompensationSweeper` re-sends unconfirmed releases (30s timeout, 5 attempts) then marks `COMPENSATION_FAILED` | Fixed |
| Order status while compensating | Order becomes `CANCELLED` immediately (option A); saga tracks the inventory cleanup separately | Fixed |
| DLT records vanished with no trace | `dead_letter_message` table + `DeadLetterRecorder` persist every `*.DLT` record with extracted `order_id`/`message_id`, exception and raw payload | Mitigated (record only, no replay) |
| Oversell with 1 unit and 2 orders | `PESSIMISTIC_WRITE` row lock on `product_stock` serializes reserve/release | Already correct |
| order-service crash around the payment-reply commit | DB commit precedes Kafka offset commit, and the saga state check makes redelivery a no-op | Already correct |

---

## 2. Kafka failures

| # | Problem | Sev | Status |
|---|---|---|---|
| K1 | **Replies and commands sent directly, not via outbox.** `InventoryListener`, `PaymentListener` commit to their DB then call `jsonKafkaTemplate.send(...)`: async, result never checked. A crash after commit, or Kafka unreachable past the 120s delivery timeout, loses the reply while the offset is already committed. Worst case: payment charged and `COMPLETED` but the saga hangs in `PROCESSING_PAYMENT`. | High | Open |
| K2 | **Orchestrator direct sends** (`sendReserve`, `sendProcessPayment`, `notify`) have the same gap: a crash after DB commit but before the buffered send flushes loses the command. | High | Open |
| K3 | **DLT had no consumer** (no replay, no alert). | High | Mitigated: DLT is now recorded; no replay tooling or alerting yet |
| K4 | **Single broker, replication factor 1**, min ISR 1: `acks=all` gives no durability. Broker/disk loss loses messages. | Med (demo infra) | Open |
| K5 | **Tiny consumer retry window**: 3 retries, 500ms backoff doubling to 4s (about 3.5s total), then DLT. Any outage longer than that dead-letters the record. | High | Open |
| K6 | **Kafka down**: order creation still works (outbox), but the relay blocks up to 10s per row each poll while holding a DB transaction. | Med | Open |
| K7 | **Auto topic creation on**: DLT topics are created implicitly with 1 partition, RF 1. | Low | Open |
| K8 | **DLT topic discovery delay**: the recorder subscribes by pattern; a brand-new DLT topic can be noticed up to about 5 minutes late (not lost). | Low | Open |

## 3. Database failures

| # | Problem | Sev | Status |
|---|---|---|---|
| D1 | **A DB outage over about 5s pushes work to the DLT.** Orchestrator, inventory and payment handlers fail, retry briefly, dead-letter with no reply, and the saga hangs (see S1/S2). | High | Open |
| D2 | **All five databases on one Postgres container**: single failure domain, no replication, one local volume. | Med (demo infra) | Open |
| D3 | **Outbox relay weaknesses**: (a) one permanently failing row blocks all later rows (`break`); (b) no attempt count / dead state for outbox rows; (c) published rows never deleted, so the table grows forever; (d) multiple order-service instances read the same rows (no `FOR UPDATE SKIP LOCKED`), causing duplicate sends (deduped downstream). | Med | Open |
| D4 | **No `@Version` on `SagaInstance`**: listeners and the sweeper can write the same row concurrently and the last write wins (e.g. sweeper can overwrite `CANCELLED` back to `COMPENSATING`; self-heals via re-send). | Low | Open |
| D5 | **Pessimistic lock has no timeout**: `FOR UPDATE` waits indefinitely behind a long transaction. | Low | Open |
| D6 | **Reservation lookup is unlocked**: two concurrent copies of one reserve or release command can double-deduct or double-restore stock (duplicate delivery, rebalance, sweeper re-send racing the original). Fix: `@Lock(PESSIMISTIC_WRITE)` on `ReservationRepository.findById` or `@Version` on `Reservation`. | Med | Open |

## 4. Service failures

| # | Problem | Sev | Status |
|---|---|---|---|
| S1 | **Payment service down / PSP outage**: command is dead-lettered, no reply, and **nothing times out the saga**. Stays `PROCESSING_PAYMENT`, order stays `PENDING`, stock stays reserved forever. The comment "a later replay can still succeed" has no replay mechanism. | High | Open |
| S2 | **Inventory down or reserve fails**: same shape. Saga hangs in `RESERVING_INVENTORY`, order `PENDING`. | High | Open |
| S3 | **PSP charge inside the DB transaction, no idempotency key**: if the charge succeeds and the save fails or the service crashes, the retry charges again (double charge with a real PSP). | High (real PSP) | Open |
| S4 | **Refund path unused**: `RefundPaymentCommand` / `PaymentService.refund` exist but the orchestrator never sends them. Once S1 is fixed with a timeout, a late successful payment on a cancelled saga would be ignored: customer charged for a cancelled order. | Med (latent) | Open |
| S5 | **order-service is one process** (API, saga, relay, sweeper): if down, no saga advances (accepted orders resume later). | Low | Open |
| S6 | **Notification/analytics** tolerate downtime (messages wait in Kafka; dedupe/upsert), but notification only writes a log line (no real channel). | Low | Open |

## 5. Other gaps

| # | Problem | Sev | Status |
|---|---|---|---|
| O1 | **Cancellation notification via direct send**: in the failure path `notify()` is still a direct Kafka send; a crash after DB commit can lose the cancellation email (the release itself is safe). Also the reserve-failed path uses `cancel()` with direct send. | Med | Open |
| O2 | **`COMPENSATION_FAILED` is only logged** (ERROR): no alert, no manual replay tool. | Med | Open |
| O3 | **DLT replay missing**: `dead_letter_message.status` is manual; nothing replays or reconciles automatically. Messages that never reach the DLT (lost async sends, K1/K2) are not captured. | Med | Open |
| O4 | **No API idempotency key** on `POST /orders`: a client retry after a timeout creates a duplicate order. | Med | Open |
| O5 | **No automated tests** anywhere in the project. | Med | Open |
| O6 | **Observability**: only payment-service exposes actuator; no metrics for stuck sagas, outbox lag or DLT counts. | Med | Open |
| O7 | **Security**: hardcoded `saga/saga` DB credentials, plaintext Kafka, open Kafka UI with dynamic config enabled. | Low (demo) | Open |
| O8 | **New code unverified at runtime**: compile-only checks so far for the release outbox/sweeper and the DLT recorder. | Med | Open |

---

## 6. Suggested fix order

1. **Outbox for all service-to-service sends** (K1, K2, O1): root cause of most hangs. Include a reply outbox in inventory and payment.
2. **Saga timeouts** for `RESERVING_INVENTORY` and `PROCESSING_PAYMENT` (S1, S2), with a refund path for late payments (S4).
3. **Longer consumer retry window, DLT replay tool and alerting** (K5, D1, K3, O2, O3).
4. **Row locks and versioning** (D6, D4, D5) and **relay hardening** (D3, K6).
5. **PSP idempotency key** (S3) and **API idempotency key** (O4).
6. **Tests and observability** (O5, O6, O8), then infra hardening (K4, D2, K7, O7).
