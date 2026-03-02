# Errors Encountered, Fixes Applied & Root Cause Analysis

Chronological log of every significant error hit while building and operating this Kafka practice cluster.

---

## 1. Port 9092 Blocked by Windows Hyper-V / WSL

**Error**
```
Bind for 0.0.0.0:9092 failed: port is already allocated
```

**Why it happens**
Windows Hyper-V and WSL2 reserve a dynamic port range that often includes 9092. Any process (including Docker) trying to bind `0.0.0.0:9092` on the host gets rejected.

**Fix**
Changed the external port mapping in `docker-compose.yml` from `9092:9092` to `19092:9092`.
Internal container-to-container traffic (`kafka:9092`) is unaffected — only the host-facing port changes.

```yaml
ports:
  - "19092:9092"   # was 9092:9092
```

---

## 2. `docker-credential-desktop` Not in WSL PATH

**Error**
```
error getting credentials - err: exec: "docker-credential-desktop": executable file not found in $PATH
```

**Why it happens**
`docker compose --build` invokes the Docker buildkit, which calls the credential helper `docker-credential-desktop`. On Windows with WSL2, this executable lives in `C:\Program Files\Docker\...` and is not on the WSL PATH.

**Fix**
Avoided `--build` entirely. Instead, built the JAR with Maven on the host and used `docker cp` + `docker restart` to deploy:

```bash
mvn -pl <module> package -DskipTests
docker cp <module>/target/<artifact>.jar <container>:/app/app.jar
docker restart <container>
```

---

## 3. Docker CLI Not Found in WSL Bash

**Error**
```
bash: docker: command not found
```

**Why it happens**
The Docker Desktop CLI (`docker.exe`) is installed on the Windows host but is not automatically on the WSL bash `$PATH`.

**Fix**
Invoked Docker through PowerShell explicitly:

```bash
powershell.exe -Command "& 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' ..."
```

---

## 4. `NodeExistsException` on Broker Restart

**Error**
```
org.apache.zookeeper.KeeperException$NodeExistsException: KeeperErrorCode = NodeExists
    at kafka.zk.KafkaZkClient$CheckedEphemeral.create
    at kafka.zk.KafkaZkClient.registerBroker
```

**Why it happens**
When a Kafka broker container is recreated (`docker compose up -d`), the new container starts a fresh JVM and ZooKeeper session. However, the *old* container's ZooKeeper session may still be alive (within the session timeout window), keeping its ephemeral broker registration node (`/brokers/ids/<id>`) intact. The new broker tries to create the same ephemeral node and ZooKeeper rejects it.

**Fix**
Restart ZooKeeper first so all existing sessions are terminated and ephemeral nodes are cleared, then restart the brokers:

```bash
docker restart zookeeper
sleep 15
docker restart kafka-broker-1 kafka-broker-2 kafka-broker-3
```

---

## 5. `MissingSourceTopicException` — Streams Goes to ERROR State

**Error**
```
org.apache.kafka.streams.errors.MissingSourceTopicException: One or more source topics were missing during rebalance
    at StreamsRebalanceListener.onPartitionsAssigned
```
Followed by:
```
KafkaStreams state transition: RUNNING -> PENDING_ERROR -> ERROR
```

**Why it happens**
When the `order-events` topic was deleted and recreated (to change the replication factor), Kafka Streams attempted to rebalance during the brief window when the topic had no metadata. The broker returned `INCOMPLETE_SOURCE_TOPIC_METADATA` in the group assignment, which `StreamsRebalanceListener` converts into `MissingSourceTopicException`. With no handler registered, all stream threads die and `KafkaStreams` transitions to the terminal `ERROR` state (KIP-662 / KIP-696). ERROR is unrecoverable without a restart.

**Fix**
Registered a `StreamsUncaughtExceptionHandler` returning `REPLACE_THREAD` for `MissingSourceTopicException`. The dead thread is discarded and a new one is started, which retries the rebalance until the topic is available. The Kafka Streams client stays in `RUNNING/REBALANCING` and self-heals.

```java
// kafka-consumer and kafka-fraud-detector:
// KafkaStreamsExceptionHandlerConfig.java
factoryBean.setStreamsUncaughtExceptionHandler(exception -> {
    if (exception instanceof MissingSourceTopicException) {
        log.warn("Source topic missing — replacing stream thread to retry");
        return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
    }
    ...
});
```

Applied via `StreamsBuilderFactoryBeanConfigurer` (Spring Kafka hook).

---

