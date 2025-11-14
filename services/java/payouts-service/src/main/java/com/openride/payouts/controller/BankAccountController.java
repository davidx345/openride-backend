package com.openride.payouts.controller;

import com.openride.payouts.dto.BankAccountRequest;
import com.openride.payouts.dto.BankAccountResponse;
import com.openride.payouts.service.BankAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for bank account management.
 */
@Tag(name = "Bank Accounts", description = "Driver bank account management")
@RestController
@RequestMapping("/v1/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @Operation(summary = "Add bank account", description = "Add and verify a new bank account for driver")
    @PostMapping
    public ResponseEntity<BankAccountResponse> addBankAccount(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId,
            @Valid @RequestBody BankAccountRequest request
    ) {
        BankAccountResponse response = bankAccountService.addBankAccount(driverId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get bank accounts", description = "Get all bank accounts for driver")
    @GetMapping
    public ResponseEntity<List<BankAccountResponse>> getBankAccounts(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId
    ) {
        List<BankAccountResponse> accounts = bankAccountService.getBankAccounts(driverId);
        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "Get primary bank account", description = "Get primary bank account for driver")
    @GetMapping("/primary")
    public ResponseEntity<BankAccountResponse> getPrimaryBankAccount(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId
    ) {
        BankAccountResponse account = bankAccountService.getPrimaryBankAccount(driverId);
        return ResponseEntity.ok(account);
    }

    @Operation(summary = "Set primary account", description = "Set a bank account as primary")
    @PutMapping("/{accountId}/primary")
    public ResponseEntity<BankAccountResponse> setPrimaryAccount(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId,
            @Parameter(description = "Bank account ID", required = true)
            @PathVariable UUID accountId
    ) {
        BankAccountResponse account = bankAccountService.setPrimaryAccount(driverId, accountId);
        return ResponseEntity.ok(account);
    }

    @Operation(summary = "Delete bank account", description = "Delete a bank account")
    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteBankAccount(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId,
            @Parameter(description = "Bank account ID", required = true)
            @PathVariable UUID accountId
    ) {
        bankAccountService.deleteBankAccount(driverId, accountId);
        return ResponseEntity.noContent().build();
    }
}
