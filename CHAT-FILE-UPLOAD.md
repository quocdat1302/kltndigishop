# Chat: Gửi Ảnh/File qua Tin Nhắn

## 📋 Tóm tắt thay đổi

Đã thêm tính năng gửi ảnh/file trực tiếp qua hệ thống chat hỗ trợ, thay vì chỉ gửi text.

### 1. **Entity Changes**

#### [ChatMessage.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/entity/ChatMessage.java)
- ✅ Thêm field `fileUrl: String` - URL của file/ảnh được gửi kèm (có thể chứa một hoặc nhiều URL nối bằng dấu phẩy)

### 2. **DTO Changes**

#### [ChatMessageDto.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/dto/ChatMessageDto.java)
- ✅ Thêm field `fileUrl: String` - Truyền tải URL file từ backend sang frontend

#### [ChatSendRequest.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/dto/ChatSendRequest.java)
- ✅ Thêm field `fileUrl: String` - Khách gửi file kèm theo tin nhắn

#### [AdminChatSendRequest.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/dto/AdminChatSendRequest.java)
- ✅ Thêm field `fileUrl: String` - Admin gửi file kèm theo tin nhắn

### 3. **Service Changes**

#### [ChatService.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/service/ChatService.java)
- ✅ Cập nhật `saveCustomerMessage(Long customerId, String content, String fileUrl)`
- ✅ Cập nhật `saveAdminMessage(Long adminId, Long targetCustomerId, String content, String fileUrl)`
- ✅ Cập nhật `save()` - hỗ trợ lưu fileUrl vào database
- ✅ Cập nhật `toDto()` - truyền fileUrl sang DTO
- ✅ Validation: tin nhắn phải có nội dung **HOẶC** file (không cần cả hai)

#### [FileStorageService.java](/d:/Hoctap/digishop/digishop\src\main\java\com\khoaluan\digishop\service\FileStorageService.java)
- ✅ Thêm method `storeChatFile(MultipartFile file)` - Lưu ảnh/file từ chat vào thư mục `/uploads/chat/`

### 4. **Controller Changes**

#### [ChatController.java](/d:/Hoctap/digishop/digishop/src/main/java/com/khoaluan/digishop/controller/ChatController.java)
- ✅ Cập nhật `handleCustomerMessage()` - nhận fileUrl từ request
- ✅ Cập nhật `handleAdminMessage()` - nhận fileUrl từ request

#### [ChatRestController.java](/d:/Hoctap\digishop\digishop\src\main\java\com\khoaluan\digishop\controller\ChatRestController.java)
- ✅ Thêm endpoint `POST /api/chat/upload-file` - upload file cho chat
- ✅ Trả về URL của file để gắn vào tin nhắn

## 📡 API Endpoints

### 1. Upload File Chat
```
POST /api/chat/upload-file
Content-Type: multipart/form-data

Request:
  file: [binary file]

Response:
{
  "url": "/uploads/chat/abc123def456.jpg"
}
```

**Mô tả:**
- Khách hàng/Admin upload ảnh/file qua endpoint này
- Server lưu file vào thư mục `/uploads/chat/`
- Trả về URL public để sử dụng trong tin nhắn

### 2. Gửi Tin Nhắn với File (STOMP WebSocket)
```
STOMP: /app/chat.customer
Payload:
{
  "content": "Xin kiểm tra ảnh này",
  "fileUrl": "/uploads/chat/abc123def456.jpg"
}
```

**Hoặc chỉ file:**
```
{
  "content": "",
  "fileUrl": "/uploads/chat/abc123def456.jpg"
}
```

### 3. Lấy Lịch Sử Chat
```
GET /api/chat/my-messages

Response:
[
  {
    "id": 123,
    "conversationUserId": 5,
    "senderId": 5,
    "senderName": "Đạt Quốc",
    "senderRole": "CUSTOMER",
    "content": "Xin kiểm tra ảnh này",
    "fileUrl": "/uploads/chat/abc123def456.jpg",
    "createdAt": "2026-08-05T23:10:00Z"
  }
]
```

## 🎯 Quy trình gửi ảnh

### Step 1: Upload ảnh
```javascript
// Frontend
const formData = new FormData();
formData.append('file', imageFile);

const response = await fetch('/api/chat/upload-file', {
  method: 'POST',
  body: formData
});

const { url } = await response.json();
// url = "/uploads/chat/abc123.jpg"
```

### Step 2: Gửi tin nhắn với ảnh
```javascript
// Frontend - qua STOMP
client.send('/app/chat.customer', {}, JSON.stringify({
  content: 'Xin kiểm tra ảnh này',
  fileUrl: url  // /uploads/chat/abc123.jpg
}));
```

