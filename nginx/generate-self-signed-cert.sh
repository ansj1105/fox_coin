#!/bin/bash

# 자체 서명 SSL 인증서 생성 스크립트
# 개발/테스트 환경용

SSL_DIR="./ssl"
mkdir -p "$SSL_DIR"

# 인증서 생성
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$SSL_DIR/key.pem" \
  -out "$SSL_DIR/cert.pem" \
  -subj "/C=KR/ST=Seoul/L=Seoul/O=Foxya/OU=IT/CN=localhost"

# Let's Encrypt 형식으로 복사 (fullchain.pem)
cp "$SSL_DIR/cert.pem" "$SSL_DIR/fullchain.pem"
cp "$SSL_DIR/key.pem" "$SSL_DIR/privkey.pem"

echo "✅ SSL 인증서가 생성되었습니다:"
echo "   - $SSL_DIR/cert.pem"
echo "   - $SSL_DIR/key.pem"
echo "   - $SSL_DIR/fullchain.pem"
echo "   - $SSL_DIR/privkey.pem"
echo ""
echo "⚠️  이 인증서는 자체 서명 인증서입니다. 브라우저에서 경고가 표시됩니다."
echo "   프로덕션 환경에서는 Let's Encrypt 인증서를 사용하세요."

