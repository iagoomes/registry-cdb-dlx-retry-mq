package br.com.iagoomes.application.controller;

import br.com.iagoomes.application.service.CdbRegistryService;
import br.com.iagoomes.domain.dto.CdbRegistryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/cdb-registry")
@RequiredArgsConstructor
public class CdbRegistryController {

    private final CdbRegistryService cdbRegistryService;

    @PostMapping
    public ResponseEntity<CdbRegistryDto> createCdbRegistry(@RequestBody CdbRegistryDto request) {
        log.info("Received request to create CDB registry: {}", request);
        CdbRegistryDto response = cdbRegistryService.createCdbRegistry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}