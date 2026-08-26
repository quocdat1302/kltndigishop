package com.khoaluan.digishop.controller;

import com.khoaluan.digishop.dto.PickupLocationDto;
import com.khoaluan.digishop.dto.PickupLocationRequest;
import com.khoaluan.digishop.entity.PickupLocation;
import com.khoaluan.digishop.exception.ApiException;
import com.khoaluan.digishop.repository.PickupLocationRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Địa điểm/hình thức nhận máy khi thuê (tại shop, chi nhánh khác, giao tận nơi...) kèm phụ phí — admin tự cấu hình. */
@RestController
@RequestMapping("/api/pickup-locations")
@RequiredArgsConstructor
public class PickupLocationController {

    private final PickupLocationRepository pickupLocationRepository;

    /** Danh sách công khai (chỉ các mục đang bật) — dùng cho trang đặt thuê. */
    @GetMapping
    public List<PickupLocationDto> getActiveLocations() {
        return pickupLocationRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PickupLocationDto> getAllLocationsForAdmin() {
        return pickupLocationRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PickupLocationDto create(@Valid @RequestBody PickupLocationRequest req) {
        PickupLocation saved = pickupLocationRepository.save(PickupLocation.builder()
                .name(req.name())
                .address(req.address())
                .fee(req.fee())
                .isDelivery(req.isDelivery())
                .active(req.active())
                .displayOrder(req.displayOrder() != null ? req.displayOrder() : 0)
                .build());
        return toDto(saved);
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PickupLocationDto update(@PathVariable Long id, @Valid @RequestBody PickupLocationRequest req) {
        PickupLocation location = pickupLocationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Không tìm thấy địa điểm"));
        location.setName(req.name());
        location.setAddress(req.address());
        location.setFee(req.fee());
        location.setDelivery(req.isDelivery());
        location.setActive(req.active());
        location.setDisplayOrder(req.displayOrder() != null ? req.displayOrder() : 0);
        return toDto(pickupLocationRepository.save(location));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        pickupLocationRepository.deleteById(id);
    }

    private PickupLocationDto toDto(PickupLocation l) {
        return new PickupLocationDto(l.getId(), l.getName(), l.getAddress(), l.getFee(), l.isDelivery(), l.isActive());
    }
}