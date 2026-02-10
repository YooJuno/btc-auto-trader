#!/bin/bash

cd /home/juno/Workspace/btc-auto-trader/frontend
npm run build
sudo systemctl restart btc-frontend
