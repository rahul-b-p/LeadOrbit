package com.leadorbit.identity.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.leadorbit.identity.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    @Query("select distinct p.code from User u join u.roles r join r.permissions p where u.id = :userId")
    List<String> findRolePermissionCodes(Long userId);

    @Query("select distinct r.code from User u join u.roles r where u.id = :userId")
    List<String> findRoleCodes(Long userId);
}
