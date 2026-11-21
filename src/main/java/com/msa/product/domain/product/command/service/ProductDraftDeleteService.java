package com.msa.product.domain.product.command.service;

import java.util.UUID;

public interface ProductDraftDeleteService {
    void delete(UUID seller, UUID product);
}
