package com.lnmiit.docreq.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.repository.DocumentRequestRepository;
import com.lnmiit.docreq.repository.NotificationRepository;
import com.lnmiit.docreq.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-005 Submit Document Request: TC-01 to TC-09 through the REST API. */
class SubmitRequestTest extends IntegrationTestBase {

    @Autowired DocumentRequestRepository requests;
    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;

    @Test
    @DisplayName("TC-01 submit a valid Bonafide Certificate request")
    void tc01_validSubmission() throws Exception {
        JsonNode offered = getJson("student1", "/api/document-types");
        assertThat(offered.findValuesAsText("name")).contains("Bonafide Certificate");

        submit("student1", BONAFIDE, "B.Tech CSE / Semester 5", "Internship application",
                pdf("bonafide_proof.pdf", 200 * 1024))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestNumber").value(org.hamcrest.Matchers.matchesPattern("REQ-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("TC-02 mandatory field empty -> E-REQ-001, nothing created")
    void tc02_mandatoryFieldEmpty() throws Exception {
        long before = requests.count();
        submit("student1", BONAFIDE, "", "Internship application", pdf("bonafide_proof.pdf", 200 * 1024))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-REQ-001"))
                .andExpect(jsonPath("$.message").value("Please complete the highlighted fields."))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Program / Semester is required."));
        assertThat(requests.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-03 purpose of exactly 250 characters is stored without truncation")
    void tc03_purpose250() throws Exception {
        String purpose = "a".repeat(250);
        String body = submit("student1", BONAFIDE, "B.Tech CSE / Semester 5", purpose, pdf("p.pdf", 1024))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String number = json.readTree(body).get("requestNumber").asText();
        assertThat(detail("student1", number).get("purpose").asText()).hasSize(250);
    }

    @Test
    @DisplayName("TC-04 purpose of 251 characters is refused")
    void tc04_purpose251() throws Exception {
        long before = requests.count();
        submit("student1", BONAFIDE, "B.Tech CSE / Semester 5", "a".repeat(251), pdf("p.pdf", 1024))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("purpose"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Purpose must be at most 250 characters."));
        assertThat(requests.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-05 attachment of exactly 5 MB is accepted and linked")
    void tc05_exactly5mb() throws Exception {
        String body = submit("student1", BONAFIDE, "B.Tech CSE / Semester 5", "Visa",
                pdf("size_5mb.pdf", 5 * 1024 * 1024))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode d = detail("student1", json.readTree(body).get("requestNumber").asText());
        assertThat(d.get("files").get(0).get("sizeBytes").asLong()).isEqualTo(5L * 1024 * 1024);
        assertThat(d.get("files").get(0).get("kind").asText()).isEqualTo("ATTACHMENT");
    }

    @Test
    @DisplayName("TC-06 attachment of 5 MB + 1 KB -> E-REQ-003")
    void tc06_over5mb() throws Exception {
        long before = requests.count();
        submit("student1", BONAFIDE, "B.Tech CSE / Semester 5", "Visa",
                pdf("size_5mb_plus.pdf", 5 * 1024 * 1024 + 1024))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-REQ-003"))
                .andExpect(jsonPath("$.message").value("Only PDF, JPG or PNG files up to 5 MB are allowed."));
        assertThat(requests.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-07 unsupported file type and missing attachment -> E-REQ-003")
    void tc07_badOrMissingFile() throws Exception {
        long before = requests.count();
        MockMultipartFile exe = new MockMultipartFile("file", "setup.exe", "application/octet-stream",
                new byte[]{0x4D, 0x5A, 0, 0, 0, 0, 0, 0});
        submit("student1", BONAFIDE, "B.Tech CSE / Semester 5", "Visa", exe)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PDF, JPG or PNG files up to 5 MB are allowed."));
        submit("student1", BONAFIDE, "B.Tech CSE / Semester 5", "Visa", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E-REQ-003"))
                .andExpect(jsonPath("$.message").value("A supporting file is required."));
        assertThat(requests.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("TC-08 document type withdrawn by admin before submission -> E-REQ-002")
    void tc08_typeWithdrawn() throws Exception {
        Map<String, Object> type = Map.of("code", "DT-T8", "name", "Temporary Certificate",
                "description", "test", "requiredFields", java.util.List.of(FIELD),
                "attachmentRequired", true, "approvalRequired", false, "processingDays", 2, "active", true);
        String created = mvc.perform(post("/api/admin/document-types").header("Authorization", bearer("admin1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(type)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        // admin deactivates it while the student is filling in the form
        Map<String, Object> inactive = new java.util.HashMap<>(type);
        inactive.put("active", false);
        mvc.perform(put("/api/admin/document-types/{id}", id).header("Authorization", bearer("admin1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(inactive)))
                .andExpect(status().isOk());

        long before = requests.count();
        submit("student1", "DT-T8", "B.Tech CSE / Semester 5", "Scholarship", pdf("proof.pdf", 150 * 1024))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E-REQ-002"))
                .andExpect(jsonPath("$.message").value("The selected document type is no longer offered."));
        assertThat(requests.count()).isEqualTo(before);
        assertThat(getJson("student1", "/api/document-types").findValuesAsText("code")).doesNotContain("DT-T8");
    }

    @Test
    @DisplayName("TC-09 unique IDs, first history entry, confirmation notification, persistence")
    void tc09_sideEffects() throws Exception {
        String r1 = submitValid("student1", BONAFIDE);
        String r2 = submitValid("student1", BONAFIDE);
        assertThat(r1).isNotEqualTo(r2).matches("REQ-\\d{4}-\\d{6}");

        JsonNode d = detail("student1", r1);
        assertThat(d.get("history")).hasSize(1);
        assertThat(d.get("history").get(0).get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(d.get("history").get(0).get("changedBy").asText()).isEqualTo("Aarav Sharma");
        assertThat(d.get("history").get(0).get("changedAt").isNull()).isFalse();

        assertThat(notifications.findByRequestNumberAndType(r1, NotificationType.SUBMISSION_CONFIRMATION)).hasSize(1);
        Long studentId = users.findByUsernameIgnoreCase("student1").orElseThrow().getId();
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDesc(studentId))
                .anyMatch(n -> r1.equals(n.getRequestNumber())
                        && n.getType() == NotificationType.SUBMISSION_CONFIRMATION);

        JsonNode mine = getJson("student1", "/api/requests/mine");
        assertThat(mine.findValuesAsText("requestNumber")).contains(r1, r2);
    }

    @Test
    @DisplayName("BR-008 a student cannot see another student's request")
    void br008_ownRequestsOnly() throws Exception {
        String number = submitValid("student1", BONAFIDE);
        mvc.perform(get("/api/requests/{n}", number).header("Authorization", bearer("student2")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("E-REQ-004"));
        assertThat(getJson("student2", "/api/requests/mine").findValuesAsText("requestNumber")).doesNotContain(number);
    }
}
