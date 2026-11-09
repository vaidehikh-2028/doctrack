package com.lnmiit.docreq.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.lnmiit.docreq.domain.Enums.ApprovalDecision;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.model.Approval;
import com.lnmiit.docreq.model.DocumentRequest;
import com.lnmiit.docreq.repository.ApprovalRepository;
import com.lnmiit.docreq.repository.DocumentRequestRepository;
import com.lnmiit.docreq.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-008 Verify Request (TC-10 to TC-15) and UC-011 Approve Request (TC-16 to TC-21). */
class VerifyAndApproveTest extends IntegrationTestBase {

    @Autowired DocumentRequestRepository requests;
    @Autowired ApprovalRepository approvals;
    @Autowired NotificationRepository notifications;

    private List<String> statuses(JsonNode detail) {
        return detail.get("history").findValuesAsText("status");
    }

    private long approvalCount(String number) {
        DocumentRequest r = requests.findByRequestNumber(number).orElseThrow();
        return approvals.countByRequestId(r.getId());
    }

    private String forwarded() throws Exception {
        String number = submitValid("student1", CHARACTER);
        verify("staff1", number, "VERIFIED", null, "Eligible, forward for approval").andExpect(status().isOk());
        return number;
    }

    // ---------------- UC-008 ----------------

    @Test
    @DisplayName("TC-10 verify a request that needs no approval -> Processing, no Approval record")
    void tc10_verifyNoApproval() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        assertThat(getJson("staff1", "/api/staff/requests?status=SUBMITTED").findValuesAsText("requestNumber"))
                .contains(number);

