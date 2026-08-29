package com.khoaluan.digishop.repository;

import com.khoaluan.digishop.entity.RentalContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RentalContractRepository extends JpaRepository<RentalContract, Long> {
    Optional<RentalContract> findByOrder_Id(Long orderId);
    boolean existsByOrder_Id(Long orderId);

    /** Xoá toàn bộ hợp đồng thuê thuộc các đơn hàng của 1 user — dùng khi admin xoá cứng tài khoản (phải xoá trước khi xoá orders vì FK order_id). */
    void deleteByOrder_User_Id(Long userId);
}