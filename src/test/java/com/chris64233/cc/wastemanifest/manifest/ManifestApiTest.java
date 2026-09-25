package com.chris64233.cc.wastemanifest.manifest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ManifestApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullHappyPathWithinToleranceCompletes() throws Exception {
        String manifestNo = "M-HAPPY-001";
        createManifest(manifestNo, "100.000", null);

        mockMvc.perform(get("/api/manifests/" + manifestNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.currentCustodian").value("GENERATOR"))
                .andExpect(jsonPath("$.declaredTotalWeight").value(100.000))
                .andExpect(jsonPath("$.items.length()").value(2));

        submitEvent(manifestNo, "E-H-1", "GENERATOR_HANDOVER", "GEN", "100.000", "2026-09-24T01:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.manifest.status").value("CREATED"))
                .andExpect(jsonPath("$.manifest.currentCustodian").value("GENERATOR"))
                .andExpect(jsonPath("$.event.sequence").value(1));

        submitEvent(manifestNo, "E-H-2", "CARRIER_PICKUP", "CAR", "100.200", "2026-09-24T02:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.manifest.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.manifest.currentCustodian").value("CARRIER"))
                .andExpect(jsonPath("$.event.sequence").value(2));

        submitEvent(manifestNo, "E-H-3", "DISPOSER_RECEIPT", "DIS", "104.500", "2026-09-24T03:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.manifest.status").value("COMPLETED"))
                .andExpect(jsonPath("$.manifest.currentCustodian").value("DISPOSER"))
                .andExpect(jsonPath("$.manifest.resolvedWeight").value(104.500));

        mockMvc.perform(get("/api/manifests/" + manifestNo + "/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventType").value("GENERATOR_HANDOVER"))
                .andExpect(jsonPath("$[2].eventType").value("DISPOSER_RECEIPT"));
    }

    @Test
    void weightOverToleranceEntersDisputeAndRequiresTwoMatchingConfirmations() throws Exception {
        String manifestNo = "M-DISPUTE-001";
        createManifest(manifestNo, "100.000", null);

        submitEvent(manifestNo, "E-D-1", "GENERATOR_HANDOVER", "GEN", "100.000", "2026-09-24T01:00:00Z");
        submitEvent(manifestNo, "E-D-2", "CARRIER_PICKUP", "CAR", "100.000", "2026-09-24T02:00:00Z");
        submitEvent(manifestNo, "E-D-3", "DISPOSER_RECEIPT", "DIS", "110.000", "2026-09-24T03:00:00Z")
                .andExpect(jsonPath("$.manifest.status").value("DISPUTED"));

        submitEvent(manifestNo, "E-D-4", "DISPOSER_CONFIRM", "DIS", "108.000", "2026-09-24T04:00:00Z")
                .andExpect(jsonPath("$.manifest.status").value("DISPUTED"));
        submitEvent(manifestNo, "E-D-5", "GENERATOR_CONFIRM", "GEN", "107.000", "2026-09-24T05:00:00Z")
                .andExpect(jsonPath("$.manifest.status").value("DISPUTED"));

        submitEvent(manifestNo, "E-D-6", "DISPOSER_CONFIRM", "DIS", "107.000", "2026-09-24T06:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.manifest.status").value("COMPLETED"))
                .andExpect(jsonPath("$.manifest.resolvedWeight").value(107.000));

        mockMvc.perform(get("/api/manifests/" + manifestNo + "/timeline"))
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void rejectsOutOfOrderWrongRoleAndTimeRegression() throws Exception {
        String manifestNo = "M-ORDER-001";
        createManifest(manifestNo, "100.000", null);

        submitEvent(manifestNo, "E-O-1", "CARRIER_PICKUP", "CAR", "100.000", "2026-09-24T02:00:00Z")
                .andExpect(status().isConflict());

        submitEvent(manifestNo, "E-O-2", "GENERATOR_HANDOVER", "IMPERSONATOR", "100.000",
                "2026-09-24T01:00:00Z")
                .andExpect(status().isConflict());

        submitEvent(manifestNo, "E-O-3", "GENERATOR_HANDOVER", "GEN", "100.000", "2026-09-24T01:00:00Z")
                .andExpect(status().isCreated());

        submitEvent(manifestNo, "E-O-4", "CARRIER_PICKUP", "CAR", "100.000", "2026-09-24T00:30:00Z")
                .andExpect(status().isConflict());

        submitEvent(manifestNo, "E-O-5", "CARRIER_PICKUP", "CAR", "100.000", "2026-09-24T02:00:00Z")
                .andExpect(status().isCreated());
    }

    @Test
    void idempotentReplaySucceedsAndDifferentContentConflicts() throws Exception {
        String manifestNo = "M-IDEM-001";
        createManifest(manifestNo, "100.000", null);

        String body = eventBody("E-I-1", "GENERATOR_HANDOVER", "GEN", "100.000", "2026-09-24T01:00:00Z");
        mockMvc.perform(post("/api/manifests/" + manifestNo + "/events")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false));

        mockMvc.perform(post("/api/manifests/" + manifestNo + "/events")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.event.sequence").value(1));

        String changed = eventBody("E-I-1", "GENERATOR_HANDOVER", "GEN", "101.000", "2026-09-24T01:00:00Z");
        mockMvc.perform(post("/api/manifests/" + manifestNo + "/events")
                        .contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/manifests/" + manifestNo + "/timeline"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejectsInvalidWeightsEmptyItemsAndUnknownManifest() throws Exception {
        String zeroWeight = """
                {"manifestNo":"M-BAD-1","generatorParty":"GEN","carrierParty":"CAR","disposerParty":"DIS",
                "items":[{"category":"HW08","packageCount":2,"declaredWeight":0}]}
                """;
        mockMvc.perform(post("/api/manifests").contentType(MediaType.APPLICATION_JSON).content(zeroWeight))
                .andExpect(status().isBadRequest());

        String tooFineWeight = """
                {"manifestNo":"M-BAD-2","generatorParty":"GEN","carrierParty":"CAR","disposerParty":"DIS",
                "items":[{"category":"HW08","packageCount":2,"declaredWeight":10.1234}]}
                """;
        mockMvc.perform(post("/api/manifests").contentType(MediaType.APPLICATION_JSON).content(tooFineWeight))
                .andExpect(status().isBadRequest());

        String emptyItems = """
                {"manifestNo":"M-BAD-3","generatorParty":"GEN","carrierParty":"CAR","disposerParty":"DIS",
                "items":[]}
                """;
        mockMvc.perform(post("/api/manifests").contentType(MediaType.APPLICATION_JSON).content(emptyItems))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/manifests/NO-SUCH"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateManifestNumberConflicts() throws Exception {
        createManifest("M-DUP-001", "100.000", null).andExpect(status().isCreated());
        createManifest("M-DUP-001", "100.000", null).andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions createManifest(String manifestNo,
                                                                             String weight,
                                                                             String tolerance) throws Exception {
        String toleranceField = tolerance == null ? "" : ",\"weightToleranceRatio\":" + tolerance;
        String body = """
                {"manifestNo":"%s","generatorParty":"GEN","carrierParty":"CAR","disposerParty":"DIS"%s,
                "items":[
                  {"category":"HW08-FEI","packageCount":3,"declaredWeight":%s},
                  {"category":"HW49-OTHER","packageCount":2,"declaredWeight":"%s"}
                ]}
                """.formatted(manifestNo, toleranceField, splitWeight(weight, "60"), splitWeight(weight, "40"));
        return mockMvc.perform(post("/api/manifests").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String splitWeight(String total, String part) {
        return new java.math.BigDecimal(total).compareTo(new java.math.BigDecimal("100.000")) == 0
                ? part
                : new java.math.BigDecimal(total).multiply(new java.math.BigDecimal(part))
                        .divide(new java.math.BigDecimal("100")).stripTrailingZeros().toPlainString();
    }

    private org.springframework.test.web.servlet.ResultActions submitEvent(String manifestNo, String eventNo,
                                                                          String type, String actor,
                                                                          String weight, String occurredAt)
            throws Exception {
        return mockMvc.perform(post("/api/manifests/" + manifestNo + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventBody(eventNo, type, actor, weight, occurredAt)));
    }

    private String eventBody(String eventNo, String type, String actor, String weight, String occurredAt) {
        return """
                {"eventNo":"%s","eventType":"%s","actorParty":"%s","weightValue":"%s","occurredAt":"%s"}
                """.formatted(eventNo, type, actor, weight, occurredAt);
    }
}