        verify("staff1", number, "VERIFIED", null, "Details match records")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        JsonNode d = detail("staff1", number);
        assertThat(statuses(d)).containsExactly("SUBMITTED", "VERIFIED", "PROCESSING");
        assertThat(d.get("history").get(1).get("remarks").asText()).isEqualTo("Details match records");
        assertThat(d.get("history").get(1).get("changedBy").asText()).isEqualTo("Ramesh Gupta");
        assertThat(approvalCount(number)).isZero();
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.STATUS_CHANGE)).hasSize(1);
        assertThat(getJson("staff1", "/api/staff/requests?status=SUBMITTED").findValuesAsText("requestNumber"))
                .doesNotContain(number);
    }

    @Test
    @DisplayName("TC-11 verify a request that needs approval -> Forwarded with a Pending approval")
    void tc11_verifyForwards() throws Exception {
        String number = submitValid("student1", CHARACTER);
        verify("staff1", number, "VERIFIED", null, "Eligible, forward for approval")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FORWARDED"))
                .andExpect(jsonPath("$.approvals[0].decision").value("PENDING"));
        assertThat(statuses(detail("staff1", number))).containsExactly("SUBMITTED", "VERIFIED", "FORWARDED");
        assertThat(getJson("approver1", "/api/approvals/pending").findValuesAsText("requestNumber")).contains(number);
    }

    @Test
    @DisplayName("TC-12 flag Incomplete with missing information -> student is told what is missing")
    void tc12_flagIncomplete() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        verify("staff1", number, "INCOMPLETE", "Attach the latest fee receipt", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.missingInfo").value("Attach the latest fee receipt"));

        JsonNode d = detail("student1", number);
        assertThat(d.get("history").get(1).get("remarks").asText()).contains("Attach the latest fee receipt");
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.INCOMPLETE_REQUEST))
                .singleElement()
                .satisfies(n -> assertThat(n.getMessage()).contains("Attach the latest fee receipt"));
        assertThat(approvalCount(number)).isZero();
    }

    @Test
    @DisplayName("TC-13 Incomplete with empty or blank missing information -> E-VER-002, nothing changes")
    void tc13_incompleteWithoutInfo() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        for (String missing : new String[]{"", "   "}) {
            verify("staff1", number, "INCOMPLETE", missing, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("E-VER-002"))
                    .andExpect(jsonPath("$.message").value("Specify the missing information."));
        }
        JsonNode d = detail("staff1", number);
        assertThat(d.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(d.get("history")).hasSize(1);
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.INCOMPLETE_REQUEST)).isEmpty();
    }

    @Test
    @DisplayName("TC-14 second staff member verifying the same request -> E-VER-001, no duplicates")
    void tc14_secondVerifierRefused() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        verify("staff2", number, "VERIFIED", null, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        verify("staff1", number, "VERIFIED", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-VER-001"));
        assertThat(statuses(detail("staff1", number))).containsExactly("SUBMITTED", "VERIFIED", "PROCESSING");
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.STATUS_CHANGE)).hasSize(1);
    }

    @Test
    @DisplayName("TC-15 a student cannot reach the staff queue or verification -> E-AUTH-403")
    void tc15_studentCannotVerify() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        mvc.perform(get("/api/staff/requests").header("Authorization", bearer("student1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("E-AUTH-403"));
        verify("student1", number, "VERIFIED", null, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("E-AUTH-403"));
        assertThat(detail("student1", number).get("status").asText()).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("E-VER-003 cannot verify a request missing mandatory details")
    void verifyRefusedWhenDataIncomplete() throws Exception {
        // Admin adds a new required field after the request was submitted
        String number = submitValid("student1", "DT-03");
        Long id = typeId("DT-03");
        Map<String, Object> cmd = Map.of("code", "DT-03", "name", "Enrollment Certificate",
                "description", "x", "requiredFields", List.of(FIELD, "Roll number"),
                "attachmentRequired", true, "approvalRequired", false, "processingDays", 2, "active", true);
        Map<String, Object> restore = new java.util.HashMap<>(cmd);
        restore.put("requiredFields", List.of(FIELD));
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/admin/document-types/{id}", id).header("Authorization", bearer("admin1"))
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cmd)))
                    .andExpect(status().isOk());
            verify("staff1", number, "VERIFIED", null, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("E-VER-003"));
        } finally {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/admin/document-types/{id}", id).header("Authorization", bearer("admin1"))
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(restore)));
        }
    }

    @Test
    @DisplayName("Incomplete -> student resubmits -> Submitted again")
    void resubmitAfterIncomplete() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        verify("staff1", number, "INCOMPLETE", "Semester is wrong", null).andExpect(status().isOk());

        mvc.perform(multipart("/api/requests/{n}/resubmit", number)
                        .param("purpose", "Internship application")
                        .param("fields", json.writeValueAsString(Map.of(FIELD, "B.Tech CSE / Semester 6")))
                        .header("Authorization", bearer("student1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.fieldValues['Program / Semester']").value("B.Tech CSE / Semester 6"));

        // a request that is not Incomplete cannot be resubmitted
        mvc.perform(multipart("/api/requests/{n}/resubmit", number)
                        .param("purpose", "x").param("fields", json.writeValueAsString(Map.of(FIELD, "y")))
                        .header("Authorization", bearer("student1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-VER-001"));
    }

    // ---------------- UC-011 ----------------

    @Test
    @DisplayName("TC-16 approve a forwarded request -> Approved, staff notified")
    void tc16_approve() throws Exception {
        String number = forwarded();
        decide("approver1", number, "APPROVED", null, "Approved as per rules")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvals[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$.approvals[0].decidedBy").value("Dr. Anil Kumar"))
                .andExpect(jsonPath("$.approvals[0].comments").value("Approved as per rules"))
                .andExpect(jsonPath("$.approvals[0].decidedAt").isNotEmpty());

        assertThat(statuses(detail("staff1", number))).endsWith("APPROVED");
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.STATUS_CHANGE))
                .anyMatch(n -> n.getMessage().contains("Please prepare the document"));
        assertThat(getJson("approver1", "/api/approvals/pending").findValuesAsText("requestNumber"))
                .doesNotContain(number);
        assertThat(getJson("approver1", "/api/approvals/history").findValuesAsText("requestNumber"))
                .contains(number);
    }

    @Test
    @DisplayName("TC-17 reject with a reason -> Rejected, student told the reason")
    void tc17_reject() throws Exception {
        String number = forwarded();
        decide("approver1", number, "REJECTED", "Not eligible for this certificate", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.approvals[0].decision").value("REJECTED"))
                .andExpect(jsonPath("$.approvals[0].comments").value("Not eligible for this certificate"));
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.APPROVAL_DECISION))
                .singleElement()
                .satisfies(n -> assertThat(n.getMessage()).contains("Not eligible for this certificate"));
    }

    @Test
    @DisplayName("TC-18 reject with empty or blank reason -> E-APR-002, approval stays Pending")
    void tc18_rejectWithoutReason() throws Exception {
        String number = forwarded();
        for (String reason : new String[]{"", "   "}) {
            decide("approver1", number, "REJECTED", reason, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("E-APR-002"))
                    .andExpect(jsonPath("$.message").value("A reason is required to reject a request."));
        }
        JsonNode d = detail("approver1", number);
        assertThat(d.get("status").asText()).isEqualTo("FORWARDED");
        assertThat(d.get("approvals").get(0).get("decision").asText()).isEqualTo("PENDING");
        assertThat(statuses(d)).containsExactly("SUBMITTED", "VERIFIED", "FORWARDED");
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.APPROVAL_DECISION)).isEmpty();
    }

    @Test
    @DisplayName("TC-19 reject with the shortest valid reason (1 character)")
    void tc19_oneCharacterReason() throws Exception {
        String number = forwarded();
        decide("approver1", number, "REJECTED", "x", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.approvals[0].comments").value("x"));
    }

    @Test
    @DisplayName("TC-20 second decision on an already decided request -> E-APR-001")
    void tc20_secondDecision() throws Exception {
        String number = forwarded();
        decide("approver1", number, "APPROVED", null, null).andExpect(status().isOk());
        decide("approver2", number, "REJECTED", "Duplicate request", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-APR-001"));
        JsonNode d = detail("approver1", number);
        assertThat(d.get("status").asText()).isEqualTo("APPROVED");
        assertThat(d.get("approvals")).hasSize(1);
        assertThat(approvals.findByRequestIdOrderByCreatedAtAsc(
                requests.findByRequestNumber(number).orElseThrow().getId()))
                .extracting(Approval::getDecision).containsExactly(ApprovalDecision.APPROVED);
    }

    @Test
    @DisplayName("TC-21 a rejected request cannot move to document preparation (BR-006)")
    void tc21_rejectedIsFinal() throws Exception {
        String number = forwarded();
        decide("approver1", number, "REJECTED", "Not eligible", null).andExpect(status().isOk());

        mvc.perform(post("/api/staff/requests/{n}/start-processing", number).header("Authorization", bearer("staff1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-VER-001"));
        mvc.perform(multipart("/api/staff/requests/{n}/complete", number).file(pdf("doc.pdf", 1024))
                        .header("Authorization", bearer("staff1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-VER-001"));

        JsonNode d = detail("staff1", number);
        assertThat(d.get("status").asText()).isEqualTo("REJECTED");
        assertThat(statuses(d)).containsExactly("SUBMITTED", "VERIFIED", "FORWARDED", "REJECTED");
        assertThat(d.get("files")).isEmpty();
    }

    @Test
    @DisplayName("NFR-013 an approver cannot see requests that were never forwarded")
    void approverSeesOnlyForwarded() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        mvc.perform(get("/api/requests/{n}", number).header("Authorization", bearer("approver1")))
                .andExpect(status().isNotFound());
    }
}
