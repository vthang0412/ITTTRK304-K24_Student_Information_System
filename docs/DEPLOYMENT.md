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
| `deploy/backup.sh` | Tạo bộ backup database + uploads, checksum, tùy chọn GPG/offsite. |
| `deploy/rollback.sh` | Rollback image về tag trước nếu schema còn tương thích; không downgrade database. |
| `deploy/restore.sh` | Khôi phục database + uploads đồng bộ (có xác nhận, không tự chạy khi deploy). |
| `deploy/verify-backup.sh` | Kiểm tra backup mà không sửa DB/volume. |
| `deploy/env.production.example` | Mẫu `.env` production (chỉ placeholder, không chứa secret thật). |
| `backend/.../application-dev.properties` | Profile development: Flyway + Hibernate validate, demo seed, Swagger, CORS localhost. |
| `backend/.../application-prod.properties` | Profile production: Hibernate validate, không seed demo, Swagger tắt, Flyway. |
| `backend/.../db/migration/V1__baseline_schema.sql` | Baseline schema cho database production mới. |
| `deploy/nginx/sis.conf` | Mẫu reverse proxy Nginx trên host + hướng dẫn HTTPS. |
| `deploy/nginx/sis-http.conf` | Cấu hình HTTP bootstrap trước khi có chứng chỉ TLS thật. |
| `docs/DEPLOYMENT.md` | Tài liệu này. |
| `.gitattributes` | Ép LF cho script/YAML để chạy đúng trên Ubuntu. |

CI kiểm thử backend/frontend, migrations và startup với MySQL riêng; CI/CD không kết nối Database Production.

---

## 3. Cách CI/CD hoạt động

1. Push/merge vào `main`.
2. Workflow `CI` chạy:
   - Backend: Java 21 + `./gradlew build` với MySQL service chỉ tồn tại trong job.
   - Hai MySQL integration tests: database mới từ V1 đến V15 và fixture legacy baseline 6 lên V15;
     cả hai phải khởi động Spring/Hibernate validate và fixture phải giữ nguyên dữ liệu.
   - Frontend: Node 22 + `npm ci`, `npm run lint`, `npm run build`.
   - Compose và shell-script syntax validation.
3. Khi `CI` kết thúc **thành công** trên `main`, workflow `Deploy to VPS` được kích hoạt bằng `workflow_run`.
4. Job **Build and push**:
   - Checkout đúng commit SHA của lần CI.
   - Build backend và frontend.
   - Gắn tag bất biến `<commit-sha>` (có thể có alias `latest`, nhưng deploy không dùng alias này).
   - Push lên GHCR, dùng cache của GitHub Actions.
5. Job **Deploy to Ubuntu VPS** (chỉ chạy khi CI thành công trên `main`, và cần approval nếu
   GitHub Environment `production` có required reviewers):
   - Upload Compose và các script deploy/backup/restore/verify lên `/opt/sis`.
   - SSH vào VPS (private key trong GitHub Secrets).
   - Nếu có `GHCR_PULL_TOKEN`, đăng nhập GHCR trên VPS trước khi pull.
   - Chạy `deploy.sh`.
6. `deploy.sh`:
   - Backup MySQL + uploads, rồi đọc Flyway version/failure state trước khi đổi image.
   - `docker compose pull backend frontend`.
   - Chỉ recreate backend/frontend (`up -d --no-deps`); không restart MySQL, `down` hay `-v`.
   - Chờ MySQL tối đa 240s, backend 540s, frontend 180s; GitHub deploy job có timeout 40 phút.
   - Smoke test frontend và proxy `/actuator/health` từ trong container.
   - Chỉ ghi `.current_tag` và `.current_schema_version` sau khi health/smoke test thành công; ghi
     `.previous_tag` cùng `.previous_schema_version` để kiểm tra khả năng tương thích rollback.
7. Tự động rollback image chỉ được thử khi backend đã healthy và Flyway state không đổi, không có
   migration lỗi. Nếu backend không healthy, schema tiến lên hoặc có migration lỗi, script **không**
   tự rollback image vì phiên bản cũ có thể không tương thích. Database không bao giờ tự downgrade.
8. Merge vào `develop`: CI chạy, sau đó workflow `Deploy` chỉ **build/push image tag dev** lên GHCR
   (tag commit SHA và `dev`). Không có bước deploy production từ `develop`.

Các workflow pin GitHub Actions bằng commit SHA. Quyền `packages: write` chỉ cấp cho hai job build/push;
job SSH deploy chỉ có `contents: read`. Manual workflow dispatch chạy từ `main` và chỉ chấp nhận
rollback về tag SHA trước đó đã ghi nhận, không deploy một SHA tùy ý.

