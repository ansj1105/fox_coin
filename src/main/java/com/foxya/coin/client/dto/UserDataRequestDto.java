package com.foxya.coin.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDataRequestDto {
    // 유저 데이터를 받을 수 있도록 모든 필드를 Map으로 받음
    private Map<String, Object> userData;
}

