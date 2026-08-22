package com.eodegano.cocobackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GroqApiResponseDto {

    private List<Choice> choices;
    private Usage usage;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        private Message message;
        private String finish_reason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        private Integer prompt_tokens;
        private Integer completion_tokens;
        private Integer total_tokens;
    }
}
