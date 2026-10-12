package com.leadorbit.crm.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leadorbit.crm.domain.Activity;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findAllByLeadIdOrderByCreatedAtDesc(Long leadId);
}