package com.openride.payouts.controller;

import com.openride.payouts.dto.EarningsSummaryResponse;
import com.openride.payouts.dto.LedgerEntryResponse;
import com.openride.payouts.dto.WalletResponse;
import com.openride.payouts.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for driver wallet and earnings endpoints.
 */
@Tag(name = "Wallet & Earnings", description = "Driver wallet and earnings management")
@RestController
@RequestMapping("/v1/payouts")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Get wallet balance", description = "Get driver wallet balance including available and pending amounts")
    @GetMapping("/balance")
    public ResponseEntity<WalletResponse> getBalance(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId
    ) {
        WalletResponse wallet = walletService.getWallet(driverId);
        return ResponseEntity.ok(wallet);
    }

    @Operation(summary = "Get earnings summary", description = "Get comprehensive earnings summary for driver")
    @GetMapping("/earnings")
    public ResponseEntity<EarningsSummaryResponse> getEarnings(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId
    ) {
        EarningsSummaryResponse summary = walletService.getEarningsSummary(driverId);
        return ResponseEntity.ok(summary);
    }

    @Operation(summary = "Get transaction history", description = "Get paginated transaction history for driver")
    @GetMapping("/history")
    public ResponseEntity<Page<LedgerEntryResponse>> getTransactionHistory(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<LedgerEntryResponse> history = walletService.getTransactionHistory(driverId, pageable);
        return ResponseEntity.ok(history);
    }
}
