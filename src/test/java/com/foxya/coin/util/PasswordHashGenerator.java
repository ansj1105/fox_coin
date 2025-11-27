package com.foxya.coin.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 테스트용 비밀번호 해시 생성기
 * 실행: ./gradlew test --tests PasswordHashGenerator
 */
public class PasswordHashGenerator {
    public static void main(String[] args) {
        String password = "Test1234!@";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        
        System.out.println("=".repeat(60));
        System.out.println("Password Hash Generator for Test Data");
        System.out.println("=".repeat(60));
        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println("=".repeat(60));
        System.out.println("\nCopy this hash to your seed SQL file:");
        System.out.println("'" + hash + "'");
    }
}

