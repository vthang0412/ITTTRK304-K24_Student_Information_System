# SIS - Triển khai Production lên Ubuntu VPS (CI/CD)

Tài liệu này mô tả quy trình Continuous Deployment (CD) cho Student Information System (SIS):
GitHub Actions build image, push lên GHCR, SSH vào Ubuntu VPS và triển khai bằng Docker Compose.

> Trạng thái: cấu hình đã được kiểm tra cú pháp cục bộ. **Chưa được chạy trên VPS thật**.
> Chỉ sau khi hoàn thành mục [Kiểm thử trên VPS](#10-kiểm-thử-trên-vps) mới được coi là deploy thành công.

---

## 1. Kiến trúc triển khai

```
Internet
   │  :80 / :443
   ▼
Host Nginx (TLS, certbot)                <- trên VPS
   │  proxy tới 127.0.0.1:8080
   ▼
sis-frontend (Nginx trong container)     <- image GHCR
   │  /api/, /uploads/, /swagger-ui/, /v3/api-docs/
   ▼
sis-backend (Spring Boot :8080)          <- image GHCR, không publish port
   │  jdbc:mysql://mysql:3306
   ▼
sis-mysql (MySQL 8.0)                    <- volume sis_mysql_data, không publish port
```

- Frontend và backend chỉ giao tiếp trong Docker network `sis-network`.
- MySQL **không** expose ra Internet.
- Frontend chỉ bind `127.0.0.1:8080`; host Nginx là điểm vào duy nhất.
- Volume `sis_mysql_data` và `sis_backend_uploads` được giữ nguyên qua mọi lần deploy.

---

## 2. File mới / file đã sửa

| File | Mô tả |
| --- | --- |
| `.github/workflows/deploy.yml` | Workflow CD: chờ CI xong, build/push GHCR, SSH deploy, kiểm tra health. |
| `deploy/docker-compose.prod.yml` | Stack production dùng image GHCR, volume bền vững, healthcheck, restart policy. |
| `deploy/deploy.sh` | Backup DB, pull image, `up -d` không xoá volume, chờ health, smoke test, ghi tag. |
| `deploy/backup.sh` | Dump MySQL nén `.sql.gz`, giữ N bản gần nhất. |
| `deploy/rollback.sh` | Quay lại tag trước hoặc tag chỉ định. |
| `deploy/restore.sh` | Khôi phục database từ file backup (có xác nhận, không tự chạy khi deploy). |
| `deploy/env.production.example` | Mẫu `.env` production (chỉ placeholder, không chứa secret thật). |
| `backend/.../application-dev.properties` | Profile development: Flyway + Hibernate validate, demo seed, Swagger, CORS localhost. |
| `backend/.../application-prod.properties` | Profile production: Hibernate validate, không seed demo, Swagger tắt, Flyway. |
| `backend/.../db/migration/V1__baseline_schema.sql` | Baseline schema cho database production mới. |
| `deploy/nginx/sis.conf` | Mẫu reverse proxy Nginx trên host + hướng dẫn HTTPS. |
| `docs/DEPLOYMENT.md` | Tài liệu này. |
| `.gitattributes` | Ép LF cho script/YAML để chạy đúng trên Ubuntu. |

Không sửa nghiệp vụ, không đổi MySQL, không viết lại backend/frontend, không sửa CI hiện tại.

---

## 3. Cách CI/CD hoạt động

1. Push/merge vào `main`.
2. Workflow `CI` hiện có chạy:
   - Backend: Java 21 + `./gradlew build`.
   - Frontend: Node 22 + `npm ci`, `npm run lint`, `npm run build`.
3. Khi `CI` kết thúc **thành công** trên `main`, workflow `Deploy to VPS` được kích hoạt bằng `workflow_run`.
4. Job **Build and push**:
   - Checkout đúng commit SHA của lần CI.
   - Build backend và frontend.
   - Gắn tag `<commit-sha>` và `latest`.
   - Push lên GHCR, dùng cache của GitHub Actions.
5. Job **Deploy to Ubuntu VPS** (chỉ chạy khi CI thành công trên `main`, và cần approval nếu
   GitHub Environment `production` có required reviewers):
   - Upload `docker-compose.prod.yml`, `deploy.sh`, `backup.sh`, `rollback.sh`, `restore.sh` lên `/opt/sis`.
   - SSH vào VPS (private key trong GitHub Secrets).
   - Nếu có `GHCR_PULL_TOKEN`, đăng nhập GHCR trên VPS trước khi pull.
   - Chạy `deploy.sh`.
6. `deploy.sh`:
   - Backup MySQL (khi stack đang chạy).
   - `docker compose pull backend frontend`.
   - `docker compose up -d --remove-orphans` (không `down`, không `-v`).
   - Chờ healthcheck `mysql`, `backend`, `frontend`.
   - Smoke test frontend và proxy `/actuator/health` từ trong container.
   - Chỉ ghi `.current_tag` sau khi tất cả thành công.
7. Nếu container mới không healthy, `deploy.sh` tự động rollback về tag đang chạy trước đó
   (khi `AUTO_ROLLBACK=1`), giữ nguyên volume database, kiểm tra health lại và báo lỗi rõ ràng.
   Nếu rollback cũng thất bại, job fail và cần can thiệp thủ công.
8. Merge vào `develop`: CI chạy, sau đó workflow `Deploy` chỉ **build/push image tag dev** lên GHCR
   (tag commit SHA và `dev`). Không có bước deploy production từ `develop`.

> `workflow_run` chỉ chạy khi file `deploy.yml` đã có trên nhánh mặc định (`main`).
> Lần đầu cần merge workflow này vào `main` (hoặc chạy `workflow_dispatch`).

---

## 4. GitHub Secrets cần tạo

Vào **Repository → Settings → Secrets and variables → Actions**:

| Secret | Bắt buộc | Giá trị |
| --- | --- | --- |
| `VPS_HOST` | Có | IP hoặc hostname của VPS. |
| `VPS_USER` | Có | User deploy, ví dụ `deploy`. |
| `VPS_SSH_PRIVATE_KEY` | Có | Nội dung **private key** (cả dòng `BEGIN`/`END`). |
| `VPS_PORT` | Không | Mặc định `22`. **Không tự đổi SSH port đang dùng.** |
| `VPS_DEPLOY_PATH` | Không | Mặc định `/opt/sis`. |
| `VPS_KNOWN_HOSTS` | Nên có | Output của `ssh-keyscan`; nếu không có, workflow dùng `ssh-keyscan` và sẽ cảnh báo. |
| `GHCR_PULL_TOKEN` | Không | PAT có quyền `read:packages`, dùng khi package GHCR để private. |
| `GHCR_USER` | Không | Tài khoản sở hữu `GHCR_PULL_TOKEN`. Mặc định dùng `github.repository_owner`. |

Không đưa `.env` production lên GitHub. `.env` chỉ nằm trên VPS.

> `GHCR_USER` và `GHCR_PULL_TOKEN` phải thuộc **cùng một tài khoản**. Nếu dùng máy/tài khoản
> riêng để pull, đặt `GHCR_USER` đúng username của token đó. Workflow không in token ra log;
> token được truyền qua stdin của `docker login --password-stdin`.

### GHCR private hay public

- **Cách A (khuyến nghị cho production):** giữ package private, tạo PAT classic với scope `read:packages`
  (hoặc fine-grained token có quyền đọc Packages), lưu vào `GHCR_PULL_TOKEN`.
- **Cách B (đơn giản):** vào GitHub → Packages → từng package → Settings → Change visibility → Public.
  Khi đó không cần `GHCR_PULL_TOKEN`.

Workflow build/push dùng `GITHUB_TOKEN` với `permissions.packages: write`, không cần PAT để push.

---

## 5. Chuẩn bị VPS

Các lệnh dưới đây chạy trên Ubuntu VPS.

### 5.1. Cài Docker Engine + Compose plugin

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y ca-certificates curl gnupg git ufw

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker

docker --version
docker compose version
```

### 5.2. Tạo user deploy và thư mục triển khai

```bash
sudo useradd --create-home --shell /bin/bash deploy
sudo usermod -aG docker deploy
sudo mkdir -p /opt/sis/backups
sudo chown -R deploy:deploy /opt/sis
sudo chmod 750 /opt/sis
```

Sau khi thêm group, đăng xuất/đăng nhập lại SSH (hoặc chạy `newgrp docker`) để user `deploy` dùng được Docker.

Kiểm tra:

```bash
sudo -u deploy docker ps
```

### 5.3. Cấu hình SSH key cho GitHub Actions

Trên máy local:

```bash
ssh-keygen -t ed25519 -C "github-actions-sis" -f sis_deploy -N ""
ssh-copy-id -i sis_deploy.pub deploy@YOUR_VPS_HOST
ssh -i sis_deploy -p 22 deploy@YOUR_VPS_HOST "docker ps"
```

Nếu không dùng `ssh-copy-id`, thêm thủ công public key vào VPS:

```bash
sudo -u deploy mkdir -p /home/deploy/.ssh
sudo -u deploy chmod 700 /home/deploy/.ssh
sudo -u deploy sh -c 'cat >> /home/deploy/.ssh/authorized_keys' < sis_deploy.pub
sudo -u deploy chmod 600 /home/deploy/.ssh/authorized_keys
```

- Dán nội dung file `sis_deploy` (private key) vào secret `VPS_SSH_PRIVATE_KEY`.
- Lấy known hosts (tuỳ chọn nhưng nên làm):

```bash
ssh-keyscan -p 22 -H YOUR_VPS_HOST
```

Dán output vào secret `VPS_KNOWN_HOSTS`.

### 5.4. Tạo production `.env` trên VPS

```bash
sudo -u deploy nano /opt/sis/.env
```

Nội dung mẫu ở `deploy/env.production.example`. Tạo secret mạnh bằng:

```bash
openssl rand -base64 48
```

Sau khi tạo:

```bash
sudo chown deploy:deploy /opt/sis/.env
sudo chmod 600 /opt/sis/.env
```

Lưu ý:
- `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET` phải là giá trị thật, không dùng placeholder.
- `SPRING_PROFILES_ACTIVE=prod` và `SPRING_SQL_INIT_MODE=never` là mặc định của production.
  Tài khoản demo `admin/123456` **không bao giờ** được seed vào production.
- Flyway (`SPRING_FLYWAY_ENABLED=true`) tạo schema trên database mới từ `V1` đến `V9`.
  Với database đã có schema nhưng chưa có `flyway_schema_history`, Flyway baseline ở **version 6**
  (V2/V4/V5 không idempotent nên phải bỏ qua), sau đó chạy `V7` để đồng bộ cấu trúc còn thiếu và
  nới precision các cột điểm lên `DECIMAL(5,2)`, `V8` để chuẩn hóa duyệt đăng ký và trạng thái đọc
  thông báo, `V9` để lưu lịch sử lịch học có ngày hiệu lực. **Không hạ
  `spring.flyway.baseline-version` về 1.** Luôn backup và kiểm tra trước khi nâng cấp database cũ;
  Hibernate `validate` sẽ báo lỗi nếu schema vẫn lệch sau migration.
- Production mặc định tắt Swagger (`SWAGGER_ENABLED=false`).
- `SPRING_SQL_INIT_MODE` chỉ nên đổi khỏi `never` khi thật sự cần chạy seed có kiểm soát.
- Không commit file `.env` này.
- **Không đổi `DB_PASSWORD`/`DB_ROOT_PASSWORD` sau khi volume MySQL đã được khởi tạo**: MySQL chỉ
  áp dụng các biến này ở lần init đầu tiên. Nếu muốn đổi, phải đổi trong MySQL trước.
- Đổi `JWT_SECRET` sẽ làm các token cũ mất hiệu lực (người dùng phải đăng nhập lại).
- Production không có tài khoản demo. Tạo tài khoản Admin đầu tiên **thủ công** bằng một mật khẩu
  mạnh (BCrypt hash) trước khi mở hệ thống. Không dùng `admin/123456`.

### 5.5. Firewall (UFW)

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

Không mở `3306` (MySQL) và không mở `8080` ra ngoài.
Frontend mặc định bind `127.0.0.1:8080` nên chỉ host Nginx truy cập được.

---

## 6. Domain và HTTPS

### 6.1. DNS

Trỏ bản ghi `A` (và `AAAA` nếu có IPv6) của domain về IP VPS:

```
sis.example.com.  A  YOUR_VPS_IP
```

Chờ DNS propagate trước khi chạy certbot.

### 6.2. Host Nginx + certbot

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

Copy `deploy/nginx/sis.conf` lên VPS (hoặc tạo file trực tiếp):

```bash
# Từ máy local
scp deploy/nginx/sis.conf deploy@YOUR_VPS_HOST:/tmp/sis.conf
```

Trên VPS:

```bash
sudo cp /tmp/sis.conf /etc/nginx/sites-available/sis
sudo sed -i 's/sis.example.com/your-real-domain/g' /etc/nginx/sites-available/sis
sudo ln -sf /etc/nginx/sites-available/sis /etc/nginx/sites-enabled/sis
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d your-real-domain --redirect
sudo certbot renew --dry-run
```

Certbot sẽ tự thêm block `listen 443 ssl` và redirect HTTP → HTTPS.

### 6.3. Khi chưa có domain

Có thể kiểm tra tạm bằng SSH tunnel (không mở port ra Internet):

```bash
ssh -L 8080:127.0.0.1:8080 deploy@YOUR_VPS_HOST
# Sau đó mở http://localhost:8080 trên máy local
```

Chỉ dùng `FRONTEND_BIND_IP=0.0.0.0` + mở firewall tạm thời khi thật sự cần test,
sau đó đổi lại `127.0.0.1`.

---

## 7. Deploy

### 7.1. Deploy tự động

1. Hoàn thành mục 4, 5, 6.
2. Merge code vào `main`.
3. Theo dõi **Actions → CI → Deploy to VPS**.
4. Job deploy chạy `deploy.sh` trên VPS.

### 7.2. Deploy thủ công một tag có sẵn

**Actions → Deploy to VPS → Run workflow**, nhập `image_tag` là commit SHA đã có image trên GHCR.
Workflow này chỉ deploy, không build lại.

Hoặc trực tiếp trên VPS:

```bash
cd /opt/sis
chmod +x deploy.sh backup.sh rollback.sh
IMAGE_TAG=<commit-sha> \
BACKEND_IMAGE=ghcr.io/<owner>/<repo>-backend \
FRONTEND_IMAGE=ghcr.io/<owner>/<repo>-frontend \
./deploy.sh
```

Biến `BACKEND_IMAGE` / `FRONTEND_IMAGE` có thể đặt sẵn trong `/opt/sis/.env`.

### 7.3. Kiểm tra nhanh trên VPS

```bash
cd /opt/sis
docker compose -f docker-compose.prod.yml --env-file .env ps
docker inspect -f '{{.State.Health.Status}}' sis-mysql sis-backend sis-frontend
curl -I http://127.0.0.1:8080/
docker volume ls | grep sis_
cat .current_tag .previous_tag 2>/dev/null
```

Compose project được tách riêng: development mới là `sis-dev-local` (container `sis-dev-local-*`),
production là `sis-prod` (container `sis-*`). Project `sis-dev-local` dùng volume riêng; stack
development cũ `sis-dev` và volume của nó được giữ nguyên, không cần gỡ để chạy project mới.
Không dùng `docker compose down -v`.
MySQL development mặc định publish tại `127.0.0.1:3308` để không đụng cổng `3307` của stack khác;
không đổi `.env.local` sang một container khác nếu chưa xác minh profile và Database đích.
Để chạy stack dev mới mà không đọc/ghi root `.env`, tạo `.env.isolated` từ `.env.example`, đặt credential
local riêng và chạy `docker compose --env-file .env.isolated up --build -d`. Stack dùng volume
`sis-dev-local_mysql_data`; không thêm `-v` khi dừng stack.

---

## 8. Rollback

Rollback chỉ đổi image tag, **không xoá volume**, và luôn backup DB trước khi đổi.
`deploy.sh` tự rollback một lần khi tag mới không healthy (`AUTO_ROLLBACK=1`). `.previous_tag`
không bao giờ trỏ về tag vừa thất bại, nên lần rollback kế tiếp không quay nhầm vào bản lỗi.

```bash
cd /opt/sis

# Quay lại tag ngay trước đó (được ghi tự động sau mỗi deploy thành công)
./rollback.sh

# Hoặc chỉ định tag cụ thể
./rollback.sh <commit-sha>

# Trường hợp khẩn cấp: MySQL không dump được nhưng vẫn cần quay lại image cũ
SKIP_BACKUP=1 ./rollback.sh <commit-sha>
```

Hoặc dùng **Actions → Deploy to VPS → Run workflow** với `image_tag` của phiên bản cũ.

Sau rollback, kiểm tra lại health và dữ liệu:

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
curl -I http://127.0.0.1:8080/
```

Không chạy `docker system prune -a` trên VPS nếu còn cần image cũ để rollback.

---

## 9. Backup và khôi phục dữ liệu

### 9.1. Backup

`deploy.sh` bảo đảm container MySQL đang chạy rồi mới chạy `backup.sh` trước mỗi lần deploy (kể cả
khi stack đang dừng), và **hủy deploy** nếu backup thất bại. Có thể chạy tay:

```bash
cd /opt/sis
./backup.sh
ls -lh backups/
```

File được lưu tại `/opt/sis/backups/sis_<UTC timestamp>.sql.gz`; mặc định giữ 10 bản gần nhất
(`BACKUP_RETENTION` có thể đổi). Script kiểm tra gzip, file không rỗng và marker
`-- Dump completed` để phát hiện dump bị cắt giữa chừng.

### 9.2. Khôi phục

Dừng ứng dụng để tránh ghi dữ liệu trong lúc restore:

```bash
cd /opt/sis
docker compose -f docker-compose.prod.yml --env-file .env stop frontend backend

gunzip -c backups/sis_YYYYMMDDTHHMMSSZ.sql.gz \
  | docker compose -f docker-compose.prod.yml --env-file .env exec -T mysql sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 "$MYSQL_DATABASE"'

docker compose -f docker-compose.prod.yml --env-file .env start backend frontend
```

Cách an toàn hơn là dùng script có kiểm tra và xác nhận:

```bash
cd /opt/sis
./restore.sh backups/sis_YYYYMMDDTHHMMSSZ.sql.gz
# Chỉ dùng FORCE=1 trong tình huống khẩn cấp/CI:
# FORCE=1 ./restore.sh backups/sis_YYYYMMDDTHHMMSSZ.sql.gz
```

`restore.sh` kiểm tra file tồn tại, kiểm tra `gzip -t` + marker `Dump completed`, đối chiếu tên
database với container, tự tạo một backup an toàn trước khi ghi đè (bỏ qua bằng
`SKIP_PRE_RESTORE_BACKUP=1` khi database hiện tại đã hỏng), hỏi xác nhận đúng tên database, dừng
backend/frontend trong lúc restore và không bao giờ xoá volume. Script này **không** được gọi tự động
từ `deploy.sh` hay workflow CD.

`data.sql` (chỉ dùng ở development) bổ sung seed bằng `INSERT IGNORE` và **không có
`TRUNCATE`/`DELETE`**, nên không xoá dữ liệu. Bản ghi demo ADMIN được tự sửa trạng thái/mật khẩu
chỉ khi username, email và role khớp chính xác; mật khẩu lưu dạng BCrypt hash. Production luôn dùng
`SPRING_SQL_INIT_MODE=never`, không seed demo.

Development cũng dùng Flyway làm nguồn schema duy nhất và Hibernate `validate`. Database local mới
phải được tạo từ migration; profile dev không tự baseline một schema cũ. `schema.sql` đã được gỡ bỏ
vì DDL thuộc Flyway; `data.sql` được cấu hình rõ là seed chỉ dành cho DEV và không chứa câu `CREATE`
hay `ALTER`. `data_fixed.sql` là artifact bootstrap cũ, không được Spring Boot hoặc Docker tham chiếu.
Flyway V7 quản lý precision điểm số; V12 mở rộng `day_of_week` sang `INT` và giới hạn giá trị 1–7 mà
không xóa dữ liệu. Trước khi dùng profile dev trên database đã có schema nhưng chưa có Flyway history,
cần backup và xác nhận đúng database local trước khi reset hoặc lập kế hoạch baseline riêng.

Flyway validate checksum khi khởi động. Không sửa migration V1–V11 đã áp dụng để xử lý lỗi schema;
hãy thêm migration tiến về phía trước và kiểm tra lịch sử database. Flyway chạy trước SQL
initialization; chỉ profile DEV chạy `data.sql`, còn Production giữ `spring.sql.init.mode=never`.

### 9.3. Backup tự động (cron hoặc systemd timer)

Ví dụ cron chạy hằng ngày lúc 02:00 (giữ tối đa `BACKUP_RETENTION` bản):

```cron
0 2 * * * cd /opt/sis && ./backup.sh >> /var/log/sis-backup.log 2>&1
```

Hoặc systemd timer:

```ini
# /etc/systemd/system/sis-backup.service
[Unit]
Description=SIS MySQL backup
[Service]
Type=oneshot
WorkingDirectory=/opt/sis
ExecStart=/opt/sis/backup.sh
User=deploy
Group=deploy
```

```ini
# /etc/systemd/system/sis-backup.timer
[Unit]
Description=Daily SIS MySQL backup
[Timer]
OnCalendar=*-*-* 02:00:00
Persistent=true
[Install]
WantedBy=timers.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now sis-backup.timer
```

### 9.4. Backup offsite

Sao chép backup ra ngoài VPS sau mỗi lần chạy, ví dụ:

```bash
# rclone (S3, Backblaze, Google Drive...)
rclone copy /opt/sis/backups remote:sis-backups --include 'sis_*.sql.gz'

# hoặc scp sang máy khác
scp /opt/sis/backups/sis_*.sql.gz backup-host:/backups/sis/
```

Giữ file backup ở chế độ `600`, thư mục `700` (script đã đặt sẵn) và mã hoá offsite nếu có thể.

---

## 10. Kiểm thử trên VPS

Checklist bắt buộc trước khi coi là deploy thành công:

| # | Kiểm tra | Lệnh / cách kiểm tra | Kết quả mong đợi |
| --- | --- | --- | --- |
| 1 | CI xanh | GitHub Actions → CI | Cả backend và frontend success |
| 2 | Build và push GHCR | GitHub Actions → Deploy → Build and push | Có tag `<sha>` và `latest` |
| 3 | SSH connection | Workflow bước Configure SSH / Upload | Không lỗi |
| 4 | Pull image trên VPS | `docker compose ... pull` trong log deploy | Pull thành công |
| 5 | Compose deployment | `docker compose ... ps` | 3 container `Up (healthy)` |
| 6 | MySQL persistence | `docker volume ls \| grep sis_mysql_data` | Volume tồn tại |
| 7 | Backend healthcheck | `docker inspect -f '{{.State.Health.Status}}' sis-backend` | `healthy` |
| 8 | Frontend truy cập | `curl -I http://127.0.0.1:8080/` | HTTP 200 |
| 9 | Swagger tắt mặc định | `curl -I https://domain/swagger-ui.html` | `404` (chỉ bật khi `SWAGGER_ENABLED=true`) |
| 10 | Health endpoint | `curl https://domain/actuator/health` | `{"status":"UP"}`, không lộ chi tiết DB |
| 11 | Demo account không tồn tại | Đăng nhập `admin/123456` | Thất bại |
| 12 | API login tài khoản thật | `POST https://domain/api/auth/login` | Trả token / 200 |
| 13 | Dữ liệu còn nguyên | Deploy lại rồi kiểm tra dữ liệu | Không mất dữ liệu |

Kiểm tra API login bằng tài khoản production do bạn tạo thủ công (không dùng tài khoản demo):

```bash
curl -i -X POST https://your-real-domain/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"YOUR_ADMIN_USERNAME","password":"YOUR_STRONG_PASSWORD"}'
```

### Kết quả kiểm thử thực tế

Đã chạy cục bộ (không phải trên VPS):

| Kiểm tra | Lệnh | Kết quả |
| --- | --- | --- |
| YAML workflow + compose | `python -c "import yaml; yaml.safe_load(...)"` | OK |
| Shell syntax | `bash -n deploy/deploy.sh deploy/backup.sh deploy/rollback.sh` | OK |
| Compose production | `IMAGE_TAG=test-sha docker compose -f deploy/docker-compose.prod.yml --env-file deploy/env.production.example config --quiet` | OK |
| Port/volume resolve | `docker compose ... config` | MySQL không publish port; frontend chỉ `127.0.0.1:8080`; volume `sis_mysql_data`, `sis_backend_uploads` |

- **Chưa chạy trên VPS thật**, do chưa có thông tin VPS và chưa được phép deploy production.
- Vì vậy **không tuyên bố deploy production thành công**. Cần người dùng thực hiện mục 5–7
  và chạy checklist trên.
- Lưu ý: ngay khi `deploy.yml` được merge vào `main` và CI xanh, CD sẽ tự chạy.
  Hãy tạo GitHub Secrets và `/opt/sis/.env` **trước** khi merge, nếu không job deploy sẽ fail.

---

## 11. Việc người dùng cần làm

1. Tạo/chuẩn bị Ubuntu VPS và chạy mục 5.1, 5.2.
2. Tạo SSH key, thêm public key cho user `deploy`, lưu private key vào `VPS_SSH_PRIVATE_KEY`.
3. Tạo `/opt/sis/.env` từ `deploy/env.production.example` và điền secret thật.
4. Tạo GitHub Secrets ở mục 4 (bao gồm `GHCR_PULL_TOKEN` nếu package private).
5. Tạo GitHub Environment `production` (Settings → Environments) và có thể bật required reviewers.
6. Cấu hình DNS + host Nginx + certbot ở mục 6.
7. Merge workflow vào `main` để kích hoạt CD, hoặc chạy `workflow_dispatch` với tag có sẵn.
8. Chạy checklist mục 10 trên VPS thật.
9. Sau lần deploy đầu, cân nhắc đặt `SPRING_SQL_INIT_MODE=never`.

---

## 12. Ghi chú an toàn

- Không dùng `docker compose down -v`; `deploy.sh` không bao giờ gọi `down`.
- Volume có tên cố định: `sis_mysql_data`, `sis_backend_uploads`.
- MySQL không publish port; frontend chỉ bind loopback.
- Không log secret: workflow không in private key, password hay token.
  Token GHCR được truyền qua stdin của `docker login --password-stdin`.
- Backup trước deploy; rollback dùng tag SHA, không dùng `latest`.
- Không xoá image cũ ngay sau deploy để còn rollback.
- `deploy.sh` chỉ ghi `.current_tag` sau khi health + smoke test thành công.
