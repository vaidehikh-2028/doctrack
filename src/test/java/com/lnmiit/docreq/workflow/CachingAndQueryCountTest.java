package com.lnmiit.docreq.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The principal and document-type caches never serve stale access decisions, and the list views
 * run a fixed number of SQL statements however many students and document types they show.
 * Statements are counted with Hibernate statistics (enabled in application-test.yml).
 */
class CachingAndQueryCountTest extends IntegrationTestBase {

    @Autowired EntityManagerFactory emf;

    // ---------- the principal cache must not outlive an admin's change ----------

    @Test
    @DisplayName("FR-028 with caching: a deactivated user's unexpired token is refused on the very next request")
    void deactivationEvictsCachedPrincipal() throws Exception {
        long id = createUser("cache1", "STUDENT");
        mine("cache1").andExpect(status().isOk());
        mine("cache1").andExpect(status().isOk());          // now served from the principal cache

        updateUser(id, "{\"accountStatus\":\"INACTIVE\"}");

        mine("cache1").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A role change applies on the next request, not when the cache entry expires")
    void roleChangeEvictsCachedPrincipal() throws Exception {
        long id = createUser("cache2", "STUDENT");
        mine("cache2").andExpect(status().isOk());
        staffQueue("cache2").andExpect(status().isForbidden());

        updateUser(id, "{\"role\":\"STAFF\"}");

        staffQueue("cache2").andExpect(status().isOk());
        mine("cache2").andExpect(status().isOk());          // the token is unchanged; the role comes from the cache
    }

    // ---------- document types ----------

    @Test
    @DisplayName("A new document type is offered at once, and a repeated read runs no SQL at all")
    void offeredTypesCache() throws Exception {
        getJson("student1", "/api/document-types");
        long repeat = statements(() -> getJson("student1", "/api/document-types"));
        assertThat(repeat).as("principal and document types both cached").isZero();

        createType("DT-C0");
        assertThat(getJson("student1", "/api/document-types").findValuesAsText("code")).contains("DT-C0");
    }

    // ---------- N+1 ----------

    @Test
    @DisplayName("Staff queue and 'my requests' cost the same number of queries for 1 or 10 rows")
    void listViewsHaveNoNPlusOne() throws Exception {
        createType("DT-C1");
        createUser("cache3", "STUDENT");
        submitValid("cache3", "DT-C1");
        getJson("staff1", "/api/staff/requests");           // warm the principal cache
        long queueBefore = statements(() -> getJson("staff1", "/api/staff/requests"));
        long mineBefore = statements(() -> getJson("cache3", "/api/requests/mine"));

        // three more students and three more document types, every combination submitted
        List<String> students = List.of("cache4", "cache5", "cache6");
        List<String> codes = List.of("DT-C2", "DT-C3", "DT-C4");
        for (String code : codes) createType(code);
        for (String s : students) {
            createUser(s, "STUDENT");
            for (String code : codes) submitValid(s, code);
        }
        for (String code : codes) submitValid("cache3", code);

        JsonNode[] queue = new JsonNode[1];
        long queueAfter = statements(() -> queue[0] = getJson("staff1", "/api/staff/requests"));
        JsonNode[] mine = new JsonNode[1];
        long mineAfter = statements(() -> mine[0] = getJson("cache3", "/api/requests/mine"));

        System.out.printf("SQL statements per request: staff queue %d (%d rows, 4 students, 4 types) vs %d before;"
                + " my requests %d (%d rows) vs %d (1 row)%n",
                queueAfter, queue[0].size(), queueBefore, mineAfter, mine[0].size(), mineBefore);
        assertThat(queue[0].findValuesAsText("studentUsername")).contains("cache3", "cache4", "cache5", "cache6");
        assertThat(mine[0].size()).isEqualTo(4);
        assertThat(queueAfter).as("staff queue statements").isEqualTo(queueBefore);
        assertThat(mineAfter).as("my requests statements").isEqualTo(mineBefore);
    }

    // ---------- helpers ----------

    interface Call { void run() throws Exception; }

    long statements(Call call) throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        call.run();
        return stats.getPrepareStatementCount();
    }

    org.springframework.test.web.servlet.ResultActions mine(String user) throws Exception {
        return mvc.perform(get("/api/requests/mine").header("Authorization", bearer(user)));
    }

    org.springframework.test.web.servlet.ResultActions staffQueue(String user) throws Exception {
        return mvc.perform(get("/api/staff/requests").header("Authorization", bearer(user)));
    }

    long createUser(String username, String role) throws Exception {
        Map<String, Object> cmd = Map.of("username", username, "name", "Cache Test " + username,
                "email", username + "@lnmiit.ac.in", "role", role, "password", PASSWORD);
        String body = mvc.perform(post("/api/admin/users").header("Authorization", bearer("admin1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cmd)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    void updateUser(long id, String patchJson) throws Exception {
        mvc.perform(patch("/api/admin/users/{id}", id).header("Authorization", bearer("admin1"))
                        .contentType(MediaType.APPLICATION_JSON).content(patchJson))
                .andExpect(status().isOk());
    }

    /** An active type with one required field, no attachment and no approval. */
    void createType(String code) throws Exception {
        Map<String, Object> type = Map.of("code", code, "name", "Cache Test " + code, "description", "test",
                "requiredFields", List.of(FIELD), "attachmentRequired", false, "approvalRequired", false,
                "processingDays", 2, "active", true);
        mvc.perform(post("/api/admin/document-types").header("Authorization", bearer("admin1"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(type)))
                .andExpect(status().isCreated());
    }
}
