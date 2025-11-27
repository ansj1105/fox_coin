package com.foxya.coin.common.jsonschema;

import io.vertx.json.schema.common.dsl.StringSchemaBuilder;
import io.vertx.json.schema.draft7.dsl.StringFormat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.vertx.json.schema.common.dsl.Keywords.*;
import static io.vertx.json.schema.common.dsl.Schemas.stringSchema;
import static io.vertx.json.schema.draft7.dsl.Keywords.format;

/**
 * request validation에 사용할 Schema
 */
public abstract class Schemas {
    
    /**
     * 비밀번호 스키마 (8-20자, 특수문자 포함)
     */
    public static StringSchemaBuilder passwordSchema() {
        return stringSchema().with(
            minLength(8),
            maxLength(20),
            pattern(Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$")),
            format(StringFormat.REGEX)
        );
    }
    
    /**
     * 이메일 스키마
     */
    public static StringSchemaBuilder emailSchema() {
        return stringSchema().with(format(StringFormat.EMAIL));
    }
    
    /**
     * 전화번호 스키마
     */
    public static StringSchemaBuilder phoneSchema() {
        return stringSchema().with(
            pattern(Pattern.compile("^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$")),
            format(StringFormat.REGEX)
        );
    }
    
    /**
     * Enum String 스키마
     */
    public static <T> StringSchemaBuilder enumStringSchema(T[] values) {
        List<String> valueList = Arrays.stream(values)
            .map(Object::toString)
            .collect(Collectors.toList());
        return stringSchema().withKeyword("enum", valueList);
    }
    
    /**
     * DateTime 스키마
     */
    public static StringSchemaBuilder dateTimeSchema() {
        return stringSchema().withKeyword("format", "date-time");
    }
    
    /**
     * 숫자 문자열 스키마
     */
    public static StringSchemaBuilder stringIntSchema() {
        return stringSchema().with(
            pattern(Pattern.compile("\\d+")),
            format(StringFormat.REGEX)
        );
    }
}

