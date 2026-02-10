#!/bin/bash

# btc-frontend 설치 스크립트
cd /home/juno/Workspace/btc-auto-trader/frontend
npm install
npm run build

sudo cp /home/juno/Workspace/btc-auto-trader/etc/btc-frontend.service /etc/systemd/system/btc-frontend.service

# 데몬 리로드 및 서비스 시작
sudo systemctl daemon-reload
sudo systemctl enable --now btc-frontend

# 상태 확인
sudo systemctl status btc-frontend
