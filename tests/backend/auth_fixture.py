"""Disposable authenticated administrator for development-only HTTP regression tests."""
import http.cookiejar
import json
import secrets
import subprocess
import urllib.request
import os

class AdminFixture:
    def __init__(self, base):
        self.base = base
        self.email = 'qa_' + secrets.token_hex(8) + '@example.test'
        self.password = secrets.token_urlsafe(24) + 'A1'
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = None
        self.user_id = None
    def sql(self, statement):
        subprocess.run(['docker', 'exec', '-i', os.environ.get('DB_CONTAINER', 'datvexe-postgres'),
            'psql', '-U', 'datvexe_user', '-d', 'datvexe', '-v', 'ON_ERROR_STOP=1', '-c', statement],
            check=True, stdout=subprocess.DEVNULL)
    def send(self, method, path, body=None):
        req = urllib.request.Request(self.base + path, method=method,
            data=None if body is None else json.dumps(body).encode(), headers=self.headers())
        with self.opener.open(req, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    def headers(self):
        return {'Content-Type': 'application/json', 'X-Requested-With': 'datvexe',
                **({'X-CSRF-Token': self.csrf} if self.csrf else {})}
    def start(self):
        profile = self.send('POST', '/auth/dang-ky', {'hoTen':'Kiểm thử', 'email':self.email,
            'soDienThoai':'09' + ''.join(secrets.choice('0123456789') for _ in range(8)), 'matKhau':self.password})
        self.user_id = profile['maNguoiDung']
        self.sql(f"UPDATE nguoi_dung SET loai_tai_khoan='ADMIN' WHERE ma_nguoi_dung='{self.user_id}'")
        self.csrf = self.send('POST', '/auth/dang-nhap', {'email':self.email,'matKhau':self.password})['csrfToken']
        return self
    def close(self):
        if self.user_id:
            self.sql(f"DELETE FROM phien_dang_nhap WHERE ma_nguoi_dung='{self.user_id}'; DELETE FROM nguoi_dung WHERE ma_nguoi_dung='{self.user_id}'")
