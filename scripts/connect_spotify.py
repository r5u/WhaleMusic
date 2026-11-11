"""Local Spotify PKCE setup. Reads playlist metadata only and stores renewable credentials beside config.txt."""
import argparse
import base64
import hashlib
import html
import json
import os
from pathlib import Path
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen

REDIRECT = 'http://127.0.0.1:8765/callback'
SCOPES = 'playlist-read-private playlist-read-collaborative'


def token_request(fields):
    request = Request('https://accounts.spotify.com/api/token', data=urlencode(fields).encode(),
                      headers={'Content-Type': 'application/x-www-form-urlencoded'})
    with urlopen(request, timeout=20) as response:
        return json.load(response)


def save_credentials(path, client_id, tokens):
    if not tokens.get('refresh_token') or not tokens.get('access_token'):
        raise ValueError('Spotify did not return renewable authorization. Please connect again.')
    content = '# Spotify user authorization. Keep this file private.\n'
    content += 'clientId=' + client_id + '\n'
    content += 'refreshToken=' + tokens['refresh_token'] + '\n'
    content += 'accessToken=' + tokens['access_token'] + '\n'
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(content, encoding='utf-8')
    os.replace(temporary, path)


def run(config_dir):
    config_dir = config_dir.resolve()
    if not config_dir.is_dir():
        raise ValueError('The bot folder does not exist.')
    credentials = config_dir / 'spotify.properties'
    flow = {}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Callback codes and tokens must never be written to the console.

        def page(self, title, body, status=200):
            data = ('<!doctype html><meta charset="utf-8"><title>' + html.escape(title) + '</title>'
                    '<style>body{font:18px system-ui;max-width:650px;margin:70px auto;padding:20px;background:#121212;color:#eee}'
                    'input,button{font:inherit;padding:12px;margin:10px 0}input{width:90%}button{background:#1ed760;border:0;border-radius:8px}'
                    'code{color:#1ed760}</style><h1>' + html.escape(title) + '</h1>' + body).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Referrer-Policy', 'no-referrer')
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            request = urlsplit(self.path)
            query = parse_qs(request.query)
            if request.path == '/':
                self.page('Connect Spotify to loserMusic',
                          '<p>In your Spotify app settings, add this Redirect URI:</p><p><code>' + REDIRECT + '</code></p>'
                          '<p>Paste the app\'s Client ID below. A Client Secret is not needed.</p>'
                          '<form action="/authorize"><label>Client ID<input name="clientId" required pattern="[a-fA-F0-9]{32}" autocomplete="off"></label>'
                          '<button>Connect Spotify</button></form>'
                          '<p>Spotify will ask permission to read private and collaborative playlist metadata. '
                          'This does not grant playback control or permission to change playlists. '
                          'A renewable token will be saved in the bot folder. Only playlists permitted by Spotify\'s app mode can be read.</p>')
                return
            if request.path == '/authorize':
                client_id = query.get('clientId', [''])[0].strip()
                if len(client_id) != 32 or any(c not in '0123456789abcdefABCDEF' for c in client_id):
                    self.page('Check the Client ID', '<p>Paste the 32-character Client ID from your Spotify developer app.</p>', 400)
                    return
                verifier = secrets.token_urlsafe(64)
                flow.update(clientId=client_id, verifier=verifier, state=secrets.token_urlsafe(32), created=time.time())
                challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
                url = 'https://accounts.spotify.com/authorize?' + urlencode({
                    'client_id': client_id, 'response_type': 'code', 'redirect_uri': REDIRECT,
                    'scope': SCOPES, 'state': flow['state'], 'code_challenge_method': 'S256', 'code_challenge': challenge,
                })
                self.send_response(302)
                self.send_header('Location', url)
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                return
            if request.path == '/callback':
                state = query.get('state', [''])[0]
                if not flow or not secrets.compare_digest(state, flow.get('state', '')) or time.time() - flow['created'] > 600:
                    self.page('Connection expired', '<p>Open the setup page again to reconnect.</p>', 400)
                    return
                if 'error' in query:
                    flow.clear()
                    self.page('Spotify was not connected', '<p>You can return to the setup page and try again. No credentials were saved.</p>', 400)
                    return
                try:
                    code = query.get('code', [''])[0]
                    if not code:
                        raise ValueError('No authorization code received.')
                    tokens = token_request({'grant_type': 'authorization_code', 'code': code,
                                            'redirect_uri': REDIRECT, 'client_id': flow['clientId'],
                                            'code_verifier': flow['verifier']})
                    save_credentials(credentials, flow['clientId'], tokens)
                    flow.clear()
                    self.page('Spotify connected', '<p>Renewable playlist access was saved securely in the bot folder.</p>'
                              '<p>Restart MusicBot to load it. The bot can now request full playlists that Spotify allows this account to access.</p>')
                    print('Spotify authorization saved. No token values were printed.', flush=True)
                    threading.Thread(target=self.server.shutdown, daemon=True).start()
                except Exception:
                    flow.clear()
                    self.page('Could not finish connecting', '<p>Check the app\'s Redirect URI and account eligibility, then retry. No token values are shown here.</p>', 400)
                return
            self.page('Page not found', '<p>Open http://127.0.0.1:8765/ to start.</p>', 404)

    server = HTTPServer(('127.0.0.1', 8765), Handler)
    print('Spotify setup is ready at http://127.0.0.1:8765/', flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--bot-folder', type=Path, default=Path(__file__).resolve().parent)
    args = parser.parse_args()
    run(args.bot_folder)
