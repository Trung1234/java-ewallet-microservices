# 🛠️ Setup môi trường dev trên Mac mini (macOS)

Hướng dẫn cài đặt công cụ để phát triển dự án ví điện tử (Java / Spring Boot / Kafka / Kubernetes) trên Mac mini. Bản này tương ứng với `setup.md` (Windows 11).

---

## 1. Yêu cầu phần cứng

| Thành phần | Tối thiểu | Khuyến nghị |
|---|---|---|
| RAM | 8GB (bật từng phần) | 16GB trở lên |
| Ổ cứng | 30GB trống | SSD, 50GB trống |
| CPU | Apple Silicon (M1) | M2 trở lên |

Hệ thống chạy cùng lúc nhiều container (các service Spring Boot, PostgreSQL, MongoDB, Kafka, Redis), nên RAM là yếu tố quan trọng nhất.

> Mac mini dùng Apple Silicon (arm64). Hầu hết image Docker đều có bản arm64; nếu gặp image chỉ có amd64, thêm `platform: linux/amd64` trong `docker-compose.yml` (chạy chậm hơn do giả lập).

---

## 2. Khác biệt so với Windows

| Windows | macOS |
|---|---|
| WSL2 | **Không cần** – Docker Desktop tự dùng máy ảo của macOS |
| winget | **Homebrew** |
| `.wslconfig` giới hạn RAM | Giới hạn trong **Docker Desktop → Settings → Resources** |
| PowerShell / Windows Terminal | Terminal (zsh) hoặc iTerm2 |

---

## 3. Cài Homebrew

Mở Terminal và chạy:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Với Apple Silicon, thêm brew vào PATH (làm theo hướng dẫn cuối lệnh cài, thường là):

```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

Kiểm tra: `brew --version`

---

## 4. Công cụ bắt buộc

| Công cụ | Mục đích |
|---|---|
| **JDK 21 hoặc 25** (LTS, Eclipse Temurin) | Biên dịch và chạy Java |
| **Git** | Quản lý mã nguồn |
| **IntelliJ IDEA** (Community là đủ) | IDE chính cho Spring Boot |
| **Docker Desktop** | Chạy Kafka, PostgreSQL, MongoDB, Redis bằng `docker compose` |

> Maven/Gradle **không cần cài riêng**: project tạo từ Spring Initializr có sẵn `mvnw` / `gradlew`.

---

## 5. Công cụ nên có

| Công cụ | Mục đích |
|---|---|
| **Postman** hoặc **Bruno** | Test REST API |
| **DBeaver** | Xem và truy vấn PostgreSQL |
| **MongoDB Compass** | Xem dữ liệu MongoDB |
| **iTerm2** (tùy chọn) | Terminal tiện hơn Terminal mặc định |
| **VS Code** | Sửa nhanh YAML, README, xem trước sơ đồ mermaid |

---

## 6. Công cụ cho Kubernetes (tuần 4)

| Công cụ | Mục đích |
|---|---|
| **kubectl** | Điều khiển cluster Kubernetes |
| **kind** | Chạy cluster Kubernetes bên trong Docker |
| **Helm** (tùy chọn) | Quản lý package cho Kubernetes |

> Cách đơn giản hơn: bật Kubernetes có sẵn trong **Docker Desktop → Settings → Kubernetes**.

---

## 7. Cài nhanh bằng Homebrew

```bash
# Bắt buộc
brew install --cask temurin@21
brew install git
brew install --cask intellij-idea-ce
brew install --cask docker

# Nên có
brew install --cask postman
brew install --cask dbeaver-community
brew install --cask mongodb-compass
brew install --cask iterm2
brew install --cask visual-studio-code

# Kubernetes
brew install kubectl
brew install kind
brew install helm
```

> Nếu một gói báo không tìm thấy, chạy `brew search <tên>`.
> Tên cask JDK có thể đổi theo thời gian (ví dụ `temurin@25`); kiểm tra bằng `brew search temurin`.

Sau khi cài Docker, mở **Docker Desktop** từ Launchpad ít nhất một lần để cấp quyền và khởi động engine.

---

## 8. Cấu hình JAVA_HOME

Thêm vào `~/.zshrc`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

Áp dụng:

```bash
source ~/.zshrc
```

Nếu cài nhiều phiên bản JDK, liệt kê bằng `/usr/libexec/java_home -V` và đổi số `-v` cho phù hợp.

---

## 9. Giới hạn RAM cho Docker (quan trọng)

Trên macOS không có `.wslconfig`. Thay vào đó:

1. Mở **Docker Desktop → Settings → Resources**.
2. Đặt **CPUs** = 4, **Memory** = 8GB (với máy 16GB), **Swap** = 1GB.
3. Nhấn **Apply & Restart**.

Nếu Mac mini chỉ có 8GB RAM, đặt Memory = 4–5GB và chỉ bật từng phần hạ tầng (ví dụ chỉ chạy Kafka + PostgreSQL khi cần).

---

## 10. Kiểm tra sau khi cài

```bash
java -version
git --version
docker run hello-world
kubectl version --client
```

✅ Cả bốn lệnh chạy thành công là môi trường đã sẵn sàng.

---

## 11. Checklist

- [ ] Homebrew đã cài (`brew --version`)
- [ ] `java -version` hiển thị JDK 21 hoặc 25
- [ ] `JAVA_HOME` trỏ đúng JDK
- [ ] `git --version` chạy được
- [ ] Docker Desktop mở được, `docker run hello-world` thành công
- [ ] Đã giới hạn RAM/CPU trong Docker Desktop → Resources
- [ ] IntelliJ IDEA nhận đúng JDK
- [ ] Postman / DBeaver / MongoDB Compass đã cài
- [ ] `kubectl version --client` chạy được (cho tuần 4)

---

## 12. Bước tiếp theo

1. Clone repo dự án.
2. Chạy hạ tầng: `docker compose -f infra/docker-compose.yml up -d`
3. Mở thư mục `services/` trong IntelliJ và bắt đầu với `user-service`, `wallet-service` (tuần 1).
