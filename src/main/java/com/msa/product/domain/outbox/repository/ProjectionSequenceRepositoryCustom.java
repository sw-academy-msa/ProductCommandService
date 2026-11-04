package com.msa.product.domain.outbox.repository;

import com.msa.product.domain.outbox.entity.ProjectionSequence;

/** 버전 증가 결과를 현재 영속성 컨텍스트와 동기화해 반환합니다. 호출자의 업무 트랜잭션이 필수입니다. */
public interface ProjectionSequenceRepositoryCustom {
    ProjectionSequence advance(String id);
}
