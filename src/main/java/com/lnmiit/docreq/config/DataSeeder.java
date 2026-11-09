package com.lnmiit.docreq.config;

import com.lnmiit.docreq.domain.Enums.UserRole;
import com.lnmiit.docreq.model.DocumentType;
import com.lnmiit.docreq.model.User;
import com.lnmiit.docreq.repository.DocumentTypeRepository;
import com.lnmiit.docreq.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds demo users and the test document types of Assignment 8 (DT-01 to DT-04) into
 * an empty database. Disabled in production with app.seed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String PROGRAM_FIELD = "Program / Semester";

    private final UserRepository users;
    private final DocumentTypeRepository types;
    private final PasswordEncoder encoder;
    private final String demoPassword;

    public DataSeeder(UserRepository users, DocumentTypeRepository types, PasswordEncoder encoder,
                      @Value("${app.seed.demo-password:Demo@1234}") String demoPassword) {
        this.users = users;
        this.types = types;
        this.encoder = encoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String hash = encoder.encode(demoPassword);
        users.saveAll(List.of(
                new User("student1", "Aarav Sharma", "student1@lnmiit.ac.in", "9000000001", UserRole.STUDENT, hash),
                new User("student2", "Diya Mehta", "student2@lnmiit.ac.in", "9000000002", UserRole.STUDENT, hash),
                new User("staff1", "Ramesh Gupta", "staff1@lnmiit.ac.in", "9000000011", UserRole.STAFF, hash),
                new User("staff2", "Sunita Verma", "staff2@lnmiit.ac.in", "9000000012", UserRole.STAFF, hash),
                new User("approver1", "Dr. Anil Kumar", "approver1@lnmiit.ac.in", "9000000021", UserRole.APPROVER, hash),
                new User("approver2", "Dr. Priya Singh", "approver2@lnmiit.ac.in", "9000000022", UserRole.APPROVER, hash),
                new User("admin1", "System Admin", "admin1@lnmiit.ac.in", "9000000031", UserRole.ADMIN, hash)));

        types.saveAll(List.of(
                new DocumentType("DT-01", "Bonafide Certificate",
                        "Confirms that you are a currently enrolled student.",
                        List.of(PROGRAM_FIELD), true, false, 2),
                new DocumentType("DT-02", "Character Certificate",
                        "Certifies conduct during your time at the institute. Needs Dean approval.",
                        List.of(PROGRAM_FIELD), false, true, 5),
                new DocumentType("DT-03", "Enrollment Certificate",
                        "Confirms your current enrollment for scholarships and visas.",
                        List.of(PROGRAM_FIELD), true, false, 2),
                new DocumentType("DT-04", "Official Transcript",
                        "Semester-wise grade record. Needs approval from the examination cell.",
                        List.of(PROGRAM_FIELD, "Number of copies"), false, true, 7)));
        log.info("Seeded demo users (password '{}') and 4 document types", demoPassword);
    }
}
