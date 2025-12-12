package com.foxya.coin.common.enums;

import lombok.Getter;

/**
 * 국가 코드 Enum
 */
@Getter
public enum CountryCode {
    KR("KR", "대한민국", "🇰🇷"),
    US("US", "미국", "🇺🇸"),
    JP("JP", "일본", "🇯🇵"),
    CN("CN", "중국", "🇨🇳"),
    GB("GB", "영국", "🇬🇧"),
    FR("FR", "프랑스", "🇫🇷"),
    DE("DE", "독일", "🇩🇪"),
    IT("IT", "이탈리아", "🇮🇹"),
    ES("ES", "스페인", "🇪🇸"),
    CA("CA", "캐나다", "🇨🇦"),
    AU("AU", "호주", "🇦🇺"),
    BR("BR", "브라질", "🇧🇷"),
    IN("IN", "인도", "🇮🇳"),
    RU("RU", "러시아", "🇷🇺"),
    MX("MX", "멕시코", "🇲🇽"),
    ID("ID", "인도네시아", "🇮🇩"),
    TH("TH", "태국", "🇹🇭"),
    VN("VN", "베트남", "🇻🇳"),
    PH("PH", "필리핀", "🇵🇭"),
    MY("MY", "말레이시아", "🇲🇾"),
    SG("SG", "싱가포르", "🇸🇬"),
    TW("TW", "대만", "🇹🇼"),
    HK("HK", "홍콩", "🇭🇰"),
    UNKNOWN("UNKNOWN", "알 수 없음", "🏳️");
    
    private final String code;
    private final String name;
    private final String flag;
    
    CountryCode(String code, String name, String flag) {
        this.code = code;
        this.name = name;
        this.flag = flag;
    }
    
    public static CountryCode fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        for (CountryCode countryCode : values()) {
            if (countryCode.code.equals(code)) {
                return countryCode;
            }
        }
        return UNKNOWN;
    }
}

