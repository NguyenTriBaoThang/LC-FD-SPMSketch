# LC-FD-SPMSketch

Repository này cung cấp **phiên bản cải tiến của thuật toán FD-SPMSketch** nhằm khai thác các mẫu trình tự thường xuyên trong **cơ sở dữ liệu hoàn toàn động**.  
Giải pháp đề xuất tập trung vào **giảm chi phí bộ nhớ và thời gian thực thi** thông qua **cơ chế lưu trữ trì hoãn (lazy storage)** và **nén dữ liệu vị trí (compressed PosList)**.

---

## 1. Nguồn gốc và kế thừa

Mã nguồn trong repository này **được kế thừa và phát triển từ công trình gốc**:

🔗 **FD-SPMSketch (Original Repository)**  
https://github.com/lilyannlee1/FD-SPMSketch

Thuật toán FD-SPMSketch gốc đề xuất phương pháp xấp xỉ độ hỗ trợ cho bài toán khai thác mẫu trình tự thường xuyên trong cơ sở dữ liệu hoàn toàn động bằng cách sử dụng **MROS sketch**.

Trong nghiên cứu này, chúng tôi **giữ nguyên lõi thuật toán và cơ chế ước lượng độ hỗ trợ**, đồng thời đề xuất các cải tiến về **cấu trúc lưu trữ và quản lý PosMap**.

---

## 2. Bối cảnh bài toán

Bài toán khai thác mẫu trình tự thường xuyên trong cơ sở dữ liệu hoàn toàn động gặp nhiều thách thức:

- Dữ liệu liên tục **được thêm và xóa**
- Số lượng **mẫu trung gian lớn**
- Chi phí **bộ nhớ cao** khi lưu trữ PosList (SID → danh sách vị trí)

Mặc dù FD-SPMSketch đã giảm chi phí tính toán độ hỗ trợ nhờ MROS, nhưng việc **lưu trữ đầy đủ PosMap cho nhiều mẫu trung gian** vẫn gây tiêu tốn bộ nhớ đáng kể.

---

## 3. Đóng góp chính

Phiên bản cải tiến của FD-SPMSketch đề xuất các điểm mới sau:

### 🔹 Lưu trữ PosMap trì hoãn (Lazy PosMap)
- PosList **chỉ được tạo khi thật sự cần thiết**
- Nhiều mẫu trung gian chỉ lưu **thông tin hỗ trợ**
- Giảm số lượng PosList phải lưu trong bộ nhớ

### 🔹 Nén PosList
- Sử dụng **gap encoding kết hợp varint**
- Giảm đáng kể dung lượng lưu trữ danh sách vị trí

### 🔹 Quản lý PosMap tối ưu
- Sử dụng **LazyPosMapManager** để quản lý tập trung
- Hạn chế tạo trùng lặp PosList
- Áp dụng cấu trúc lưu trữ dạng CSR cho các PosMap đang hoạt động

---

## 4. Tổng quan thuật toán

### FD-SPMSketch gốc
- Lưu PosMap đầy đủ cho hầu hết các mẫu
- Chi phí bộ nhớ cao khi ngưỡng hỗ trợ thấp

### FD-SPMSketch cải tiến
- Trì hoãn việc tạo PosList bằng cơ chế lazy
- PosList được lưu dưới dạng nén
- Kết hợp MROS để tỉa sớm các mẫu không tiềm năng

---

## 5. Cấu trúc mã nguồn

```
├── algoFpmMros.java              # Thuật toán khai phá chính (đã chỉnh sửa)
├── VerDB_Mros.java               # Cơ sở dữ liệu dạng dọc
├── Mros.java                     # Cấu trúc MROS
│
├── CsrPosMap.java                # PosMap dạng CSR
├── GapVarintPosList.java         # PosList nén (gap + varint)
├── LazyPosMapManager.java        # Quản lý PosMap trì hoãn
├── PatternEntry.java             # Thông tin pattern
├── LruCache.java                 # Cache PosMap
├── Varint.java                   # Hỗ trợ mã hóa varint
│
├── mainRunMrosFPM.java           # Chương trình chạy thực nghiệm
└── README.md
```

---

## 6. Thiết lập thực nghiệm

- **Tham số**:
  - `minSupRe`: ngưỡng hỗ trợ tương đối
  - `delta`: hệ số động
- **Chỉ số đánh giá**:
  - Bộ nhớ đỉnh (MB)
  - Thời gian thực thi (ms)
  - Số lượng mẫu sinh ra

So sánh giữa:
- FD-SPMSketch gốc
- FD-SPMSketch cải tiến

---

## 7. Kết quả chính

- Giảm **đáng kể chi phí bộ nhớ** trong hầu hết các cấu hình
- Thời gian thực thi được cải thiện hoặc tăng nhẹ tùy tham số
- Hiệu quả nhất với **minSupRe thấp và trung bình**

---

## 8. Cách chạy chương trình

```bash
javac *.java
java mainRunMrosFPM
```

Điều chỉnh tham số trong `mainRunMrosFPM.java` để chạy các kịch bản khác nhau.

---

## 10. Ghi chú

- Mã nguồn dùng cho **mục đích học thuật**
- Bản quyền thuật toán gốc thuộc về các tác giả FD-SPMSketch
