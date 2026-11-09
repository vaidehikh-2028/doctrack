package com.lnmiit.docreq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** Users are authenticated with JWTs against our own table, so Boot's default in-memory user is excluded. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DocReqApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocReqApplication.class, args);
    }
}
