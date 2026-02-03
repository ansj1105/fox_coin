# FCM 푸시 알림 연동

입금/출금 완료 시 `notifications` 테이블에 알림을 저장하고, 설정된 경우 해당 유저의 등록 디바이스로 FCM 푸시를 발송합니다.

## 전제

**푸시 알람을 쓰려면 Firebase(푸시 알람 서비스)에 가입해 프로젝트를 만들고, 서비스 계정 키(JSON)를 발급받아야 합니다.**  
[Firebase 콘솔](https://console.firebase.google.com) → 프로젝트 생성 후 아래 설정을 진행합니다.

## 설정

1. **Firebase 콘솔**에서 프로젝트 → 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성하여 JSON 파일 다운로드.
2. **JSON 파일 위치 (중요)**  
   **프로젝트 폴더 안에 넣지 말고**, 로컬/서버의 다른 경로에만 둡니다.  
   - **로컬**: 예) `~/.config/foxya/firebase-service-account.json`  
   - **서버**: 예) `/var/secrets/firebase-service-account.json` (배포 스크립트/시크릿 관리로만 복사)  
   - 이 파일은 **절대 Git에 커밋하지 않습니다.** (.gitignore에 패턴 등록됨)
3. **환경 변수** (둘 중 하나):
   - `GOOGLE_APPLICATION_CREDENTIALS` = 위 JSON 파일의 **절대 경로**
   - 또는 `FCM_CREDENTIALS_PATH` = 동일 경로
4. 미설정 시 푸시는 보내지 않고, 알림만 DB에 저장됩니다.

## 앱에서 할 일

1. **FCM 토큰 등록**  
   로그인 후(또는 앱 실행 시) 동일한 `deviceId`로 토큰을 등록합니다.

   ```
   PATCH /api/v1/user/fcm-token
   Authorization: Bearer <JWT>
   Content-Type: application/json

   { "deviceId": "앱에서 사용하는 디바이스 ID", "fcmToken": "FCM 등록 토큰" }
   ```

2. **deviceId**  
   로그인/회원가입 시 보내는 `deviceId`와 동일한 값을 사용해야, 해당 디바이스에 푸시가 갑니다.

## 동작

- **입금 완료**: `TokenDepositService.completeTokenDeposit` → 알림 생성 → FCM 푸시 (제목/내용 + metadata: depositId, amount, currencyCode, txHash, type).
- **출금 완료**: `TransferService.confirmExternalTransfer` → 알림 생성 → FCM 푸시 (제목/내용 + metadata: transferId, amount, currencyCode, txHash, toAddress, type).
- 푸시 실패 시에도 알림 DB 저장은 유지되며, 푸시만 로그 후 무시됩니다.

## 의존성

- `com.google.firebase:firebase-admin:9.2.0` (build.gradle.kts)
