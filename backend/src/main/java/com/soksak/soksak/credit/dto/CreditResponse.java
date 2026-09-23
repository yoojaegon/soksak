package com.soksak.soksak.credit.dto;

/** 헤더에 띄울 잔액. 내역은 아직 화면이 없어 내려주지 않는다(필요해지면 여기 옆에 붙인다). */
public record CreditResponse(int balance) {
}
