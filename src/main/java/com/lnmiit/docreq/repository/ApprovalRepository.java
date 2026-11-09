package com.lnmiit.docreq.repository;

import com.lnmiit.docreq.domain.Enums.ApprovalDecision;
import com.lnmiit.docreq.model.Approval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    List<Approval> findByDecisionOrderByCreatedAtAsc(ApprovalDecision decision);

    Optional<Approval> findFirstByRequestIdOrderByCreatedAtDesc(Long requestId);

    List<Approval> findByRequestIdOrderByCreatedAtAsc(Long requestId);

    List<Approval> findByDecidedByIdOrderByDecidedAtDesc(Long approverId);

    long countByRequestId(Long requestId);
}
