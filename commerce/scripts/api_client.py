"""Small session + CSRF client used by smoke checks and repeatable evaluations."""
import http.cookiejar
import json
import os
import urllib.request
import urllib.error

class Client:
    def __init__(self, base=None):
        self.base = base or os.environ.get('CI_API_URL', 'http://127.0.0.1:8065')
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def request(self, path, method='GET', body=None, headers=None, csrf=True):
        request_headers = dict(headers or {})
        if method != 'GET' and csrf:
            _, token = self.request('/api/auth/csrf')
            request_headers[token['headerName']] = token['token']
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode()
            request_headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(self.base + path, data=data, headers=request_headers, method=method)
        try:
            with self.opener.open(request, timeout=40) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                payload = json.loads(raw) if raw else None
            except ValueError:
                payload = {'message': raw.decode(errors='replace')[:200]}
            return error.code, payload

    def login(self, username):
        status, data = self.request('/api/auth/login', 'POST', {'username': username, 'password': os.environ.get('CI_DEMO_PASSWORD', 'CommerceDemo!2026')})
        if status != 200:
            raise RuntimeError(f'Login failed for {username}: HTTP {status}, {data}')
        return data
