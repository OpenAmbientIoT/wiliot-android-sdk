import tkinter as tk
from tkinter import ttk, messagebox
import requests

def execute():
    try:
        port = int(port_var.get())
        broker_url = broker_url_var.get().strip()
        owner_id = owner_id_var.get().strip()
        username = username_var.get().strip()
        password = password_var.get().strip()
        gateway_id = gateway_id_var.get().strip()
        api_key = api_key_var.get().strip()
        env = env_var.get().strip()

        if not gateway_id or not api_key:
            messagebox.showerror("Missing fields", "Gateway ID and API Key are required.")
            return

        base_urls = {
            "AWS_PROD": "https://api.us-east-2.prod.wiliot.cloud",
        }

        base_url = base_urls.get(env)
        if not base_url:
            messagebox.showerror("Invalid Environment", f"Unknown environment: {env}")
            return

        # Step 1: Get access token
        token_url = f"{base_url}/v1/auth/token/api"
        token_headers = {"Authorization": api_key}
        token_response = requests.post(token_url, headers=token_headers)

        if token_response.status_code != 200:
            messagebox.showerror("Auth Failed", f"Token error:\n{token_response.text}")
            return

        access_token = token_response.json().get("access_token")
        if not access_token:
            messagebox.showerror("Token Error", "No access token in response.")
            return

        # Step 2: Send custom-message
        custom_msg_url = f"{base_url}/v1/owner/{owner_id}/gateway/{gateway_id}/custom-message"
        headers = {
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json"
        }
        payload = {
            "customBroker": True,
            "port": port,
            "brokerUrl": broker_url,
            "username": username,
            "password": password,
            "updateTopic": f"update-test/{owner_id}/{gateway_id}",
            "statusTopic": f"status-test/{owner_id}/{gateway_id}",
            "dataTopic": f"data-test/{owner_id}/{gateway_id}"
        }

        response = requests.post(custom_msg_url, headers=headers, json=payload)

        if response.status_code == 200:
            messagebox.showinfo("Success", "Custom message sent successfully.")
        else:
            messagebox.showerror("Failed", f"Custom message failed:\n{response.text}")

    except Exception as e:
        messagebox.showerror("Exception", str(e))


# --- GUI Layout ---
root = tk.Tk()
root.title("Custom Broker Config")

# Default values
port_var = tk.StringVar(value="8883")
broker_url_var = tk.StringVar(value="mqtts://mqtt.eclipseprojects.io")
owner_id_var = tk.StringVar(value="wiliot")
username_var = tk.StringVar()
password_var = tk.StringVar()
gateway_id_var = tk.StringVar()
api_key_var = tk.StringVar()
env_var = tk.StringVar(value="AWS_PROD")

# Input Fields
entries = [
    ("Port", port_var),
    ("Broker URL", broker_url_var),
    ("Owner ID", owner_id_var),
    ("Username", username_var),
    ("Password", password_var),
    ("Gateway ID*", gateway_id_var),
    ("API Key*", api_key_var)
]

for i, (label, var) in enumerate(entries):
    tk.Label(root, text=label).grid(row=i, column=0, sticky="e", padx=5, pady=2)
    tk.Entry(root, textvariable=var, width=50).grid(row=i, column=1, padx=5, pady=2)

# Environment dropdown
tk.Label(root, text="Environment").grid(row=len(entries), column=0, sticky="e", padx=5, pady=2)
ttk.Combobox(root, textvariable=env_var, values=[
    "AWS_DEV", "AWS_TEST", "AWS_PROD",
    "GCP_DEV", "WMT_NON_PROD", "WMT_PROD"
], state="readonly", width=48).grid(row=len(entries), column=1, padx=5, pady=2)

# Execute button
tk.Button(root, text="Execute", command=execute).grid(row=len(entries)+1, column=0, columnspan=2, pady=12)

root.mainloop()
