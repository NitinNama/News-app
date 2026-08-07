import os
import sys
import json
import urllib.request
import urllib.parse

def upload_apk():
    apk_path = ".build-outputs/app-debug.apk"
    if not os.path.exists(apk_path):
        print(f"Error: APK not found at {apk_path}")
        sys.exit(1)
        
    file_size = os.path.getsize(apk_path)
    print(f"File size: {file_size / (1024*1024):.2f} MB")
    
    # Get Access Token from metadata server
    print("Fetching access token from metadata server...")
    try:
        req = urllib.request.Request(
            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token",
            headers={"Metadata-Flavor": "Google"}
        )
        with urllib.request.urlopen(req) as res:
            token_data = json.loads(res.read().decode())
            access_token = token_data["access_token"]
    except Exception as e:
        print(f"Error getting access token: {e}")
        sys.exit(1)
        
    print("Initiating resumable upload...")
    metadata = {
        "name": "Factum.apk",
        "mimeType": "application/vnd.android.package-archive"
    }
    
    init_url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
    init_data = json.dumps(metadata).encode('utf-8')
    init_headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json; charset=UTF-8",
        "X-Upload-Content-Type": "application/vnd.android.package-archive",
        "X-Upload-Content-Length": str(file_size)
    }
    
    try:
        init_req = urllib.request.Request(init_url, data=init_data, headers=init_headers, method="POST")
        with urllib.request.urlopen(init_req) as init_res:
            upload_url = init_res.getheader("Location")
            if not upload_url:
                print("Error: Location header not found in resumable upload initiation response.")
                sys.exit(1)
    except Exception as e:
        print(f"Error initiating resumable upload: {e}")
        sys.exit(1)
        
    print(f"Uploading file chunks to: {upload_url}")
    try:
        with open(apk_path, "rb") as f:
            file_data = f.read()
            
        upload_req = urllib.request.Request(
            upload_url,
            data=file_data,
            headers={
                "Content-Length": str(file_size),
                "Content-Type": "application/vnd.android.package-archive"
            },
            method="PUT"
        )
        with urllib.request.urlopen(upload_req) as upload_res:
            upload_result = json.loads(upload_res.read().decode())
            file_id = upload_result.get("id")
            print(f"Upload complete! File ID: {file_id}")
    except Exception as e:
        print(f"Error uploading file content: {e}")
        sys.exit(1)
        
    print("Setting permissions to public (anyone with link can read)...")
    permission_url = f"https://www.googleapis.com/drive/v3/files/{file_id}/permissions"
    permission_data = json.dumps({
        "role": "reader",
        "type": "anyone"
    }).encode('utf-8')
    permission_headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json"
    }
    try:
        perm_req = urllib.request.Request(permission_url, data=permission_data, headers=permission_headers, method="POST")
        with urllib.request.urlopen(perm_req) as perm_res:
            print("Permissions updated successfully!")
    except Exception as e:
        print(f"Warning: Failed to set public permissions: {e}")
        
    print("Fetching file details...")
    details_url = f"https://www.googleapis.com/drive/v3/files/{file_id}?fields=webContentLink,webViewLink"
    details_headers = {
        "Authorization": f"Bearer {access_token}"
    }
    try:
        det_req = urllib.request.Request(details_url, headers=details_headers, method="GET")
        with urllib.request.urlopen(det_req) as det_res:
            details = json.loads(det_res.read().decode())
            download_link = details.get("webContentLink")
            view_link = details.get("webViewLink")
            print("\n" + "="*50)
            print("GOOGLE DRIVE UPLOAD SUCCESSFUL!")
            print(f"File Name: Factum.apk")
            print(f"Download Link: {download_link}")
            print(f"View Link: {view_link}")
            print("="*50 + "\n")
    except Exception as e:
        print(f"Error fetching file details: {e}")
        sys.exit(1)

if __name__ == "__main__":
    upload_apk()
