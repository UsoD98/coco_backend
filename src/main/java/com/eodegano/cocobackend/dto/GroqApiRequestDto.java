package com.eodegano.cocobackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroqApiRequestDto {

    private String model;
    private List<Message> messages;
    private Double temperature;
    private Integer max_tokens;
    private String reasoning_effort;
    private String reasoning_format;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
