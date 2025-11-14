package com.openride.payouts.service;

import com.openride.payouts.dto.BankAccountRequest;
import com.openride.payouts.dto.BankAccountResponse;
import com.openride.payouts.exception.BankAccountNotFoundException;
import com.openride.payouts.exception.PayoutsException;
import com.openride.payouts.model.entity.BankAccount;
import com.openride.payouts.repository.BankAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BankAccountService.
 */
@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private BankVerificationService bankVerificationService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private BankAccountService bankAccountService;

    private UUID driverId;
    private BankAccount bankAccount;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();

        bankAccount = new BankAccount();
        bankAccount.setId(UUID.randomUUID());
        bankAccount.setDriverId(driverId);
        bankAccount.setAccountNumber("0123456789");
        bankAccount.setBankCode("058");
        bankAccount.setBankName("GTBank");
        bankAccount.setAccountName("John Doe");
        bankAccount.setIsVerified(true);
        bankAccount.setIsPrimary(true);
        bankAccount.setCreatedAt(LocalDateTime.now());
        bankAccount.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void addBankAccount_WithValidDetails_ShouldVerifyAndCreateAccount() {
        // Arrange
        BankAccountRequest request = new BankAccountRequest();
        request.setAccountNumber("0123456789");
        request.setBankCode("058");

        when(bankVerificationService.verifyBankAccount("0123456789", "058"))
                .thenReturn(new BankVerificationService.BankAccountVerification("John Doe", "GTBank", true));
        when(bankAccountRepository.findByDriverId(driverId)).thenReturn(Arrays.asList());
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> {
            BankAccount account = inv.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });

        // Act
        BankAccountResponse response = bankAccountService.addBankAccount(driverId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo("0123456789");
        assertThat(response.getBankCode()).isEqualTo("058");
        assertThat(response.getAccountName()).isEqualTo("John Doe");
        assertThat(response.getBankName()).isEqualTo("GTBank");
        assertThat(response.getIsVerified()).isTrue();
        assertThat(response.getIsPrimary()).isTrue(); // First account should be primary

        verify(bankVerificationService).verifyBankAccount("0123456789", "058");
        verify(bankAccountRepository).save(any(BankAccount.class));
        verify(auditService).logAuditEntry(eq("BANK_ACCOUNT"), any(), eq("ADD_BANK_ACCOUNT"), eq(driverId), any(), any());
    }

    @Test
    void addBankAccount_WhenVerificationFails_ShouldThrowException() {
        // Arrange
        BankAccountRequest request = new BankAccountRequest();
        request.setAccountNumber("0123456789");
        request.setBankCode("058");

        when(bankVerificationService.verifyBankAccount("0123456789", "058"))
                .thenReturn(new BankVerificationService.BankAccountVerification("", "", false));

        // Act & Assert
        assertThatThrownBy(() -> bankAccountService.addBankAccount(driverId, request))
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("verification failed");

        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    void addBankAccount_WhenOtherAccountsExist_ShouldNotSetAsPrimary() {
        // Arrange
        BankAccountRequest request = new BankAccountRequest();
        request.setAccountNumber("9876543210");
        request.setBankCode("058");

        BankAccount existingAccount = new BankAccount();
        existingAccount.setId(UUID.randomUUID());
        existingAccount.setDriverId(driverId);
        existingAccount.setIsPrimary(true);

        when(bankVerificationService.verifyBankAccount("9876543210", "058"))
                .thenReturn(new BankVerificationService.BankAccountVerification("Jane Doe", "GTBank", true));
        when(bankAccountRepository.findByDriverId(driverId)).thenReturn(Arrays.asList(existingAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> {
            BankAccount account = inv.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });

        // Act
        BankAccountResponse response = bankAccountService.addBankAccount(driverId, request);

        // Assert
        assertThat(response.getIsPrimary()).isFalse();
        
        ArgumentCaptor<BankAccount> captor = ArgumentCaptor.forClass(BankAccount.class);
        verify(bankAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getIsPrimary()).isFalse();
    }

    @Test
    void getBankAccounts_ShouldReturnAllAccountsForDriver() {
        // Arrange
        BankAccount account1 = createBankAccount("0123456789", true);
        BankAccount account2 = createBankAccount("9876543210", false);

        when(bankAccountRepository.findByDriverId(driverId))
                .thenReturn(Arrays.asList(account1, account2));

        // Act
        List<BankAccountResponse> accounts = bankAccountService.getBankAccounts(driverId);

        // Assert
        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).getIsPrimary()).isTrue();
        assertThat(accounts.get(1).getIsPrimary()).isFalse();
    }

    @Test
    void setPrimaryAccount_ShouldUpdatePrimaryStatus() {
        // Arrange
        UUID newPrimaryId = UUID.randomUUID();
        
        BankAccount currentPrimary = createBankAccount("0123456789", true);
        BankAccount newPrimary = createBankAccount("9876543210", false);
        newPrimary.setId(newPrimaryId);

        when(bankAccountRepository.findById(newPrimaryId)).thenReturn(Optional.of(newPrimary));
        when(bankAccountRepository.findByDriverIdAndIsPrimary(driverId, true))
                .thenReturn(Optional.of(currentPrimary));
        when(bankAccountRepository.save(any(BankAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        bankAccountService.setPrimaryAccount(driverId, newPrimaryId);

        // Assert
        ArgumentCaptor<BankAccount> captor = ArgumentCaptor.forClass(BankAccount.class);
        verify(bankAccountRepository, times(2)).save(captor.capture());

        List<BankAccount> savedAccounts = captor.getAllValues();
        
        // First save should unset current primary
        assertThat(savedAccounts.get(0).getIsPrimary()).isFalse();
        
        // Second save should set new primary
        assertThat(savedAccounts.get(1).getIsPrimary()).isTrue();
        assertThat(savedAccounts.get(1).getId()).isEqualTo(newPrimaryId);

        verify(auditService).logAuditEntry(eq("BANK_ACCOUNT"), any(), eq("SET_PRIMARY_ACCOUNT"), eq(driverId), any(), any());
    }

    @Test
    void setPrimaryAccount_WhenAccountNotFound_ShouldThrowException() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bankAccountService.setPrimaryAccount(driverId, accountId))
                .isInstanceOf(BankAccountNotFoundException.class);

        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    void setPrimaryAccount_WhenAccountBelongsToDifferentDriver_ShouldThrowException() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        UUID differentDriverId = UUID.randomUUID();
        
        BankAccount account = createBankAccount("0123456789", false);
        account.setId(accountId);
        account.setDriverId(differentDriverId);

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Act & Assert
        assertThatThrownBy(() -> bankAccountService.setPrimaryAccount(driverId, accountId))
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("does not belong to driver");

        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    void deleteBankAccount_ShouldRemoveAccount() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        BankAccount account = createBankAccount("0123456789", false);
        account.setId(accountId);

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Act
        bankAccountService.deleteBankAccount(driverId, accountId);

        // Assert
        verify(bankAccountRepository).delete(account);
        verify(auditService).logAuditEntry(eq("BANK_ACCOUNT"), any(), eq("DELETE_BANK_ACCOUNT"), eq(driverId), any(), any());
    }

    @Test
    void deleteBankAccount_WhenAccountIsPrimary_ShouldThrowException() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        BankAccount primaryAccount = createBankAccount("0123456789", true);
        primaryAccount.setId(accountId);

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(primaryAccount));

        // Act & Assert
        assertThatThrownBy(() -> bankAccountService.deleteBankAccount(driverId, accountId))
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("Cannot delete primary account");

        verify(bankAccountRepository, never()).delete(any());
    }

    @Test
    void deleteBankAccount_WhenAccountNotFound_ShouldThrowException() {
        // Arrange
        UUID accountId = UUID.randomUUID();
        
        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bankAccountService.deleteBankAccount(driverId, accountId))
                .isInstanceOf(BankAccountNotFoundException.class);

        verify(bankAccountRepository, never()).delete(any());
    }

    @Test
    void getPrimaryBankAccount_ShouldReturnPrimaryAccount() {
        // Arrange
        BankAccount primaryAccount = createBankAccount("0123456789", true);
        
        when(bankAccountRepository.findByDriverIdAndIsPrimary(driverId, true))
                .thenReturn(Optional.of(primaryAccount));

        // Act
        BankAccountResponse response = bankAccountService.getPrimaryBankAccount(driverId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getIsPrimary()).isTrue();
        assertThat(response.getAccountNumber()).isEqualTo("0123456789");
    }

    @Test
    void getPrimaryBankAccount_WhenNoPrimaryExists_ShouldThrowException() {
        // Arrange
        when(bankAccountRepository.findByDriverIdAndIsPrimary(driverId, true))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bankAccountService.getPrimaryBankAccount(driverId))
                .isInstanceOf(PayoutsException.class)
                .hasMessageContaining("No primary bank account");
    }

    private BankAccount createBankAccount(String accountNumber, boolean isPrimary) {
        BankAccount account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setDriverId(driverId);
        account.setAccountNumber(accountNumber);
        account.setBankCode("058");
        account.setBankName("GTBank");
        account.setAccountName("John Doe");
        account.setIsVerified(true);
        account.setIsPrimary(isPrimary);
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        return account;
    }
}
