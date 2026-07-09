# 📱 SMS → Notion (개인용)

갤럭시 안드로이드의 SMS(문자)를 **Notion 데이터베이스로 자동 전송**하는 개인용 앱입니다.
선택적으로 **Gemma 3n E4B 온디바이스 AI**(또는 Gemini API 클라우드)를 사용해 카테고리 자동 분류 + 주요정보 추출까지 해줍니다.

> 개인 사이드로드 전용 — Play Store 배포 목적 아님. 본인 기기에만 설치해 사용하세요.

---

## ✨ 기능

- ✅ 기존 문자 전체 일괄 동기화 (최초 1회)
- ✅ 새로 수신되는 문자 실시간 자동 전송 (BroadcastReceiver)
- ✅ 전송 실패 시 자동 재시도 (WorkManager 지수 백오프, 부팅 후 복원)
- ✅ **Gemma 3n E4B 온디바이스** LLM (MediaPipe GenAI, 무료/오프라인/프라이빗)
- ✅ 또는 **Gemini/Gemma 클라우드** API (Google AI Studio 무료 쿼터)
- ✅ 자동 카테고리 분류: 스팸 / 인증번호 / 택배 / 금융 / 개인 / 업무 / 공공 / 기타
- ✅ 주요정보 추출: 금액, 날짜, 장소, 인증코드, 송장번호, URL, 요약
- ✅ 로컬 Room DB로 중복 방지 및 재시도 큐 관리
- ⏳ (예정) Vito 공유 시트 연동, 갤럭시 통화 녹음 STT

---

## 🗂 Notion 데이터베이스 속성

다음 속성을 가진 데이터베이스를 미리 만들어주세요:

| 속성명 | 타입 | 필수 |
|---|---|---|
| 제목 | Title | ✅ |
| 날짜 | Date | ✅ |
| 발신/수신 | Select (옵션: 받은 문자 / 보낸 문자 / 기타) | ✅ |
| 번호 | Phone | ✅ |
| 내용 | Text | ✅ |
| 종류 | Select (옵션: SMS / MMS / CALL_MEMO / VITO) | ✅ |
| 카테고리 | Select (옵션: 스팸 / 인증번호 / 택배 / 금융 / 개인 / 업무 / 공공/행정 / 기타) | AI 사용 시 |
| 주요정보 | Text | AI 사용 시 |

> 💡 Select 옵션은 앱이 새 값을 전송할 때 Notion이 자동으로 추가해주니 미리 안 만들어도 됩니다.

---

## 🚀 설치 (APK 다운로드 방식)

### 0. GitHub Actions 활성화 (최초 1회, 저장소 소유자가 수동)
GitHub App의 보안 정책으로 인해 `.github/workflows/*.yml` 파일은 웹에서 직접 추가해야 합니다.

1. GitHub 저장소 페이지 → **Add file → Create new file**
2. 파일명: `.github/workflows/build-apk.yml`
3. 내용: 이 저장소의 [`scripts/build-apk.yml.template`](scripts/build-apk.yml.template) 전체 복사/붙여넣기
4. Commit new file → 자동으로 Actions가 실행됩니다

### 1. GitHub Actions에서 APK 다운로드
1. 저장소 → **Actions** 탭 → 가장 최근 **Build APK** 워크플로우 클릭
2. 하단 **Artifacts** → `SmsToNotion-debug` 다운로드 (권장)
3. 압축 해제 → `app-debug.apk` 파일을 핸드폰으로 전송

### 2. 핸드폰에 설치
1. 파일 매니저에서 APK 탭 → "출처를 알 수 없는 앱 허용" 
2. 설치 완료

### 3. Notion Integration 준비
1. https://www.notion.so/my-integrations → **+ 새 통합** → 이름: `SMS Import` → 제출
2. **Internal Integration Token** (secret_...) 복사
3. 대상 데이터베이스 페이지 → 우상단 **…** → **Connections** → 위에서 만든 integration 추가
4. 데이터베이스 URL에서 DB ID 복사 (`https://notion.so/xxx?v=yyy` 에서 `xxx` 부분)

### 4. 앱 초기 설정
1. 앱 실행 → **권한 허용하기** (SMS 읽기/수신, 연락처, 알림)
2. 우상단 ⚙️ **설정** 진입
   - Notion Integration Token 입력
   - Database ID 입력
   - AI 처리 모드 선택
3. **저장**

