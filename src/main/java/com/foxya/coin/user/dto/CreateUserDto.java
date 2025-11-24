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
    private String username;
    private String email;
    private String password;
    @Setter
    private String passwordHash;
    private String phone;
    private String referralCode;
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> params = new HashMap<>();
        if (username != null) params.put("username", username);
        if (email != null) params.put("email", email);
        if (passwordHash != null) params.put("password_hash", passwordHash);
        if (phone != null) params.put("phone", phone);
        if (referralCode != null) params.put("referral_code", referralCode);
        params.put("status", "ACTIVE");
        return params;
    }
}

