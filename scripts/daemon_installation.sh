# !/bin/bash

sudo cp /home/juno/Workspace/btc-auto-trader/scripts/btc-backend.service /etc/systemd/system/btc-backend.service
sudo cp /home/juno/Workspace/btc-auto-trader/scripts/btc-frontend.service /etc/systemd/system/btc-frontend.service

sudo systemctl daemon-reload

sudo systemctl enable --now btc-frontend
sudo systemctl enable --now btc-backend