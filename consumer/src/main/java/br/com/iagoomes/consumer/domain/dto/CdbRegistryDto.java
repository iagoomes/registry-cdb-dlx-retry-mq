package br.com.iagoomes.consumer.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CdbRegistryDto {

    private String registryId;
    private String clientId;
    private BigDecimal amount;
    private Integer durationDays;
    private BigDecimal interestRate;
    private LocalDateTime createdAt;
}
