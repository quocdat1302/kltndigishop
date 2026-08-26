package com.khoaluan.digishop.entity;

/** Cách khách nhận/kiểm tra máy khi thuê — chỉ áp dụng cho OrderType.RENTAL. */
public enum FulfillmentMethod {
    /** Khách đến shop nhận máy, kiểm tra tình trạng máy trực tiếp tại shop. */
    PICKUP_AT_SHOP,
    /** Shop giao máy tận nơi, khách kiểm tra tình trạng máy ngay lúc nhận. */
    HOME_DELIVERY
}