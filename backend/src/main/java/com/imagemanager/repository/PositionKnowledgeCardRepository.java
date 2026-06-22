package com.imagemanager.repository;

import com.imagemanager.entity.PositionKnowledgeCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PositionKnowledgeCardRepository extends JpaRepository<PositionKnowledgeCard, String> {

    Page<PositionKnowledgeCard> findByCompanyAndUserId(String company, String userId, Pageable pageable);

    Page<PositionKnowledgeCard> findByCompany(String company, Pageable pageable);

    Optional<PositionKnowledgeCard> findByIdAndCompany(String id, String company);

    List<PositionKnowledgeCard> findByCompanyAndPositionNameContainingIgnoreCase(String company, String keyword);

    List<PositionKnowledgeCard> findByCompanyAndDepartment(String company, String department);

    long countByCompany(String company);

    long countByCompanyAndUserId(String company, String userId);

    void deleteByIdAndCompany(String id, String company);
}