### 5. (선택) Gemma 온디바이스 모델 설정
이미 **Google AI Edge Gallery** 앱으로 `gemma-3n-E4B-it-int4.task` 모델을 다운로드 받으셨다면:
1. 파일 관리자 앱(삼성 내 파일 등)에서 해당 `.task` 파일을 찾음
2. 절대경로 확인 (예: `/storage/emulated/0/Download/gemma-3n-E4B-it-int4.task`)
3. 앱 **설정 → .task 모델 파일 경로** 에 붙여넣기

> Edge Gallery 앱은 모델을 앱 내부 저장소에 두는 경우가 있어, **파일을 `Download` 폴더 등 공용 위치로 복사한 뒤 경로 입력**을 권장합니다.
> 모델 크기가 2~4GB이며, 첫 추론 시 초기 로드에 10~20초 소요될 수 있습니다.

### 6. (선택) Gemini 클라우드 모드
1. https://aistudio.google.com/apikey → API Key 생성
2. 앱 설정 → **Gemini API Key** 입력
3. 모델명: `gemma-3-27b-it` (권장), `gemini-2.5-flash` 등

### 7. 실행
- **전체 문자 동기화 시작**: 기존 모든 문자를 Notion으로 전송 (시간 소요)
- 이후 들어오는 새 문자는 **자동** 전송됨
- 실패한 항목은 **재시도** 버튼 또는 재부팅 시 자동 재시도됨
- **배터리 최적화 예외 설정** 버튼을 눌러 본 앱을 제외하면 백그라운드 수신이 더 안정적

---

## 🏗 직접 빌드하기 (선택)

```bash
git clone https://github.com/dbenergy21/smspasing.git
cd smspasing
./gradlew :app:assembleDebug
# 결과: app/build/outputs/apk/debug/app-debug.apk
```

요구사항: JDK 17, Android SDK 34.

---

## ⚠️ 주의사항

- **개인용**: 본인 기기에만 설치. 타인의 SMS를 수집하는 것은 불법입니다.
- **Notion API 제한**: 초당 평균 3 req. 수만 건 이상이면 몇 시간 걸릴 수 있습니다 (앱이 자동 백오프).
- **배터리**: 실시간 수신을 위해 배터리 최적화 예외 설정 권장.
- **권한**: 기본 SMS 앱이 아니어도 **읽기/수신**은 가능 (앱이 문자를 삭제/변조하지 않음).
- **프라이버시**: 모든 데이터는 당신의 Notion 워크스페이스로만 전송됩니다. 온디바이스 AI 모드에서는 AI 처리도 로컬에서만 이뤄집니다.

---

## 📁 프로젝트 구조

```
app/src/main/java/com/sms2notion/app/
├── App.kt                          # Application (의존성 수동 DI)
├── ui/
│   ├── MainActivity.kt
│   ├── main/MainScreen.kt           # 상태/권한/동기화 버튼/최근 목록
│   └── settings/SettingsScreen.kt   # Notion·Gemma·Gemini 설정
├── data/
│   ├── prefs/SettingsRepository.kt  # DataStore 기반 설정 저장
│   ├── db/                          # Room DB (MessageEntity/Dao)
│   ├── sms/SmsReader.kt             # SMS ContentResolver
│   ├── notion/NotionRepository.kt   # Notion API /v1/pages
│   └── llm/
│       ├── LlmManager.kt            # 통합 관리 (온/오프라인 분기)
│       └── GeminiClient.kt          # Gemini/Gemma 클라우드 호출
├── receiver/
│   ├── SmsReceiver.kt               # SMS_RECEIVED 브로드캐스트
│   └── BootReceiver.kt              # 부팅 후 재시도 복원
└── worker/
    ├── SendMessageWorker.kt         # 단일 메시지 LLM + Notion 전송
    ├── SyncAllWorker.kt             # 전체 동기화 (포그라운드)
    └── RetryPendingWorker.kt        # 실패 항목 재큐잉
```

---

## 🛣 로드맵

- [x] SMS 파싱 + Notion 전송
- [x] Gemma 3n 온디바이스 / Gemini 클라우드 듀얼 모드
- [ ] Vito 공유 인텐트 수신 (Vito 앱의 "공유" 버튼 → 본 앱)
- [ ] 갤럭시 통화 녹음 폴더 모니터링 + STT
- [ ] 전송 실패 로그 전용 화면
- [ ] 키워드/번호 블랙리스트(스팸은 전송 제외)

---

## 📄 라이선스

MIT (개인 학습/사용 목적)