## 6. `DisconnectException` (Wrapped) — Streams ERROR After Broker Restart

**Error**
```
org.apache.kafka.streams.errors.StreamsException: ...
Caused by: org.apache.kafka.common.errors.DisconnectException: null
    at TaskManager.commitOffsetsOrTransaction
    at StreamThread.runOnce
```

**Why it happens**
When brokers were restarted, Kafka Streams was mid-commit (`commitSync()`). The broker disconnect caused `DisconnectException` to propagate up through the Streams commit path. `StreamsException` wraps it and the stream thread dies. Without a handler, this drives the client to ERROR state.

**Fix**
Extended the `StreamsUncaughtExceptionHandler` to also catch `DisconnectException` as the *cause* of the exception:

```java
if (exception.getCause() instanceof DisconnectException) {
    return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
}
```

---

## 7. `DisconnectException` (Unwrapped) — Direct Exception from StreamThread

**Error**
```
org.apache.kafka.common.errors.DisconnectException: null
    at NetworkClient.initiateConnect
    at StreamThread.run
```

**Why it happens**
In some code paths, Kafka Streams passes `DisconnectException` directly to the uncaught exception handler without wrapping it in `StreamsException`. The earlier fix only checked `exception.getCause() instanceof DisconnectException`, missing the case where the exception *is* the `DisconnectException` itself.

**Fix**
Added a direct `instanceof` check alongside the cause check:

```java
if (exception instanceof DisconnectException
        || exception.getCause() instanceof DisconnectException) {
    return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD;
}
```

---

## 8. `UnknownHostException: kafka-2` — Docker DNS Negative Cache

**Error**
```
java.net.UnknownHostException: kafka-2
    at InetAddress$CachedAddresses.get
    at DefaultHostResolver.resolve
    at NetworkClient.initiateConnect
```

**Why it happens**
When `kafka-2` was recreated via `docker compose up -d`, there was a brief window where Docker's embedded DNS had no valid record for `kafka-2`. The Java Kafka client attempted DNS resolution during this window, received an `UnknownHostException`, and Java's DNS negative cache stored the failure. Subsequent resolution attempts within the negative TTL window returned the cached failure, causing `DisconnectException` to bubble up through the Streams commit path.

**Fix**
Caught by the same `DisconnectException` handler added in Fix 7 (`REPLACE_THREAD`). The replacement thread retries after the DNS negative cache expires and connectivity is restored.

---

## 9. `Leader: none` — All Partitions Offline After Stopping Broker

**Error**
```
Topic: order-events  Partition: 0  Leader: none  Replicas: 1,2  Isr: 1
Topic: order-events  Partition: 1  Leader: none  Replicas: 2,1  Isr: 1
Topic: order-events  Partition: 2  Leader: none  Replicas: 1,2  Isr: 1
```

**Why it happens**
During repeated broker restarts, broker-2 fell behind broker-1's replication and was evicted from ISR (`replica.lag.time.max.ms` exceeded). The ISR shrank to `[1]` only. When broker-1 was intentionally stopped for failover testing, the only ISR member was gone. Kafka will not elect an out-of-ISR replica as leader (`unclean.leader.election.enable=false`), so all partitions had `Leader: none`.

**Fix (immediate)**
Restarted broker-1 to restore leadership. Broker-2 re-synced and rejoined ISR.

**Fix (structural)**
Added a third broker so the cluster can tolerate one broker going down while ISR still has two members meeting `min.insync.replicas=2`.

**Correct failover test procedure:**
1. Verify `Isr: 1,2,3` on all partitions before stopping any broker
2. Stop one broker — the remaining two elect a new leader immediately
3. All partitions remain available

---

## 10. `order-events` Topic Recreated with RF=1 Instead of RF=2

**Error**
```
ReplicationFactor: 1
Partition: 0  Leader: 2  Replicas: 2  Isr: 2
```

**Why it happens**
After deleting the topic, Kafka's `auto.create.topics.enable=true` caused the topic to be auto-created by broker metadata before the Spring Boot `KafkaTopicConfig` bean ran. The auto-created topic used the broker's `DEFAULT_REPLICATION_FACTOR` which was still 1 (old running container config). Additionally, the `kafka-producer` container was still running the old JAR with `replicas(1)`.

**Fix**
1. Rebuilt the `kafka-producer` JAR with `replicas(2)` (later `replicas(3)`)
2. Deployed the new JAR to the container
3. Deleted the topic and restarted the producer so `KafkaTopicConfig` recreated it with the correct RF

---

