async function main() {
  const loginRes = await fetch('http://localhost:9090/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: '123456' })
  });
  const loginJson = await loginRes.json();
  console.log('LOGIN_STATUS', loginRes.status);
  console.log('LOGIN_TOKEN_PRESENT', !!loginJson.token);
  const token = loginJson.token;

  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAFb6QHAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJ0UkGAAAAAElFTkSuQmCC', 'base64');
  const form = new FormData();
  form.append('file', new Blob([png], { type: 'image/png' }), 'runtime-check.png');

  const uploadRes = await fetch('http://localhost:9090/api/me/avatar', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + token },
    body: form
  });
  const uploadText = await uploadRes.text();
  console.log('UPLOAD_STATUS', uploadRes.status);
  console.log('UPLOAD_BODY', uploadText.slice(0, 500));

  const meRes = await fetch('http://localhost:9090/api/me', {
    headers: { Authorization: 'Bearer ' + token }
  });
  const meText = await meRes.text();
  console.log('ME_STATUS', meRes.status);
  console.log('ME_BODY', meText.slice(0, 500));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
