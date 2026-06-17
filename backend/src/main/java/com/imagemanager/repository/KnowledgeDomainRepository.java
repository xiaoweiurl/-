package com.imagemanager.repository;

import com.imagemanager.entity.KnowledgeDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeDomainRepository extends JpaRepository<KnowledgeDomain, Integer> {
    Optional<KnowledgeDomain> findByCode(String code);

    // 按 company 过滤
    List<KnowledgeDomain> findByCompanyOrderByCreatedAtDesc(String company);
    Optional<KnowledgeDomain> findByCodeAndCompany(String code, String company);
}
