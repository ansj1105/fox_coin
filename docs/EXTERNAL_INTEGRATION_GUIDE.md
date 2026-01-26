# External Integration Guide (Korion)

This document describes how external services integrate with Korion for user linking and data access.

## 1) Overview
External services link a Korion user to an external UUID using a short-lived link code.

Flow:
1. User signs up/logs in at Korion (`korion.io.kr`).
2. User generates a link code in Korion (valid 5 minutes).
3. External service submits a link request with the link code and its user UUID.
4. Link is completed; external service can request user tokens and call APIs.

## 2) Base URL
- `https://your-domain.com`

## 3) Common Response Format
```
{
  "status": "OK" | "FAIL",
  "message": "string",
  "data": {}
}
```

## 4) Required Parameters (External Service)
The external service must know these:
- `apiKey`: issued by Korion
- `apiSecret`: issued by Korion
- `provider`: fixed identifier (e.g. `BROADCAST`)
- `externalId`: external user UUID
- `linkCode`: 1-time code created by the Korion user

## 5) Endpoints

### 5.1 Issue Client Token
Use API key/secret to get a client token.

`POST /api/v1/client/token`

Request:
```
{
  "apiKey": "string",
  "apiSecret": "string"
}
```

Response `data`:
```
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 1800
}
```

### 5.2 Link External User (linkCode)
Use the client token to link an external UUID with a Korion user.

`POST /api/v1/client/external-ids/link`

Header:
- `Authorization: Bearer <clientAccessToken>`

Request:
```
{
  "provider": "BROADCAST",
  "externalId": "uuid-1234",
  "linkCode": "ABCD1234"
}
```

Response `data`:
```
{
  "userId": 123,
  "provider": "BROADCAST",
  "externalId": "uuid-1234"
}
```

Notes:
- `linkCode` is valid for 5 minutes.
- `provider` is normalized to uppercase.

### 5.3 Issue User Token (after link)
Use `provider + externalId` to issue a user token.

`POST /api/v1/client/user-token`

Header:
- `Authorization: Bearer <clientAccessToken>`

Request:
```
{
  "provider": "BROADCAST",
  "externalId": "uuid-1234"
}
```

Response `data`:
```
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 1800
}
```

### 5.4 Call Protected APIs
Use the user token to call existing APIs.

Example:
`GET /api/v1/wallets/my`

Header:
- `Authorization: Bearer <userAccessToken>`

## 6) Korion User Actions (frontend)
The Korion user must create a link code:

`POST /api/v1/users/external-ids/link-code`

Header:
- `Authorization: Bearer <userAccessToken>`

Request:
```
{
  "provider": "BROADCAST"
}
```

Response `data`:
```
{
  "linkCode": "ABCD1234",
  "expiresIn": 300,
  "provider": "BROADCAST"
}
```

## 7) Link Status (Korion user)
To show whether a user is linked:

`GET /api/v1/users/external-ids/status?provider=BROADCAST`

Header:
- `Authorization: Bearer <userAccessToken>`

Response `data`:
```
{
  "linked": true,
  "provider": "BROADCAST",
  "externalId": "uuid-1234"
}
```

## 8) Data Storage and Migration
- Link codes use Redis only.
- No DB migration is needed for link code issuance.
- Linking writes to the existing `user_external_ids` table.
