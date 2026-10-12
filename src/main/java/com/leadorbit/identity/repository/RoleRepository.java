package com.leadorbit.identity.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.identity.domain.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    List<Role> findAllByOrderByRankDesc();

    List<Role> findByCodeIn(List<String> codes);
}
