# Gmail SMTP 설정 가이드

## Gmail SMTP 인증 오류 해결 방법

### 오류 메시지
```
AUTH PLAIN failed: 535-5.7.8 Username and Password not accepted
```

### 해결 방법

#### 1. Gmail 2단계 인증 활성화

1. Google 계정 설정 접속: https://myaccount.google.com/
2. 보안 → 2단계 인증 → 사용 설정
3. 2단계 인증이 활성화되어 있어야 앱 비밀번호를 생성할 수 있습니다

#### 2. 앱 비밀번호 생성

1. Google 계정 설정 → 보안
2. "앱 비밀번호" 검색 또는 직접 접속: https://myaccount.google.com/apppasswords
3. "앱 선택" → "기타(맞춤 이름)" → "Foxya Coin Service" 입력
4. "생성" 클릭
5. 생성된 16자리 앱 비밀번호 복사 (공백 없이)

#### 3. config.json 업데이트

`src/main/resources/config.json`에서:

```json
{
  "smtp": {
    "host": "smtp.gmail.com",
    "port": 587,
    "username": "your-gmail@gmail.com",  // 실제 Gmail 주소
    "password": "your-16-digit-app-password",  // 앱 비밀번호 (공백 없이)
    "from": "no-reply@foxya.com",
    "starttls": "REQUIRED",
    "login": "LOGIN",
    "authMethods": "PLAIN"
  }
}
```

#### 4. 확인 사항

- ✅ Gmail 주소가 정확한지 확인
- ✅ 앱 비밀번호가 공백 없이 입력되었는지 확인
- ✅ 2단계 인증이 활성화되어 있는지 확인
- ✅ "보안 수준이 낮은 앱 액세스"는 더 이상 필요 없음 (앱 비밀번호 사용 시)

### 앱 비밀번호 형식

앱 비밀번호는 16자리이며, 공백으로 구분되어 표시될 수 있습니다:
- 표시: `wdod lmjv ijgl jedk`
- 실제 사용: `wdodlmjvijgljedk` (공백 제거)

### 테스트

서버에서 다음을 실행하여 테스트:

```bash
# 이메일 인증 코드 발송 API 호출
curl -X POST http://localhost:8080/api/v1/user/email/send-code \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"email": "test@example.com"}'
```

### 추가 문제 해결

#### "Less secure app access" 오류
- 이제는 앱 비밀번호를 사용하므로 "보안 수준이 낮은 앱 액세스" 설정은 필요 없습니다
- 앱 비밀번호를 사용하면 더 안전합니다

#### "Username and Password not accepted" 오류
1. 앱 비밀번호가 올바른지 확인 (공백 제거)
2. Gmail 주소가 정확한지 확인
3. 2단계 인증이 활성화되어 있는지 확인
4. 앱 비밀번호가 만료되지 않았는지 확인 (재생성 필요할 수 있음)

### 참고 링크

- [Google 계정 보안 설정](https://myaccount.google.com/security)
- [앱 비밀번호 생성](https://myaccount.google.com/apppasswords)
- [Gmail SMTP 설정](https://support.google.com/mail/answer/7126229)

