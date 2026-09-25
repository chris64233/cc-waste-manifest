package com.chris64233.cc.wastemanifest.manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.cc.wastemanifest.manifest.domain.EventType;
import com.chris64233.cc.wastemanifest.manifest.domain.ManifestStatus;
import com.chris64233.cc.wastemanifest.manifest.dto.CreateManifestRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.EventSubmissionView;
import com.chris64233.cc.wastemanifest.manifest.dto.ManifestDetailView;
import com.chris64233.cc.wastemanifest.manifest.dto.SubmitEventRequest;
import com.chris64233.cc.wastemanifest.manifest.dto.WasteItemRequest;
import com.chris64233.cc.wastemanifest.manifest.repo.HandoverEventRepository;
import com.chris64233.cc.wastemanifest.manifest.service.ConflictException;
import com.chris64233.cc.wastemanifest.manifest.service.ManifestService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ManifestConcurrencyTest {

    @Autowired
    private ManifestService manifestService;

    @Autowired
    private HandoverEventRepository eventRepository;

    @Test
    void concurrentCarrierPickupsAdvanceStatusOnlyOnce() throws Exception {
        String manifestNo = createDisputedReadyManifest();
        manifestService.submitEvent(manifestNo, event("CG-1", EventType.GENERATOR_HANDOVER, "GEN",
                "100.000", Instant.parse("2026-09-24T01:00:00Z")));

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return manifestService.submitEvent(manifestNo, event("CP-" + index,
                        EventType.CARRIER_PICKUP, "CAR", "100.000",
                        Instant.parse("2026-09-24T02:00:0" + index + "Z")));
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int success = 0;
        int conflict = 0;
        for (Future<Object> future : futures) {
            try {
                future.get();
                success++;
            } catch (Exception e) {
                assertThat(e.getCause()).isInstanceOf(ConflictException.class);
                conflict++;
            }
        }
        assertThat(success).isEqualTo(1);
        assertThat(conflict).isEqualTo(threads - 1);

        ManifestDetailView detail = manifestService.getManifest(manifestNo);
        assertThat(detail.status()).isEqualTo(ManifestStatus.IN_TRANSIT.name());
        assertThat(detail.currentCustodian()).isEqualTo("CARRIER");
        assertThat(eventRepository.findByManifestIdOrderBySequenceAsc(detail.id())).hasSize(2);
    }

    @Test
    void sameEventNoSubmittedConcurrentlyIsIdempotent() throws Exception {
        String manifestNo = createDisputedReadyManifest();

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EventSubmissionView>> futures = Stream.generate(() -> (Callable<EventSubmissionView>) () -> {
                    ready.countDown();
                    start.await();
                    return manifestService.submitEvent(manifestNo, event("IDEM-GEN-1",
                            EventType.GENERATOR_HANDOVER, "GEN", "100.000",
                            Instant.parse("2026-09-24T01:00:00Z")));
                }).limit(threads)
                .map(pool::submit)
                .toList();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        for (Future<EventSubmissionView> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        ManifestDetailView detail = manifestService.getManifest(manifestNo);
        assertThat(eventRepository.findByManifestIdOrderBySequenceAsc(detail.id())).hasSize(1);
    }

    @Test
    void concurrentContradictoryDisputeConfirmationsNeverComplete() throws Exception {
        String manifestNo = createDisputedReadyManifest();
        manifestService.submitEvent(manifestNo, event("X-1", EventType.GENERATOR_HANDOVER, "GEN",
                "100.000", Instant.parse("2026-09-24T01:00:00Z")));
        manifestService.submitEvent(manifestNo, event("X-2", EventType.CARRIER_PICKUP, "CAR",
                "100.000", Instant.parse("2026-09-24T02:00:00Z")));
        manifestService.submitEvent(manifestNo, event("X-3", EventType.DISPOSER_RECEIPT, "DIS",
                "130.000", Instant.parse("2026-09-24T03:00:00Z")));
        assertThat(manifestService.getManifest(manifestNo).status())
                .isEqualTo(ManifestStatus.DISPUTED.name());

        int roundsPerParty = 5;
        ExecutorService pool = Executors.newCachedThreadPool();
        CountDownLatch ready = new CountDownLatch(roundsPerParty * 2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        // 产生方始终坚持 101.000、处置方始终坚持 102.000，两方意见永不一致。
        for (int i = 0; i < roundsPerParty; i++) {
            Instant occurredAt = Instant.parse("2026-09-24T04:00:00Z").plusSeconds(i * 10L);
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return manifestService.submitEvent(manifestNo,
                        event("GC-" + UUID.randomUUID(), EventType.GENERATOR_CONFIRM, "GEN",
                                "101.000", occurredAt));
            }));
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return manifestService.submitEvent(manifestNo,
                        event("DC-" + UUID.randomUUID(), EventType.DISPOSER_CONFIRM, "DIS",
                                "102.000", occurredAt.plusSeconds(1)));
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                // 时间倒序 / 并发更新冲突属于预期拒绝；核心断言是联单绝不完成。
                assertThat(e.getCause()).isInstanceOf(ConflictException.class);
            }
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        ManifestDetailView detail = manifestService.getManifest(manifestNo);
        assertThat(detail.status()).isEqualTo(ManifestStatus.DISPUTED.name());
        assertThat(detail.resolvedWeight()).isNull();
    }

    private String createDisputedReadyManifest() {
        String manifestNo = "MC-" + UUID.randomUUID();
        CreateManifestRequest request = new CreateManifestRequest(
                manifestNo, "GEN", "CAR", "DIS",
                List.of(new WasteItemRequest("HW08", 3, new java.math.BigDecimal("60.000")),
                        new WasteItemRequest("HW49", 2, new java.math.BigDecimal("40.000"))),
                new java.math.BigDecimal("0.05"));
        manifestService.createManifest(request);
        return manifestNo;
    }

    private SubmitEventRequest event(String eventNo, EventType type, String actor,
                                     String weight, Instant occurredAt) {
        return new SubmitEventRequest(eventNo, type, actor, new java.math.BigDecimal(weight), occurredAt);
    }
}
