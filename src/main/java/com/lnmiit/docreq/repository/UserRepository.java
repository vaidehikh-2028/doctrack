package com.lnmiit.docreq.repository;

import com.lnmiit.docreq.domain.Enums.AccountStatus;
import com.lnmiit.docreq.domain.Enums.UserRole;
import com.lnmiit.docreq.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRoleAndAccountStatus(UserRole role, AccountStatus status);

    List<User> findAllByOrderByRoleAscUsernameAsc();
}