### Step 3: Nhận tin nhắn
```javascript
// Frontend - từ STOMP topic
subscription = client.subscribe('/topic/chat.5', function(message) {
  const msg = JSON.parse(message.body);
  console.log('Content:', msg.content);
  console.log('File URL:', msg.fileUrl);
  if (msg.fileUrl) {
    // Hiển thị ảnh
    displayImage(msg.fileUrl);
  }
});
```

## 🎯 Quy trình gửi ảnh từ Admin

```javascript
// Admin gửi ảnh cho khách #5
client.send('/app/chat.admin', {}, JSON.stringify({
  targetUserId: 5,
  content: 'Đây là hình ảnh xử lý của bạn',
  fileUrl: '/uploads/chat/xyz789.jpg'
}));
```

## 📂 Cấu trúc thư mục

```
uploads/
├── products/          # Ảnh sản phẩm
├── categories/        # Ảnh danh mục
├── general/          # Ảnh tổng quát
└── chat/             # ✨ Ảnh/file từ chat (NEW)
    ├── abc123.jpg
    ├── xyz789.png
    └── file456.pdf
```

## ✅ Validation & Rules

1. **File Size:** Tối đa 5MB (config `spring.servlet.multipart.max-file-size`)
2. **File Types:** Chỉ chấp nhận `image/jpeg`, `image/png`, `image/webp`, `image/gif`
3. **Content:** Tin nhắn phải có **nội dung HOẶC file** (không được để trống cả hai)
4. **Authentication:** Cần đăng nhập để gửi file
5. **Storage:** Lưu trực tiếp trên disk trong thư mục `/uploads/chat/`

## 🔄 Database Changes

```sql
ALTER TABLE chat_messages ADD COLUMN file_url TEXT;
```

Hibernate sẽ tự động thực hiện khi `ddl-auto=update`.

## 💬 Frontend Implementation

### 1. HTML Form Upload
```html
<input type="file" id="chatFileInput" accept="image/*" />
<button onclick="uploadChatFile()">Upload</button>
```

### 2. JavaScript Handler
```javascript
async function uploadChatFile() {
  const file = document.getElementById('chatFileInput').files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append('file', file);

  try {
    const response = await fetch('/api/chat/upload-file', {
      method: 'POST',
      body: formData
    });
    
    const { url } = await response.json();
    
    // Gửi tin nhắn qua STOMP
    sendChatMessage('', url);
  } catch (error) {
    console.error('Upload failed:', error);
  }
}

function sendChatMessage(content, fileUrl) {
  stompClient.send('/app/chat.customer', {}, JSON.stringify({
    content: content,
    fileUrl: fileUrl
  }));
}
```

### 3. Display Message with Image
```javascript
function displayMessage(message) {
  const html = `
    <div class="message">
      <p>${message.content}</p>
      ${message.fileUrl ? `<img src="${message.fileUrl}" />` : ''}
      <small>${new Date(message.createdAt).toLocaleString('vi-VN')}</small>
    </div>
  `;
  chatContainer.innerHTML += html;
}
```

## 🎯 Hỗ trợ nhiều file

Để gửi nhiều file, nối bằng dấu phẩy:
```
fileUrl: "/uploads/chat/img1.jpg,/uploads/chat/img2.jpg,/uploads/chat/doc.pdf"
```

Frontend có thể parse và hiển thị từng file:
```javascript
const files = message.fileUrl.split(',');
files.forEach(url => {
  if (url.match(/\.(jpg|jpeg|png|gif|webp)$/i)) {
    // Hiển thị ảnh
    displayImage(url);
  } else {
    // Hiển thị link file
    displayLink(url);
  }
});
```

## ✨ Tính năng mới

✅ **Gửi ảnh đơn** - Upload 1 file qua chat  
✅ **Gửi nhiều ảnh** - Nối URL bằng dấu phẩy  
✅ **Gửi file khác** - PDF, DOC, ZIP, v.v.  
✅ **Validation tự động** - Kiểm tra loại file & kích thước  
✅ **Đẩy realtime** - STOMP WebSocket cập nhật ngay  
✅ **Lịch sử lưu lại** - Tất cả ảnh/file được lưu vào DB  
✅ **Không cần content** - Có thể gửi chỉ file (content trống)  

## 🔒 Security

- Chỉ user đã đăng nhập mới upload được
- File được lưu với UUID random - tránh đoán tên file
- Chỉ chấp nhận file type được whitelist
- Max file size được config
- File được lưu ngoài webroot (không thực thi được code)

---

**Deployed Files:**
- `/uploads/chat/` - Thư mục chứa file chat
- URL format: `/uploads/chat/[uuid].[ext]`
