package com.foxya.coin.wallet.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VirtualWalletMapping {
    private Long id;
    private Long userId;
    private String network;
    private String hotWalletAddress;
    private String virtualAddress;
    private String ownerAddress;
    private String mappingSeed;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
