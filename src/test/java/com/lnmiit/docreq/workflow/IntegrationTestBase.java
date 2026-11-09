package com.lnmiit.docreq.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnmiit.docreq.config.DataSeeder;
import com.lnmiit.docreq.repository.DocumentTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole application (H2 in memory, local file storage, seeded demo data)
 * and drives it over HTTP with MockMvc, exactly as the web UI does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    static final String PASSWORD = "Demo@1234";
    static final String FIELD = DataSeeder.PROGRAM_FIELD;
    static final String BONAFIDE = "DT-01";      // no approval, attachment required
    static final String CHARACTER = "DT-02";     // approval required, no attachment

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DocumentTypeRepository types;

    private final Map<String, String> tokens = new HashMap<>();

    String token(String username) throws Exception {
        String cached = tokens.get(username);
        if (cached != null) return cached;
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String t = json.readTree(body).get("token").asText();
        tokens.put(username, t);
        return t;
    }

    String bearer(String username) throws Exception {
        return "Bearer " + token(username);
    }

    Long typeId(String code) {
        return types.findByCode(code).orElseThrow().getId();
    }

    static byte[] pdfBytes(int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) ' ');
        byte[] header = "%PDF-1.4\n".getBytes();
        System.arraycopy(header, 0, b, 0, Math.min(header.length, size));
        return b;
    }

    static MockMultipartFile pdf(String name, int size) {
        return new MockMultipartFile("file", name, "application/pdf", pdfBytes(size));
    }

    ResultActions submit(String student, String typeCode, String program, String purpose, MockMultipartFile file)
            throws Exception {
        Map<String, String> fields = new HashMap<>();
        if (program != null) fields.put(FIELD, program);
        MockMultipartHttpServletRequestBuilder req = multipart("/api/requests");
        if (file != null) req.file(file);
        return mvc.perform(req
                .param("documentTypeId", String.valueOf(typeId(typeCode)))
                .param("purpose", purpose)
                .param("fields", json.writeValueAsString(fields))
                .header("Authorization", bearer(student)));
    }

    /** Submits a valid request and returns its request number. */
    String submitValid(String student, String typeCode) throws Exception {
        MockMultipartFile file = BONAFIDE.equals(typeCode) || "DT-03".equals(typeCode)
                ? pdf("bonafide_proof.pdf", 200 * 1024) : null;
        String body = submit(student, typeCode, "B.Tech CSE / Semester 5", "Internship application", file)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("requestNumber").asText();
    }

    ResultActions verify(String staff, String number, String outcome, String missingInfo, String remarks)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("outcome", outcome);
        body.put("missingInfo", missingInfo);
        body.put("remarks", remarks);
        return mvc.perform(post("/api/staff/requests/{n}/verify", number)
                .header("Authorization", bearer(staff))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    ResultActions decide(String approver, String number, String decision, String reason, String comments)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", decision);
        body.put("reason", reason);
        body.put("comments", comments);
        return mvc.perform(post("/api/approvals/{n}/decision", number)
                .header("Authorization", bearer(approver))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    JsonNode detail(String user, String number) throws Exception {
        String body = mvc.perform(get("/api/requests/{n}", number).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    JsonNode getJson(String user, String path) throws Exception {
        String body = mvc.perform(get(path).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
}
