package com.soksak.soksak.common;

public record ErrorResponse (
        int status,
        String message
){
}
