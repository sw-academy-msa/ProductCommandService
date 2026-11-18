package com.msa.product.domain.outbox.repository;

import com.msa.product.domain.outbox.entity.ProjectionSequence;

import jakarta.persistence.EntityManager;

import org.springframework.transaction.annotation.*;

/** INSERT RETURNING의 결과 처리를 JPA로 수행합니다. 전체 clear 없이 이 버전 엔티티만 갱신합니다. */
public class ProjectionSequenceRepositoryCustomImpl implements ProjectionSequenceRepositoryCustom {
    private final EntityManager em;

    public ProjectionSequenceRepositoryCustomImpl(EntityManager em) {
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectionSequence advance(String id) {
        // 같은 트랜잭션에서 앞서 바꾼 완판 시각·원본 변경을 먼저 반영해 다음 native 갱신과 충돌하지 않게 합니다.
        em.flush();
        em.createNativeQuery(
                        """
                        insert into product.projection_sequences (id,version) values (:id,1)
                        on conflict (id) do update set version=product.projection_sequences.version+1
                        returning version
                        """)
                .setParameter("id", id)
                .getSingleResult();
        var sequence = em.find(ProjectionSequence.class, id);
        // native 변경으로 오래된 version이 남지 않게 대상만 refresh합니다. 상품·문의의 미반영 상태는 유지합니다.
        em.refresh(sequence);
        return sequence;
    }
}
