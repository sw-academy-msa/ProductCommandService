package com.msa.product.domain.outbox.repository;

import com.msa.product.domain.outbox.entity.ProjectionSequence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 공개 조회 버전과 완판 시각을 관리하며 원자적 버전 증가는 커스텀 JPA 구현에 위임합니다. */
public interface ProjectionSequenceRepository
        extends JpaRepository<ProjectionSequence, String>, ProjectionSequenceRepositoryCustom {}
