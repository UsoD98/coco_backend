# Blue/Green 무중단 배포 — 서버 최초 1회 설정

`main` push → GitHub Actions(`.github/workflows/deploy.yml`)가 매번 자동으로 하는 일과, 서버에서 **사람이 딱 한 번만** 해야 하는 일을 분리한 문서다. 아래는 후자다. 이미 기존 `cocobackend` systemd 서비스로 운영 중인 서버(8080 직접 노출)를 전제로 한다.

## 0. 사전 확인

- 방화벽/보안그룹에서 **80번 포트**가 열려 있는지 확인 (기존에 8080을 직접 열어뒀다면 80으로 교체 — 8080/8081은 이제 nginx 뒤에서만 쓰는 내부 포트).
- HTTPS를 쓴다면(443) 별도로 인증서 발급 후 `deploy/nginx-cocobackend.conf`에 `listen 443 ssl;` 블록 추가는 이 문서 범위 밖 — Let's Encrypt(certbot) 등으로 별도 진행.

## 1. nginx 설치

```bash
sudo apt update
sudo apt install -y nginx
```

## 2. 디렉토리·상태 파일 준비

```bash
mkdir -p /home/ubuntu/cocobackend-blue /home/ubuntu/cocobackend-green
# 기존에 실행 중이던 jar를 blue 슬롯의 최초 실행 파일로 사용
cp /home/ubuntu/cocobackend/app.jar /home/ubuntu/cocobackend-blue/app.jar
touch /home/ubuntu/cocobackend-green/app.jar   # green은 최초엔 미기동 상태라 빈 파일이어도 무방(첫 배포 때 CI가 채움)
echo blue > /home/ubuntu/cocobackend/active_color
```

`/home/ubuntu/cocobackend/.env`는 기존 그대로 두 슬롯이 공유한다 (변경 없음).

## 3. systemd 유닛 등록

```bash
sudo cp deploy/cocobackend-blue.service /etc/systemd/system/
sudo cp deploy/cocobackend-green.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable cocobackend-blue cocobackend-green
sudo systemctl start cocobackend-blue     # green은 시작하지 않음 — 다음 배포 때 CI가 기동
sudo systemctl stop cocobackend           # 기존 단일 인스턴스 유닛 중지
sudo systemctl disable cocobackend        # 재부팅 시 재기동 방지
```

`cocobackend-blue`가 `http://127.0.0.1:8080/actuator/health`에서 `{"status":"UP"}`를 반환하는지 확인 후 다음 단계로 진행.

## 4. switch-active.sh 배치

```bash
cp deploy/switch-active.sh /home/ubuntu/cocobackend/switch-active.sh
chmod +x /home/ubuntu/cocobackend/switch-active.sh
sudo /home/ubuntu/cocobackend/switch-active.sh 8080   # 최초 업스트림을 blue(8080)로 생성 + nginx reload
```

## 5. nginx 사이트 설정

먼저 `nginx.conf`가 어느 디렉토리를 읽는지 확인한다:

```bash
grep -n include /etc/nginx/nginx.conf
```

`include /etc/nginx/sites-enabled/*;`가 있으면 (Debian/Ubuntu 계열 기본 nginx) sites-available/sites-enabled 방식을 써도 되지만, **없고 `include /etc/nginx/conf.d/*.conf;`만 있다면(nginx.org 계열 등) conf.d에 직접 설치해야 한다** — 실제로 이 방식이 확인 없이도 항상 동작하므로 아래 conf.d 방식을 기본으로 쓴다:

```bash
sudo cp deploy/nginx-cocobackend.conf /etc/nginx/conf.d/cocobackend.conf
sudo nginx -t
sudo systemctl reload nginx
```

`curl http://<서버IP>/actuator/health`로 nginx 경유 응답이 오는지 확인.

## 6. CI가 sudo 없이 걸리지 않도록 sudoers 설정

`sudo visudo -f /etc/sudoers.d/cocobackend-deploy`로 새 파일을 만들고 아래 내용을 넣는다(배포 계정이 `ubuntu`가 아니면 이름 교체):

```
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl restart cocobackend-blue, /usr/bin/systemctl restart cocobackend-green, /usr/bin/systemctl stop cocobackend-blue, /usr/bin/systemctl stop cocobackend-green, /home/ubuntu/cocobackend/switch-active.sh *
```

`which systemctl`로 실제 경로가 `/usr/bin/systemctl`인지 먼저 확인할 것 (배포판에 따라 다를 수 있음).

## 7. 검증

1. `main`에 아무 커밋이나 push해 GitHub Actions가 정상적으로 blue↔green 전환하는지 확인 (Actions 로그에서 헬스체크 통과 → `switch-active.sh` 실행 → 이전 슬롯 stop 순서로 찍히는지).
2. `sudo systemctl status cocobackend-blue cocobackend-green`으로 매 배포 후 하나만 active(running)인지 확인.
3. `cat /home/ubuntu/cocobackend/active_color`로 현재 활성 색 확인.

## 롤백 (전환 직후 문제 발견 시)

```bash
# 예: green으로 방금 전환했는데 문제가 있어 blue로 되돌리는 경우
sudo systemctl start cocobackend-blue     # 이미 stop된 이전 슬롯 재기동
# health 확인 후
sudo /home/ubuntu/cocobackend/switch-active.sh 8080
echo blue > /home/ubuntu/cocobackend/active_color
sudo systemctl stop cocobackend-green
```
