package com.openride.user.repository;

import com.openride.user.entity.User;
import com.openride.user.enums.KycStatus;
import com.openride.user.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by phone number.
     *
     * @param phone phone number
     * @return optional user
     */
    Optional<User> findByPhone(String phone);

    /**
     * Checks if a user with the given phone exists.
     *
     * @param phone phone number
     * @return true if exists, false otherwise
     */
    boolean existsByPhone(String phone);

    /**
     * Finds all users with a specific role.
     *
     * @param role user role
     * @return list of users
     */
    List<User> findByRole(UserRole role);

    /**
     * Finds all users with a specific KYC status.
     *
     * @param kycStatus KYC status
     * @return list of users
     */
    List<User> findByKycStatus(KycStatus kycStatus);

    /**
     * Finds all drivers with pending KYC status.
     *
     * @return list of users
     */
    List<User> findByRoleAndKycStatus(UserRole role, KycStatus kycStatus);

    /**
     * Finds all active users.
     *
     * @param isActive active status
     * @return list of users
     */
    List<User> findByIsActive(Boolean isActive);
}