> `workflow_run` chỉ chạy khi file `deploy.yml` đã có trên nhánh mặc định (`main`).
> Manual `workflow_dispatch` chỉ dùng để rollback về `.previous_tag` đã ghi nhận.

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
| `VPS_KNOWN_HOSTS` | Có | Host key đã xác minh cho VPS (dạng `known_hosts`; với SSH port khác 22 phải đúng `[host]:port`). Không có fallback `ssh-keyscan` trong workflow. |
| `GHCR_PULL_TOKEN` | Không | PAT có quyền `read:packages`, dùng khi package GHCR để private. |
| `GHCR_USER` | Không | Tài khoản sở hữu `GHCR_PULL_TOKEN`. Mặc định dùng `github.repository_owner`. |

Không đưa `.env` production lên GitHub. `.env` chỉ nằm trên VPS.
`GITHUB_TOKEN` do Actions cấp tự động; chỉ job build/push có `packages: write`. Job triển khai
không nhận quyền ghi package.

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

Dùng `ssh-keyscan -p <port> -H <host>` chỉ để thu thập candidate key, rồi đối chiếu fingerprint qua
một kênh tin cậy (ví dụ VPS console/provider) trước khi lưu output vào secret `VPS_KNOWN_HOSTS`.
Workflow từ chối thiếu secret hoặc file host key không hợp lệ và luôn bật `StrictHostKeyChecking=yes`.

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
- `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `JWT_SECRET` phải được cung cấp; không dùng placeholder.
- `JWT_SECRET` phải có ít nhất 32 byte UTF-8. Ứng dụng từ chối giá trị placeholder và fail-fast
  nếu thiếu secret hoặc secret quá ngắn.
- Production Compose cố định `SPRING_PROFILES_ACTIVE=prod`, `SPRING_SQL_INIT_MODE=never` và
  `SWAGGER_ENABLED=false`; các giá trị này không thể bị bật lại từ `.env`.
- Production CORS chỉ đọc `PROD_CORS_ALLOWED_ORIGINS`; cấu hình Dev
  `APP_CORS_ALLOWED_ORIGINS` không được truyền vào backend Production.
  Tài khoản demo `admin/123456` **không bao giờ** được seed vào production.
- Flyway (`SPRING_FLYWAY_ENABLED=true`) tạo schema trên database mới từ `V1` đến `V9`.
  Với database đã có schema nhưng chưa có `flyway_schema_history`, Flyway baseline ở **version 6**
  (V2/V4/V5 không idempotent nên phải bỏ qua), sau đó chạy `V7` để đồng bộ cấu trúc còn thiếu và
  nới precision các cột điểm lên `DECIMAL(5,2)`, `V8` để chuẩn hóa duyệt đăng ký và trạng thái đọc
  thông báo, `V9` để lưu lịch sử lịch học có ngày hiệu lực. **Không hạ
  `spring.flyway.baseline-version` về 1.** Luôn backup và kiểm tra trước khi nâng cấp database cũ;
  Hibernate `validate` sẽ báo lỗi nếu schema vẫn lệch sau migration.
- Production luôn tắt Swagger (`SWAGGER_ENABLED=false`); Compose và profile `prod` cố định trạng thái này.
- `SPRING_SQL_INIT_MODE` được Compose và profile `prod` cố định là `never`; không bật seed demo ở Production.
- Không commit file `.env` này.
- Chỉ các file mẫu `.env.example` được theo dõi trong Git; file môi trường thật phải ở ngoài Git
  và bị loại khỏi Docker build context.
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

Trước khi có chứng chỉ, dùng cấu hình bootstrap HTTP-only (không tạo chứng chỉ tự ký):

```bash
# Từ máy local
scp deploy/nginx/sis-http.conf deploy@YOUR_VPS_HOST:/tmp/sis-http.conf
```

Trên VPS:

```bash
sudo cp /tmp/sis-http.conf /etc/nginx/sites-available/sis
sudo sed -i 's/sis.example.com/your-real-domain/g' /etc/nginx/sites-available/sis
sudo ln -sf /etc/nginx/sites-available/sis /etc/nginx/sites-enabled/sis
sudo mkdir -p /var/www/html/.well-known/acme-challenge
sudo nginx -t
sudo systemctl reload nginx
sudo certbot certonly --webroot -w /var/www/html -d your-real-domain
```

Chỉ sau khi Certbot xác nhận chứng chỉ hợp lệ, cài cấu hình HTTPS cuối cùng:

```bash
scp deploy/nginx/sis.conf deploy@YOUR_VPS_HOST:/tmp/sis-https.conf
# Run on the VPS:
sudo cp /tmp/sis-https.conf /etc/nginx/sites-available/sis
sudo sed -i 's/sis.example.com/your-real-domain/g' /etc/nginx/sites-available/sis
sudo nginx -t
sudo systemctl reload nginx
sudo certbot renew --dry-run
```

`sis.conf` yêu cầu file chứng chỉ thực tại `/etc/letsencrypt/live/<domain>/`; không cài/reload
cấu hình này trước khi chứng chỉ đã được cấp. Cấu hình cuối giữ ACME challenge trên HTTP và redirect
mọi request còn lại sang HTTPS. HSTS bật `includeSubDomains`; chỉ dùng khi tất cả subdomain đều luôn
có HTTPS. CSP cho phép Google Fonts mà frontend hiện import, dùng hash cho script khởi tạo theme, và
cho phép inline styles React hiện tại. Nếu thay nội dung script khởi tạo theme trong `frontend/index.html`,
cần cập nhật hash CSP trong hai Nginx config và kiểm tra trên browser. Trang login/forgot/reset password
bị rate-limit theo IP; monitor 429 sau khi bật.
Firewall VPS chỉ mở 80/443 và SSH; cổng frontend vẫn bind loopback, MySQL không publish port.

Giới hạn upload đồng bộ: avatar tối đa 5 MB; Spring multipart request và cả hai Nginx layer cho phép
6 MB để dành chỗ cho multipart overhead.

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

### 7.2. Rollback thủ công về bản ngay trước đó

**Actions → Deploy to VPS → Run workflow**, chọn nhánh `main` và nhập `image_tag` là commit SHA
được ghi trong `.previous_tag`. Workflow này không build image; remote script xác nhận schema đang
ở đúng version mà tag đích từng chạy cùng và tạo backup hợp lệ trước khi đổi image.

Để triển khai phiên bản mới, không dùng workflow_dispatch; merge/push lên `main` để CI/CD build image
và tự deploy SHA của commit vừa kiểm thử.

Hoặc trực tiếp trên VPS:

```bash
cd /opt/sis
chmod +x deploy.sh backup.sh restore.sh verify-backup.sh rollback.sh
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
cat .current_tag .previous_tag .current_schema_version .previous_schema_version 2>/dev/null
```

Compose project Dev là `sis-dev` (container `sis-dev-*`), Production là `sis-prod`
(container `sis-*`). Development dùng volume riêng `sis-dev-isolated-mysql-data`;
Production tiếp tục dùng volume hiện hữu `sis_mysql_data`. Không dùng
`docker compose down -v` và không đổi tên/xóa volume Production.
MySQL development mặc định publish tại `127.0.0.1:3307`, chỉ bind loopback.
Để chạy stack Dev, tạo `.env.isolated` từ `.env.example`, đặt credential local riêng,
sau đó chạy `docker compose --env-file .env.isolated up --build -d`. Lệnh này chỉ được
chạy sau khi xác nhận container Dev không còn dùng volume Production.

---

## 8. Rollback

Rollback chỉ đổi image tag, **không xoá volume và không downgrade database**. Trước khi deploy, script
ghi version Flyway hiện tại; sau thành công, lưu version schema tương thích với từng tag hiện tại/trước.
Rollback chỉ được phép đến `.previous_tag` khi `.previous_schema_version` khớp trạng thái Flyway hiện
tại và tạo được backup DB/uploads hợp lệ. Nếu version khác hoặc có migration lỗi, cần đánh giá thủ công;
không chạy image cũ trên schema mới chỉ vì container health thất bại.

Automatic rollback chỉ thử khi backend đã healthy và trạng thái Flyway không thay đổi trong lần deploy.
Backend không healthy, migration thất bại/đã tiến version, hoặc không đọc được history đều dẫn đến
rollback bị chặn để tránh lệch schema-app. Không có lệnh Flyway downgrade tự động.

```bash
cd /opt/sis

