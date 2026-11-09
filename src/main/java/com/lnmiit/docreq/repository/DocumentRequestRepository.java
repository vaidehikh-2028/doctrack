package com.lnmiit.docreq.repository;

import com.lnmiit.docreq.domain.RequestStatus;
import com.lnmiit.docreq.model.DocumentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, Long> {

    Optional<DocumentRequest> findByRequestNumber(String requestNumber);

    /**
     * Loads a request with a row lock (SELECT ... FOR UPDATE) before changing its status.
     * A second staff member acting at the same moment waits, then sees the new status and
     * is refused cleanly (E-VER-001 / E-APR-001). @Version stays as a second line of defence.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DocumentRequest r where r.requestNumber = :requestNumber")
    Optional<DocumentRequest> findForUpdate(@Param("requestNumber") String requestNumber);

    List<DocumentRequest> findByStudentIdOrderBySubmittedAtDesc(Long studentId);

    /** Work queues are first-come, first-served (Assignment 6, Section 43.8). */
    List<DocumentRequest> findByStatusInOrderBySubmittedAtAsc(Collection<RequestStatus> statuses);

    List<DocumentRequest> findAllByOrderBySubmittedAtDesc();

    @Query("select r from DocumentRequest r where r.status in :statuses "
            + "and r.escalatedAt is null and r.updatedAt < :cutoff order by r.updatedAt asc")
    List<DocumentRequest> findOverdue(@Param("statuses") Collection<RequestStatus> statuses,
                                      @Param("cutoff") Instant cutoff);

    long countByEscalatedAtIsNotNullAndStatusIn(Collection<RequestStatus> statuses);

    @Query("select r.status, count(r) from DocumentRequest r group by r.status")
    List<Object[]> countByStatus();

    @Query("select r from DocumentRequest r where r.status = com.lnmiit.docreq.domain.RequestStatus.COMPLETED")
    List<DocumentRequest> findCompleted();
}