## 11. `order-events` Topic Stays RF=2 After Adding Broker-3

**Error**
```
ReplicationFactor: 2   (expected 3 after adding kafka-broker-3)
```

**Why it happens**
The existing `order-events` topic was created when only 2 brokers were running. Adding a third broker to the cluster does not automatically reassign partitions or change the replication factor of existing topics. Topic RF is set at creation time and persists until explicitly altered or the topic is deleted and recreated.

Additionally, the running `kafka-broker-1` and `kafka-broker-2` containers still had the old `KAFKA_DEFAULT_REPLICATION_FACTOR=2` environment from before the `docker-compose.yml` update — the new setting only takes effect after the containers are recreated.

**Fix**
1. Recreated all 3 broker containers via `docker compose up -d kafka kafka-2 kafka-3` to pick up new environment
2. Restarted ZooKeeper to clear stale ephemeral nodes (NodeExistsException prevention)
3. Deleted the topic: `kafka-topics --delete --topic order-events`
4. Redeployed `kafka-producer` JAR with `replicas(3)` and restarted — `KafkaTopicConfig` recreated the topic with RF=3

---

## 12. `COORDINATOR_NOT_AVAILABLE` Loop — Consumer Stuck, Not Streaming

**Error**
```
Group coordinator kafka:9092 is unavailable or invalid due to cause: coordinator unavailable
SyncGroup failed: The coordinator is not available.
Request joining group due to: rebalance failed due to 'The coordinator is not available.' (CoordinatorNotAvailableException)
```

**Why it happens**
The `__consumer_offsets` internal topic was created when the cluster had only 2 brokers, giving it `ReplicationFactor: 2`. After updating `KAFKA_MIN_INSYNC_REPLICAS: 2` broker-wide, this global setting was also applied to `__consumer_offsets`. With RF=2 and replicas knocked out of sync by the repeated broker restarts, the offset coordinator could not acknowledge offset commits (producing to `__consumer_offsets` requires `min.insync.replicas` to be met). The consumer group coordinator returned `COORDINATOR_NOT_AVAILABLE` on every attempt, causing an infinite retry loop with no data flowing.

**Fix (immediate)**
Overrode `min.insync.replicas` specifically on the internal topic to 1:

```bash
kafka-configs --bootstrap-server kafka:9092 \
  --alter --entity-type topics \
  --entity-name __consumer_offsets \
  --add-config min.insync.replicas=1
```

Consumer recovered immediately and resumed streaming.

**Fix (permanent / clean)**
Perform a full cluster reset so all internal topics are created fresh with RF=3 from the start:

```bash
docker compose down -v      # removes all volumes including __consumer_offsets
docker compose up -d        # all internal topics created fresh with RF=3, min.isr=2
```

---

## 13. MM2 Silently Not Replicating — Connect Storage Topics Missing on Dest

**Error**
No error message. MM2 container shows `Up` but `mm2-configs.source.internal`,
`mm2-offsets.source.internal`, and `mm2-status.source.internal` never appear on the dest cluster.
Replication does not happen.

**Why it happens**
The three Kafka Connect internal storage topics are written to the **dest** cluster by MM2's embedded
Connect worker. Setting `offset.storage.replication.factor`, `status.storage.replication.factor`, or
`config.storage.replication.factor` to a value greater than the number of brokers on the dest cluster
causes topic creation to fail silently. MM2 keeps running but never registers its connectors.

In this project the dest cluster has 1 broker, so RF must be 1 for these topics.

**Fix**
```properties
# mm2.properties — dest has 1 broker
offset.storage.replication.factor  = 1
status.storage.replication.factor  = 1
config.storage.replication.factor  = 1
```

After correcting the values, stop MM2, then start it again. If the topics were partially created,
delete them first:
```bash
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-configs.source.internal
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-offsets.source.internal
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-status.source.internal
docker compose up -d kafka-mirrormaker2
```

**Verification**
Within 30 seconds of startup, all three topics must appear on the dest cluster:
```bash
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 --list
# expected: mm2-configs.source.internal, mm2-offsets.source.internal, mm2-status.source.internal
```

---

## 14. `topics.rename.format` Has No Effect in cp-kafka:7.6.1

**Error**
```properties
# mm2.properties
source->dest.topics.rename.format = ${topic}
```
Expected: replicated topic on dest named `order-events`.
Actual: replicated topic on dest still named `source.order-events`.

