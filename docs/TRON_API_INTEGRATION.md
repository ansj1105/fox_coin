# TRON 서비스 API 통합 가이드

## 📋 현재 상황

### ✅ Java 서비스 (foxya_coin_service)
- **요청**: `POST /api/wallet/create`
- **Request Body**: 
  ```json
  {
    "currencyCode": "KRO"
  }
  ```
- **Response 기대**: 
  ```json
  {
    "address": "TXYZabc123def456..."
  }
  ```

### ❌ TRON 서비스 (coin_publish)
- **현재 상태**: `/api/wallet/create` 엔드포인트가 **구현되지 않음**
- **기존 엔드포인트**: `/api/health`, `/api/login`, `/api/transfer`, `/api/deploy` 등

## 🔧 해결 방법

### 방법 1: TRON 서비스에 `/api/wallet/create` 엔드포인트 추가 (권장)

`/Users/an/work/coin_publish/src/server/index.js`에 다음 코드를 추가하세요:

```javascript
import { createTronWeb } from "../scripts/utils/tron.js";

// 지갑 생성 API (인증 불필요 - 내부 서비스 간 통신용)
app.post("/api/wallet/create", async (req, res) => {
  try {
    const { currencyCode } = req.body || {};
    
    if (!currencyCode) {
      res.status(400).json({ error: "currencyCode is required" });
      return;
    }

    // TRON 지갑 생성
    const tronWeb = createTronWeb();
    const account = await tronWeb.createAccount();
    
    if (!account || !account.address) {
      res.status(500).json({ error: "Failed to create wallet address" });
      return;
    }

    res.json({
      address: account.address.base58,
      currencyCode: currencyCode
    });
  } catch (err) {
    console.error("Wallet creation error:", err);
    res.status(500).json({ error: err.message || "Internal server error" });
  }
});
```

### 방법 2: 기존 TronService 활용

`/Users/an/work/coin_publish/src/services/TronService.js`를 확인하고, 지갑 생성 기능이 있다면 활용:

```javascript
import { createWallet } from "../services/TronService.js";

app.post("/api/wallet/create", async (req, res) => {
  try {
    const { currencyCode } = req.body || {};
    
    if (!currencyCode) {
      res.status(400).json({ error: "currencyCode is required" });
      return;
    }

    const address = await createWallet(currencyCode);
    
    res.json({
      address: address,
      currencyCode: currencyCode
    });
  } catch (err) {
    console.error("Wallet creation error:", err);
    res.status(500).json({ error: err.message || "Internal server error" });
  }
});
```

## 🔍 API 스펙 확인

### Java 서비스 요청 형식
```java
// WalletService.java
String url = tronServiceUrl + "/api/wallet/create";
JsonObject requestBody = new JsonObject()
    .put("currencyCode", currencyCode);

webClient.postAbs(url)
    .sendJsonObject(requestBody)
    .compose(response -> {
        if (response.statusCode() == 200) {
            JsonObject body = response.bodyAsJsonObject();
            if (body.containsKey("address")) {
                String address = body.getString("address");
                return Future.succeededFuture(address);
            }
        }
    });
```

### TRON 서비스 응답 형식 (필수)
```json
{
  "address": "TXYZabc123def456..."
}
```

**중요**: 응답에 `address` 필드가 반드시 포함되어야 합니다.

## ✅ 체크리스트

- [ ] `WalletRepository.java` 구문 오류 수정 완료
- [ ] TRON 서비스에 `/api/wallet/create` 엔드포인트 추가
- [ ] 요청 형식: `POST /api/wallet/create` with `{ "currencyCode": "KRO" }`
- [ ] 응답 형식: `{ "address": "T..." }` (200 OK)
- [ ] 에러 처리: 400 (잘못된 요청), 500 (서버 오류)
- [ ] 테스트: Java 서비스에서 TRON 서비스 호출 테스트

## 🧪 테스트 방법

### 1. TRON 서비스 직접 테스트
```bash
curl -X POST http://localhost:3000/api/wallet/create \
  -H "Content-Type: application/json" \
  -d '{"currencyCode": "KRO"}'
```

**예상 응답**:
```json
{
  "address": "TXYZabc123def456..."
}
```

### 2. Java 서비스에서 통합 테스트
```bash
# 지갑 생성 API 호출
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"currencyCode": "KRO"}'
```

## 📝 참고사항

1. **인증**: `/api/wallet/create`는 내부 서비스 간 통신이므로 인증이 필요 없을 수 있습니다. 하지만 보안을 위해 IP 화이트리스트나 API 키를 고려하세요.

2. **에러 처리**: TRON 서비스 호출 실패 시 Java 서비스는 자동으로 더미 주소를 생성합니다 (fallback).

3. **로깅**: 두 서비스 모두 적절한 로깅을 추가하여 디버깅을 용이하게 하세요.

