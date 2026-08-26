package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.RentalContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RentalContractRepository extends JpaRepository<RentalContract, Long> {
    Optional<RentalContract> findByOrder_Id(Long orderId);
    boolean existsByOrder_Id(Long orderId);
}