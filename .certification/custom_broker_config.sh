#!/bin/bash

cd "$(dirname "$0")"

# Step 1: Create virtual environment if it doesn't exist
if [ ! -d ".venv" ]; then
  echo "🔧 Creating Python virtual environment..."
  python3 -m venv .venv || { echo "❌ Failed to create venv"; exit 1; }
fi

# Step 2: Activate the virtual environment
source .venv/bin/activate

# Step 3: Install 'requests' and 'tkinter' if not already installed
if ! python3 -c "import requests" &>/dev/null; then
  echo "📦 Installing 'requests'..."
  pip install requests || { echo "❌ Failed to install 'requests'"; deactivate; exit 1; }
fi

# Step 4: Run the GUI
python3 broker_config_gui.py

# Step 5: Deactivate venv after exit
deactivate