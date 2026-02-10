# !/bin/bash

# btc-backend 데몬 설치 스크립트
sudo cp /home/juno/Workspace/btc-auto-trader/etc/btc-backend.service /etc/systemd/system/btc-backend.service

# 데몬 리로드 및 서비스 시작
sudo systemctl daemon-reload
sudo systemctl enable --now btc-backend

# 상태 확인
sudo systemctl status btc-backend
