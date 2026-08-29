#!/usr/bin/env bash
# Blue/Green 배포 — nginx 업스트림을 지정한 포트로 전환.
# 사용법: switch-active.sh <8080|8081>
# 서버 배치: /home/ubuntu/cocobackend/switch-active.sh (chmod +x)
# CI(GitHub Actions)가 sudo로 이 스크립트만 호출하도록 sudoers에 NOPASSWD 등록.
# 최초 1회 설정 절차 전체는 deploy/BLUEGREEN_SETUP.md 참고.
set -euo pipefail

PORT="${1:-}"
if [[ "$PORT" != "8080" && "$PORT" != "8081" ]]; then
  echo "usage: switch-active.sh <8080|8081>" >&2
  exit 1
fi

UPSTREAM_CONF=/etc/nginx/conf.d/cocobackend_upstream.conf

cat > "$UPSTREAM_CONF" <<EOF
upstream cocobackend {
    server 127.0.0.1:${PORT};
}
EOF

nginx -t
systemctl reload nginx
