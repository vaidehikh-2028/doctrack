package com.lnmiit.docreq.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.lnmiit.docreq.domain.Enums.NotificationType;
import com.lnmiit.docreq.repository.NotificationRepository;
import com.lnmiit.docreq.repository.UserRepository;
import com.lnmiit.docreq.security.JwtService;
import com.lnmiit.docreq.service.SlaEscalationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, access control, end-to-end lifecycle, administration, reports and SLA escalation. */
class SystemTest extends IntegrationTestBase {

    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired SlaEscalationService sla;
    @Autowired Clock clock;
    @Value("${app.jwt.secret}") String jwtSecret;

    private org.springframework.test.web.servlet.ResultActions login(String user, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", user, "password", password))));
    }

    @Test
    @DisplayName("TC-22 login with valid and invalid credentials; failures share one generic message")
    void tc22_login() throws Exception {
        login("student1", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
        login("student1@lnmiit.ac.in", PASSWORD).andExpect(status().isOk());

        String wrongPassword = login("student1", "wrong-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E-AUTH-401"))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = login("nobody", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrongPassword).get("message").asText())
                .isEqualTo(json.readTree(unknownUser).get("message").asText())
                .isEqualTo("Invalid credentials. Please check your details and try again.");
    }

    @Test
    @DisplayName("TC-23 no page beyond login without authentication; expired session -> E-AUTH-440")
    void tc23_authenticationRequired() throws Exception {
        mvc.perform(get("/api/requests/mine")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E-AUTH-401"));
        mvc.perform(get("/api/staff/requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/requests/mine").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        JwtService expiredIssuer = new JwtService(jwtSecret, Duration.ofMinutes(-5), clock);
        String expired = expiredIssuer.issue(users.findByUsernameIgnoreCase("student1").orElseThrow());
        mvc.perform(get("/api/requests/mine").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("E-AUTH-440"))
                .andExpect(jsonPath("$.message").value("Your session has expired. Please log in again."));

        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-24 end-to-end lifecycle of an approval-required request")
    void tc24_endToEnd() throws Exception {
        String number = submitValid("student1", CHARACTER);
        verify("staff1", number, "VERIFIED", null, "Eligible").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FORWARDED"));
        decide("approver1", number, "APPROVED", null, "OK").andExpect(status().isOk());
        mvc.perform(post("/api/staff/requests/{n}/start-processing", number).header("Authorization", bearer("staff1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(multipart("/api/staff/requests/{n}/complete", number)
                        .file(pdf("character_certificate.pdf", 40 * 1024))
                        .param("remarks", "Signed by Dean")
                        .header("Authorization", bearer("staff1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        JsonNode d = detail("student1", number);
        assertThat(d.get("history").findValuesAsText("status"))
                .containsExactly("SUBMITTED", "VERIFIED", "FORWARDED", "APPROVED", "PROCESSING", "COMPLETED");
        long fileId = d.get("files").get(0).get("id").asLong();

        // FR-024 the student downloads the document; BR-009 nobody else can
        mvc.perform(get("/api/requests/{n}/files/{f}", number, fileId).header("Authorization", bearer("student1")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(pdfBytes(40 * 1024)));
        mvc.perform(get("/api/requests/{n}/files/{f}", number, fileId).header("Authorization", bearer("staff1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/{n}/files/{f}", number, fileId).header("Authorization", bearer("student2")))
                .andExpect(status().isNotFound());

        // FR-025 to FR-027: the student was notified at every step
        JsonNode mine = getJson("student1", "/api/notifications/mine");
        assertThat(mine.findValuesAsText("type")).contains("SUBMISSION_CONFIRMATION", "STATUS_CHANGE",
                "APPROVAL_DECISION", "DOCUMENT_READY");
    }

    @Test
    @DisplayName("FR-028 admin creates and deactivates a user; deactivated user cannot log in")
    void adminManagesUsers() throws Exception {
        Map<String, Object> cmd = Map.of("username", "student9", "name", "Kabir Rao", "email", "student9@lnmiit.ac.in",
                "role", "STUDENT", "password", "Secret@123");
        String body = mvc.perform(post("/api/admin/users").header("Authorization", bearer("admin1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cmd)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();
        login("student9", "Secret@123").andExpect(status().isOk());

        mvc.perform(post("/api/admin/users").header("Authorization", bearer("admin1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cmd)))
                .andExpect(status().isConflict());

        mvc.perform(patch("/api/admin/users/{id}", id).header("Authorization", bearer("admin1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountStatus\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("INACTIVE"));
        login("student9", "Secret@123").andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/users").header("Authorization", bearer("staff1")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-030 report summary counts requests by status")
    void reports() throws Exception {
        submitValid("student2", BONAFIDE);
        JsonNode report = getJson("admin1", "/api/reports/summary");
        assertThat(report.get("total").asLong()).isPositive();
        assertThat(report.get("byStatus").get("SUBMITTED").asLong()).isPositive();
        mvc.perform(get("/api/reports/summary").header("Authorization", bearer("student1")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SLA: a request waiting more than 48 hours is escalated once to staff and admins")
    void slaEscalation() throws Exception {
        String number = submitValid("student2", BONAFIDE);
        Instant now = clock.instant();

        sla.escalateOverdue(now.plus(Duration.ofHours(47)));
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.SLA_ESCALATION)).isEmpty();

        sla.escalateOverdue(now.plus(Duration.ofHours(49)));
        long staffAndAdmins = users.findAll().stream()
                .filter(u -> u.isActive() && (u.getRole().name().equals("STAFF") || u.getRole().name().equals("ADMIN")))
                .count();
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.SLA_ESCALATION))
                .hasSize((int) staffAndAdmins);
        assertThat(detail("staff1", number).get("status").asText()).isEqualTo("SUBMITTED");

        // escalated only once per stage
        sla.escalateOverdue(now.plus(Duration.ofHours(60)));
        assertThat(notifications.findByRequestNumberAndType(number, NotificationType.SLA_ESCALATION))
                .hasSize((int) staffAndAdmins);
    }
}
