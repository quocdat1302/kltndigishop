package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.PickupLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PickupLocationRepository extends JpaRepository<PickupLocation, Long> {
    List<PickupLocation> findByActiveTrueOrderByDisplayOrderAscIdAsc();
    List<PickupLocation> findAllByOrderByDisplayOrderAscIdAsc();
}