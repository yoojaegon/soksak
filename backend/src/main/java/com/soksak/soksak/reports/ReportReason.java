package com.soksak.soksak.reports;

// 왜 신고했나. MINOR(미성년 성적 묘사)도 숨김 판정은 다른 사유와 같다(신고 남용 방지). 운영자 확인용 warn 로그만 따로 남긴다.
public enum ReportReason {
    MINOR,
    SEXUAL,
    VIOLENCE,
    HATE,
    OTHER
}
