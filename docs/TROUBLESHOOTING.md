# 트러블슈팅 가이드

## 외부에서 접속이 안 되는 경우

### 1. 서버 내부 방화벽 확인

```bash
# UFW 사용 시 (Ubuntu/Debian)
sudo ufw status
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw reload

# firewalld 사용 시 (CentOS/RHEL)
sudo firewall-cmd --list-ports
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload

# iptables 직접 확인
sudo iptables -L -n | grep 80
```

### 2. Docker 포트 바인딩 확인

```bash
# Docker 컨테이너 포트 확인
docker ps | grep nginx
# 출력 예: 0.0.0.0:80->80/tcp

# 포트가 제대로 바인딩되어 있는지 확인
netstat -tlnp | grep :80
# 또는
ss -tlnp | grep :80
```

### 3. Nginx가 모든 인터페이스에서 수신하는지 확인

```bash
# Nginx 설정 확인
docker exec foxya-nginx nginx -T | grep "listen"

# Nginx 프로세스 확인
docker exec foxya-nginx ps aux | grep nginx
```

### 4. AWS 보안 그룹 확인

- EC2 콘솔 → 보안 그룹 → 인바운드 규칙
- HTTP (80), HTTPS (443) 포트가 열려있는지 확인
- 소스가 `0.0.0.0/0` 또는 특정 IP로 설정되어 있는지 확인

### 5. EC2 인스턴스의 Public IP 확인

```bash
# EC2 인스턴스의 Public IP 확인
curl http://169.254.169.254/latest/meta-data/public-ipv4

# 또는
hostname -I
```

### 6. 로컬에서 테스트

```bash
# 서버 내부에서 테스트
curl -I http://localhost
curl -I http://127.0.0.1
curl -I http://$(hostname -I | awk '{print $1}')

# 외부에서 테스트 (다른 서버나 로컬 PC에서)
curl -I http://54.210.92.221
```

### 7. Nginx 로그 확인

```bash
# 액세스 로그 확인
docker logs foxya-nginx --tail 50

# 또는 직접 로그 파일 확인
docker exec foxya-nginx tail -f /var/log/nginx/access.log
docker exec foxya-nginx tail -f /var/log/nginx/error.log
```

### 8. Docker 네트워크 확인

```bash
# Docker 네트워크 확인
docker network inspect foxya-network

# 포트 매핑 확인
docker port foxya-nginx
```

## 일반적인 문제 해결

### 문제: "Connection refused" 오류

**원인:**
- 포트가 바인딩되지 않음
- 방화벽이 포트를 차단
- 서비스가 실행되지 않음

**해결:**
1. Docker 컨테이너 상태 확인: `docker ps`
2. 포트 바인딩 확인: `docker port foxya-nginx`
3. 방화벽 확인: 위의 1번 참고

### 문제: "Timeout" 오류

**원인:**
- AWS 보안 그룹이 포트를 차단
- 네트워크 라우팅 문제

**해결:**
1. AWS 보안 그룹 확인
2. EC2 인스턴스의 Public IP 확인
3. 다른 포트로 테스트 (예: 8080)

### 문제: Nginx는 실행 중이지만 응답이 없음

**원인:**
- Nginx 설정 오류
- Upstream 서비스가 응답하지 않음

**해결:**
1. Nginx 설정 테스트: `docker exec foxya-nginx nginx -t`
2. 백엔드 서비스 확인: `docker logs foxya-api`
3. Nginx 에러 로그 확인: `docker logs foxya-nginx`

## 빠른 진단 체크리스트

```bash
# 1. 모든 컨테이너가 실행 중인지 확인
docker ps

# 2. 포트가 열려있는지 확인
netstat -tlnp | grep -E ':(80|443)'

# 3. 방화벽 상태 확인
sudo ufw status  # 또는 sudo firewall-cmd --list-all

# 4. Nginx 설정 테스트
docker exec foxya-nginx nginx -t

# 5. 로컬에서 접속 테스트
curl -I http://localhost

# 6. 외부에서 접속 테스트 (다른 서버에서)
curl -I http://54.210.92.221

# 7. Nginx 로그 확인
docker logs foxya-nginx --tail 20
```

## 추가 도움말

문제가 계속되면 다음 정보를 수집하세요:

1. `docker ps` 출력
2. `docker logs foxya-nginx --tail 50` 출력
3. `netstat -tlnp | grep -E ':(80|443)'` 출력
4. `sudo ufw status` 또는 `sudo firewall-cmd --list-all` 출력
5. AWS 보안 그룹 인바운드 규칙 스크린샷

