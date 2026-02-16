#!/bin/bash

# build back-end jar
cd /home/juno/Workspace/btc-auto-trader/apps/backend
./gradlew bootJar

# build front-end
cd /home/juno/Workspace/btc-auto-trader/apps/frontend
npm install
npm run build

sudo cp /home/juno/Workspace/btc-auto-trader/scripts/btc-backend.service /etc/systemd/system/btc-backend.service
sudo cp /home/juno/Workspace/btc-auto-trader/scripts/btc-frontend.service /etc/systemd/system/btc-frontend.service

sudo systemctl daemon-reload