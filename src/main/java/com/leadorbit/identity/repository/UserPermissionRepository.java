package com.leadorbit.identity.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.leadorbit.identity.domain.PermissionEffect;
import com.leadorbit.identity.domain.UserPermission;
import com.leadorbit.identity.domain.UserPermissionId;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {

    @Query("select p.code from UserPermission up join up.permission p "
        + "where up.user.id = :userId and up.effect = :effect")
    List<String> findPermissionCodesByEffect(@Param("userId") Long userId,
                                             @Param("effect") PermissionEffect effect);
}
