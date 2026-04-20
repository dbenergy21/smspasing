# SMS → Notion (개인용 Android 앱)

내 갤럭시 스마트폰의 **문자(SMS/MMS)** 를 자동으로 파싱해서 **Notion 데이터베이스**에 저장해주는 개인용 앱.
온디바이스 **Gemma 3n E4B** (또는 클라우드 Gemini API) 로 문자를 자동 분류/정보 추출합니다.

> ⚠️ **개인용 사이드로드 전용** — Play Store 배포 목적이 아님. 본인 기기에만 설치해서 사용.

---

## ✨ 기능

- ✅ **기존 문자 일괄 동기화** — 전화기에 쌓여있던 모든 SMS를 한 번에 Notion으로 보냄
- ✅ **실시간 수신** — 새 SMS가 도착하면 자동으로 Notion에 저장
- ✅ **재시도 큐** — Notion API 실패 / 오프라인 상황 자동 복구 (WorkManager)
- ✅ **중복 방지** — Room DB로 전송 여부 추적
- ✅ **AI 분석 (선택)**:
  - **온디바이스**: AI Edge Gallery에서 다운받은 `gemma-3n-E4B-it-int4.task` 파일을 그대로 재사용
  - **클라우드**: Gemini API 키로 Gemma / Gemini 모델 호출
  - 결과: `카테고리` 속성(자동 분류) + `추출정보` 속성(JSON: 금액/일시/장소/코드/상대방/요약)

---

## 📦 설치 방법 (핸드폰에 APK 설치)

### 1. GitHub Actions에서 APK 다운로드

1. 이 저장소의 **Actions** 탭 → 최신 **"Build APK"** 워크플로우 실행 결과 클릭
2. 하단의 **Artifacts** 에서 `SmsToNotion-release` 다운로드 (또는 debug)
3. 압축 풀면 `app-release.apk` 가 나옴
4. APK를 폰으로 전송 (이메일/텔레그램/카톡나에게/USB 아무거나)
5. 폰에서 APK 탭 → "출처를 알 수 없는 앱 설치" 허용 → 설치

### 2. 권한 허용
앱 실행 → 권한 요청이 뜨면 모두 **허용**:
- SMS 읽기/받기
- 연락처 읽기 (발신자 이름 매칭용)
- 알림 표시 (동기화 진행상황)

### 3. 배터리 최적화 제외 (권장)
**설정 → 앱 → SMS → Notion → 배터리 → 제한 없음**
(안 하면 도즈 모드에서 실시간 수신이 지연될 수 있음)

---

## 🗒️ Notion 설정

### 1. Integration 생성 (이미 만드셨다고 하셨으니 스킵 가능)
1. https://www.notion.so/my-integrations → **"+ New integration"**
2. 유형: **Internal** / 워크스페이스 선택
3. 생성 후 **"Internal Integration Token"** 복사 (secret_ 로 시작)

### 2. Notion 데이터베이스 만들기

새 페이지를 만들고 `/database - full page` 로 DB 생성 후, 다음 속성을 추가:

| 속성 이름 | 타입 | 비고 |
|---|---|---|
| **제목** | Title | 기본 (이름 변경해서 한글 "제목"으로) |
| **날짜** | Date | 문자 수신 시각 |
| **발신/수신** | Select | 옵션: 수신 / 발신 / 기타 |
| **번호** | Phone | 전화번호 |
| **내용** | Rich Text | 문자 본문 |
| **종류** | Select | 옵션: SMS / MMS / 통화기록 / VITO |
| **카테고리** | Select | 옵션: 인증번호, 금융, 택배, 광고/스팸, 예약/일정, 고지서, 공공/행정, 업무, 개인, 기타 (LLM이 자동 채움) |
| **추출정보** | Rich Text | LLM이 추출한 JSON (금액/일시/장소 등) |

> 💡 **속성 이름은 정확히 일치해야 합니다** (대소문자, 공백 포함). Select 옵션들은 미리 안 만들어도 자동 생성됩니다.

### 3. Integration을 DB에 공유
DB 우상단 **"..."** → **"Add connections"** → 방금 만든 Integration 선택

### 4. Database ID 복사
DB 페이지 URL 맨 끝 32자리가 ID:
```
https://www.notion.so/your-workspace/<여기가_DB_ID_32자리>?v=...
```

---

## 🤖 Gemma 3n E4B (온디바이스) 설정

1. 구글 AI Edge Gallery에서 이미 다운로드하신 모델을 그대로 사용합니다.
2. 앱 설정 화면 → **AI 분석 → 온디바이스** 선택
3. **"AI Edge Gallery 기본 폴더 사용"** 버튼을 탭하면 자동 탐색
4. 찾지 못하면 직접 경로 입력. 예:
   - `/storage/emulated/0/Download/gemma-3n-E4B-it-int4.task`
   - `/storage/emulated/0/Android/data/com.google.aiedge.gallery/files/models/gemma-3n-E4B-it-int4.task`
5. 파일을 접근 가능한 `/Download/` 폴더로 복사해두면 가장 안전합니다.

### 클라우드 모드 (대안)
- Google AI Studio (https://aistudio.google.com) → API Key 발급 (무료)
- 앱 설정 → **AI 분석 → 클라우드** → API Key 입력
- 기본 모델: `gemma-3-27b-it` (다른 모델명도 가능: `gemini-2.5-flash` 등)

---

## 🛠️ 개발자 빌드

```bash
# JDK 17 + Android SDK 필요
./gradlew :app:assembleDebug
# => app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions 에서 자동 빌드됩니다 (`.github/workflows/build-apk.yml`).

---

## 🗺️ 로드맵

- [x] **1단계**: SMS 파싱 → Notion 전송 + Gemma 분석 ← **현재**
- [ ] **2단계**: Vito 앱 공유 시트 수신 (Vito에서 "공유 → SMS2Notion" 탭하면 통화 STT 결과가 Notion으로)
- [ ] **3단계**: 갤럭시 통화 녹음 파일 자동 감시 + 자체 STT → Notion

---

## ❓ FAQ

**Q. 왜 `release` APK도 debug 키로 서명되나요?**
개인용 사이드로드 용도이므로 별도의 키스토어 관리 없이 설치 편의를 위해 그렇게 설정했습니다.
배포할 계획이 생기면 `app/build.gradle.kts` 에서 release signingConfig를 활성화하세요.

**Q. 실시간 수신이 잘 안 돼요.**
삼성의 공격적인 배터리 최적화 때문입니다:
1. **설정 → 앱 → SMS → Notion → 배터리 → 제한 없음**
2. **디바이스 케어 → 배터리 → 백그라운드 사용 제한 → 절전 앱 제외**

**Q. 통화 내용은요?**
Galaxy 내장 통화 녹음 텍스트는 시스템 내부 저장소에 암호화되어 보관돼 서드파티 앱이 직접 접근하기 어렵습니다.
2단계에서 Vito 공유 인텐트 수신으로 우회 예정입니다.