**Why it happens**
`DefaultReplicationPolicy.formatRemoteTopic()` in Apache Kafka 3.6.x (Confluent cp-kafka:7.6.1)
hardcodes the naming logic as `sourceClusterAlias + separator + topic`. The `topics.rename.format`
config is stored in `mm2-configs.source.internal` and passed to connector tasks, but the method
never reads it. The setting is silently ignored.

**Fix**
Remove `topics.rename.format` from mm2.properties — it is a no-op in this version.

Accept the default `source.order-events` naming on the dest cluster. On failover, switch consumers
via two environment variables — no code change needed:

```yaml
# kafka-consumer in docker-compose.yml on failover
environment:
  SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka-dest:9092
  KAFKA_TOPIC_ORDER_EVENTS: source.order-events
```

`kafka.topic.order-events=${KAFKA_TOPIC_ORDER_EVENTS:order-events}` is already in
`application.properties`, so the consumer picks this up on restart.

---

## 15. MM2 Connector Config Not Updated After `mm2.properties` Change

**Error**
Updated `mm2.properties` and restarted the MM2 container. The old connector configuration is still
active — the change has no effect.

**Why it happens**
MM2's embedded Kafka Connect caches connector configurations in `mm2-configs.source.internal` on the
dest cluster. A container restart reads from this cached config, not from mm2.properties, so property
changes are ignored.

**Fix**
Stop MM2, delete the three Connect storage topics from the dest cluster, then restart. MM2 will
re-read mm2.properties and register connectors fresh:

```bash
docker stop kafka-mirrormaker2
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-configs.source.internal
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-offsets.source.internal
docker exec kafka-dest kafka-topics --bootstrap-server kafka-dest:9092 \
  --delete --topic mm2-status.source.internal
docker compose up -d kafka-mirrormaker2
```

Note: deleting `mm2-offsets.source.internal` resets MM2's position in the source topic.
MM2 will re-replicate from the beginning, which may cause duplicates on the dest cluster.

---

## Summary Table

| # | Error | Root Cause | Fix |
|---|---|---|---|
| 1 | Port 9092 already allocated | Windows Hyper-V reserves the port | Map external port to 19092 |
| 2 | `docker-credential-desktop` not found | Credential helper not in WSL PATH | Use `docker cp` + `docker restart` instead of `--build` |
| 3 | `docker: command not found` in WSL | Docker CLI is Windows-only binary | Invoke via `powershell.exe -Command "& 'C:\...\docker.exe'"` |
| 4 | `NodeExistsException` on broker restart | Old ZK session still alive when container recreated | Restart ZooKeeper first, then brokers |
| 5 | `MissingSourceTopicException` → ERROR state | Topic deleted mid-rebalance, no exception handler | `StreamsUncaughtExceptionHandler` returning `REPLACE_THREAD` |
| 6 | Wrapped `DisconnectException` → ERROR state | Broker restart mid-commit, no handler for cause | Check `exception.getCause() instanceof DisconnectException` |
| 7 | Unwrapped `DisconnectException` → ERROR state | Streams passes exception directly in some paths | Check `exception instanceof DisconnectException` directly |
| 8 | `UnknownHostException: kafka-2` | Docker DNS negative cache hit during container recreation | Handled by same `DisconnectException` `REPLACE_THREAD` handler |
| 9 | `Leader: none` on all partitions | Only one ISR member, then stopped it | 3-broker cluster with RF=3 so one failure still leaves quorum |
| 10 | Topic recreated with RF=1 | Old JAR deployed; auto-create used broker default | Rebuild JAR, delete topic, restart producer |
| 11 | Topic stays RF=2 after broker-3 added | Existing topic RF immutable; running containers had old env | Recreate broker containers, delete topic, redeploy producer JAR |
| 12 | `COORDINATOR_NOT_AVAILABLE` loop | `__consumer_offsets` RF=2 but broker min.isr=2 after restarts | Set `min.insync.replicas=1` on `__consumer_offsets`; permanent fix: full reset |
| 13 | MM2 running but not replicating, no error | `offset/status/config.storage.replication.factor` > dest broker count; Connect topics never created | Set all three to RF=1 (dest has 1 broker); delete stale topics and restart MM2 |
| 14 | `topics.rename.format=${topic}` has no effect | `DefaultReplicationPolicy.formatRemoteTopic()` in Kafka 3.6.x ignores the config | Remove the setting; accept `source.order-events`; use env var on failover |
| 15 | mm2.properties change ignored after restart | Connector config cached in `mm2-configs.source.internal`; restart reads cache not file | Stop MM2, delete all three `mm2-*.source.internal` topics from dest, restart MM2 |