# Quay lại tag ngay trước đó (được ghi tự động sau mỗi deploy thành công)
./rollback.sh

# Chỉ tag ngay trước đó mới có metadata schema để rollback an toàn
./rollback.sh <commit-sha>
```

Nếu thiếu metadata `.previous_schema_version`, version database không khớp, hoặc backup thất bại,
rollback bị từ chối. Không chạy downgrade Flyway tự động. Với schema đã tiến lên, giữ backend hiện tại
hoặc triển khai bản sửa tương thích; chỉ khôi phục DB từ bộ backup đã xác minh theo quy trình mục 9
sau khi có kế hoạch khôi phục và chấp thuận downtime.

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

Mỗi lần tạo một bộ cùng timestamp UTC `sis_<timestamp>` gồm:

- `sis_<timestamp>.sql.gz`: dump MySQL với `--single-transaction`, `--quick`, `--hex-blob`,
  routines, triggers và **events**. `--single-transaction` nhất quán cho InnoDB; tables MyISAM
  (nếu có) vẫn có thể thay đổi trong lúc dump.
- `sis_<timestamp>.uploads.tar.gz`: snapshot toàn volume `sis_backend_uploads`, gồm ảnh đại diện
  và các uploads đang lưu tại `/app/uploads`.
- `sis_<timestamp>.sha256`: checksum hai archive.

Script từ chối nếu MySQL/volume không tồn tại; kiểm tra archive không rỗng, `gzip -t`, marker
`-- Dump completed`, danh sách tar và checksum trước khi công bố manifest cuối. Không tự xóa backup
cũ. Khi khai báo `BACKUP_GPG_RECIPIENT` (GPG public-key recipient), archive được mã hóa thành
`.gpg` trước khi lưu; private key chỉ cần trên máy dùng để giải mã/restore. Có thể đặt
`RCLONE_DEST=remote:bucket/sis` để upload và `rclone check --download` kiểm tra bộ backup; nên dùng
remote `rclone crypt` hoặc GPG để mã hóa dữ liệu offsite. Có thể đặt hai biến trong file
`/opt/sis/.backup.env` (shell-compatible, quyền `600`, không commit); backup script tự đọc file này.
Lỗi upload/verify offsite làm lệnh backup thoát lỗi (bản local đã xác thực vẫn được giữ). Bảo vệ
`/opt/sis/backups` quyền `700`, files `600`.

Kiểm tra một bộ mà không sửa DB/volume:

```bash
./verify-backup.sh backups/sis_YYYYMMDDTHHMMSSZ.sql.gz
# Với bộ mã hóa GPG, cần private key khả dụng:
./verify-backup.sh backups/sis_YYYYMMDDTHHMMSSZ.sql.gz.gpg
```

Giữ bản offsite và thực hiện restore drill định kỳ trên một VPS/database/volume thử nghiệm riêng;
không kiểm tra bằng cách restore lên Production.

### 9.2. Khôi phục

Khôi phục phải dùng đúng cặp DB/uploads từ cùng timestamp; script xác thực manifest, gzip, marker dump
và tar trước khi ghi dữ liệu. Nó yêu cầu MySQL cùng uploads volume đang tồn tại, đối chiếu DB name,
tạo backup an toàn cả DB + uploads trước, rồi mới hỏi xác nhận, dừng frontend/backend và phục hồi.
Restore ghi đè database và thay toàn bộ nội dung volume uploads. Chạy trong maintenance window:

```bash
cd /opt/sis
./restore.sh backups/sis_YYYYMMDDTHHMMSSZ.sql.gz
# Bộ đã mã hóa: private key GPG phải khả dụng.
./restore.sh backups/sis_YYYYMMDDTHHMMSSZ.sql.gz.gpg
```

Không dùng `FORCE=1` khi thao tác thủ công. Nếu restore DB hoặc uploads thất bại, ứng dụng được để dừng
để tránh ghi lệch; khôi phục từ bộ pre-restore mà script vừa tạo sau khi điều tra. Script này **không**
được gọi tự động bởi deploy/rollback/workflow. Không có lệnh xóa volume.

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

### 9.3. Lịch backup tự động

Có thể lên lịch sau khi kiểm thử thủ công, cấu hình quyền thư mục, khóa GPG/offsite và cảnh báo khi job
fail. Cron chạy hằng ngày lúc 02:00:

```cron
0 2 * * * cd /opt/sis && ./backup.sh >> /var/log/sis-backup.log 2>&1
```

Khuyến nghị systemd timer/service để có logging và trạng thái dễ giám sát. Bảo đảm systemd truyền
`BACKUP_GPG_RECIPIENT`/`RCLONE_DEST` nếu dùng; không đặt private key hoặc remote credentials trong Git.

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
# Optional, mode 600 and owned by deploy:
EnvironmentFile=-/opt/sis/.backup.env
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

Không có retention tự động trong script để tránh xóa bản cũ khi bộ mới chưa được xác minh. Thiết lập
retention riêng sau khi xác nhận bản local và offsite; luôn giữ ít nhất một bản đã restore-drill.

### 9.4. Backup offsite

Sao chép backup ra ngoài VPS sau mỗi lần chạy, ví dụ:

```bash
RCLONE_DEST=remote-crypt:sis-backups ./backup.sh
```

`remote-crypt` phải là remote `rclone crypt` đã cấu hình khóa ngoài repository. Hoặc đặt
`BACKUP_GPG_RECIPIENT` để script mã hóa local trước khi upload. Kiểm tra định kỳ file trên remote bằng
`rclone check --download` (được script chạy khi `RCLONE_DEST` đặt) và thử restore từ bản offsite.
Không lưu rclone credentials hoặc GPG private key trong repository; private key restore cần được bảo
quản riêng và chỉ cài vào máy restore khi cần.

Các bản `.sql.gz` cũ không có cặp uploads và manifest nên `restore.sh` mới sẽ từ chối chúng. Giữ
nguyên các bản cũ; nếu cần phục hồi chúng, phải theo quy trình DB-only đã xác minh riêng và chấp nhận
rằng avatars/uploads không được phục hồi từ các file đó.

---

## 10. Kiểm thử trên VPS

Checklist bắt buộc trước khi coi là deploy thành công:

| # | Kiểm tra | Lệnh / cách kiểm tra | Kết quả mong đợi |
| --- | --- | --- | --- |
| 1 | CI xanh | GitHub Actions → CI | Backend/unit + MySQL migration integration + frontend lint/build success |
| 2 | Build và push GHCR | GitHub Actions → Deploy → Build and push | Có image tag commit SHA; deploy không phụ thuộc `latest` |
| 3 | SSH connection | Workflow bước Configure SSH / Upload | Không lỗi |
| 4 | Pull image trên VPS | `docker compose ... pull` trong log deploy | Pull thành công |
| 5 | Compose deployment | `docker compose ... ps` | 3 container `Up (healthy)` |
| 6 | MySQL persistence | `docker volume ls \| grep sis_mysql_data` | Volume tồn tại |
| 7 | Backend healthcheck | `docker inspect -f '{{.State.Health.Status}}' sis-backend` | `healthy` |
| 8 | Frontend truy cập | `curl -I http://127.0.0.1:8080/` | HTTP 200 |
| 9 | Swagger bị tắt ở Production | `curl -I https://domain/swagger-ui.html` | `404` |
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
| Shell syntax | `bash -n deploy/deploy.sh deploy/backup.sh deploy/rollback.sh deploy/restore.sh deploy/verify-backup.sh` | OK |
| Compose production | `IMAGE_TAG=test-sha docker compose -f deploy/docker-compose.prod.yml --env-file deploy/env.production.example config --quiet` | OK |
| Port/volume resolve | `docker compose ... config` | Prod giữ volume `sis_mysql_data`; Dev dùng volume riêng `sis-dev-isolated-mysql-data` |
| Flyway MySQL integration | `./gradlew build` với MySQL tạm riêng | Fresh V1–V15 + baseline-6 legacy V7–V15; backend/Hibernate validate khởi động, dữ liệu fixture được giữ |
| Deploy/rollback guard | Docker mock + Flyway state tổng hợp | Deploy SHA lưu metadata; tự rollback bị chặn khi schema tiến; rollback thủ công bị chặn khi schema version khác |

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
7. Merge workflow vào `main` để kích hoạt CD; chỉ dùng `workflow_dispatch` để rollback về SHA ngay trước đó.
8. Chạy checklist mục 10 trên VPS thật.
9. Production luôn cố định `SPRING_SQL_INIT_MODE=never`; Compose không cho phép bật seed qua `.env`.

---

## 12. Ghi chú an toàn

- Không dùng `docker compose down -v`; `deploy.sh` không bao giờ gọi `down`.
- Volume Production có tên cố định: `sis_mysql_data`, `sis_backend_uploads`; Dev dùng
  `sis-dev-isolated-mysql-data`.
- MySQL không publish port; frontend chỉ bind loopback.
- Không log secret: workflow không in private key, password hay token.
  Token GHCR được truyền qua stdin của `docker login --password-stdin`.
- Backup trước deploy; rollback dùng tag SHA, không dùng `latest`.
- Không xoá image cũ ngay sau deploy để còn rollback.
- `deploy.sh` chỉ ghi `.current_tag` sau khi health + smoke test thành công.
- Nếu `.previous_schema_version` không khớp Flyway version hiện tại, không rollback image cũ và không tự
  restore DB; cần đánh giá tương thích hoặc thực hiện quy trình restore đã được duyệt.
