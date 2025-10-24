package br.com.iagoomes.application.service;

import br.com.iagoomes.domain.dto.CdbRegistryDto;
import br.com.iagoomes.infra.mqprovider.producer.CdbRegistryProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CdbRegistryService {

    private final CdbRegistryProducer cdbRegistryProducer;

    public CdbRegistryDto createCdbRegistry(CdbRegistryDto request) {
        log.info("Creating CDB registry for client: {}", request.getClientId());

        CdbRegistryDto cdbRegistry = CdbRegistryDto.builder()
                .registryId(UUID.randomUUID().toString())
                .clientId(request.getClientId())
                .amount(request.getAmount())
                .durationDays(request.getDurationDays())
                .interestRate(request.getInterestRate())
                .createdAt(LocalDateTime.now())
                .build();

        cdbRegistryProducer.sendCdbRegistryCreated(cdbRegistry);

        log.info("CDB registry created successfully: {}", cdbRegistry.getRegistryId());
        return cdbRegistry;
    }
}