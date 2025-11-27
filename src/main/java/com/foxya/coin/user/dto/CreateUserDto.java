package com.foxya.coin.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.foxya.coin.common.database.ParametersMapped;
import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class CreateUserDto implements ParametersMapped {
    private String loginId;
    private String password;
    @Setter
    private String passwordHash;
    private String referralCode;
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> params = new HashMap<>();
        if (loginId != null) params.put("login_id", loginId);
        if (passwordHash != null) params.put("password_hash", passwordHash);
        if (referralCode != null) params.put("referral_code", referralCode);
        params.put("status", "ACTIVE");
        return params;
    }
}
