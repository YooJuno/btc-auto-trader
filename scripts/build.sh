# !/bin/bash

# build front-end
cd /home/juno/Workspace/btc-auto-trader/apps/frontend
npm install
npm run build

sudo systemctl daemon-reload

sudo systemctl enable --now btc-backend
sudo systemctl enable --now btc-frontend