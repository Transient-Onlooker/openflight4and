# OpenFlight4And

> 비행 시간을 집중 타이머처럼 사용하는 Android 집중 앱

비행 시간을 집중 타이머로 활용한다는 아이디어에서 영감을 받아, 부족하다고 느낀 강제성과 Android 네이티브 경험에 집중해 독자적으로 개발한 안드로이드 앱입니다.

## 소개 ✈️

OpenFlight4And는 출발지와 목적지를 정하고, 실제 비행 시간에 맞춰 집중 세션을 진행하는 앱입니다.  
비행 중에는 지도 추적, 비행권, 출석 체크, 인플라이트 설정, 집중 잠금 같은 흐름을 통해 단순 타이머보다 더 강한 몰입 경험을 목표로 합니다.

## 주요 기능 🧭

- 공항 선택과 대권 거리 기반 예상 비행 시간 계산
- 2D / 2.5D / 3D 지도 전환
- 좌석 선택, 보딩패스, 비행 시작 흐름
- 비행권 시스템
- 출석 체크, 광고 보상, 리딤 코드
- 비행 기록과 통계 화면
- 가로모드 지원
- 비행 중 설정 진입
- 집중 잠금 on/off
  - 사용정보 접근
  - 다른 앱 위에 표시

## 차별점 🛫

유사 가능성을 낮추기 위해, 앱 구조와 사용자 경험을 아래처럼 분명히 다르게 가져가고 있습니다.

- 비행 시간을 집중 타이머로 활용하는 방향은 같더라도 구현은 독자적으로 설계했습니다.
- 지도 UI와 지도 스타일이 다릅니다.
- 비행권 시스템과 광고 보상 흐름이 들어가 있습니다.
- Desired Focus Time 같은 별도 목표 시간 UI가 없습니다.
- 홈 메뉴 구조가 다릅니다.
- 프로필 기능이 없고, `History`와 `Statistics` 중심으로 단순화했습니다.
- 언어 변경, 진동 같은 범용 옵션 대신 실용적인 설정 위주로 구성했습니다.
- 공항 선택 UI가 다릅니다.
- 좌석 모양과 좌석 선택 흐름이 다릅니다.
- Android 네이티브 경험과 강제성에 더 무게를 두고 있습니다.

## 아키텍처 🏗️

앱은 Android 클라이언트와 Cloudflare Worker 백엔드로 구성된 2-tier 구조입니다.

```
Android App (Kotlin)
       │
       │  HTTPS / Bearer Token (Firebase JWT)
       ▼
Cloudflare Worker  (worker/src/index.js)
       │
       ├── Firebase Auth  ← JWT 서명 검증 (Google JWK)
       └── Cloudflare D1  ← 유저 / 티켓 / 이벤트 데이터 (SQLite)
```

- Android 앱은 Firebase 로그인 후 발급된 **ID 토큰(JWT)** 을 Bearer 헤더에 담아 Worker에 요청합니다.
- Worker는 Google 공개키(JWK)를 직접 가져와 `crypto.subtle`로 서명을 검증하며, Firebase SDK에 의존하지 않습니다.
- 티켓 데이터는 Cloudflare D1(SQLite)에 저장되고, 중복 이벤트는 `client_event_id`로 멱등성을 보장합니다.

## Worker API 엔드포인트 🔌

`worker/src/index.js`에 정의된 REST API입니다. 모든 인증 요청은 `Authorization: Bearer <Firebase ID Token>` 헤더가 필요합니다.

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| `GET` | `/version` | 불필요 | 앱 허용 버전 / 최신 버전 조회 |
| `POST` | `/auth/session` | 필수 | Firebase 로그인 → 유저 코드 & 티켓 수 반환 |
| `GET` | `/me` | 필수 | 현재 유저 정보 조회 |
| `POST` | `/tickets/merge` | 필수 | 로컬 티켓과 서버 티켓 수 병합 (최댓값 채택) |
| `POST` | `/tickets/events` | 필수 | 티켓 증감 이벤트 기록 (`clientEventId`로 중복 방지) |
| `POST` | `/tickets/transfer` | 필수 | 유저 코드로 다른 유저에게 티켓 전송 |
| `POST` | `/redeem` | 선택 | 리딤 코드 입력 → 티켓 지급 |

> 유저 코드는 `OF-XXXX-XXXX` 형식으로 서버에서 자동 생성됩니다.

## 기술 스택 🧱

### Android

- Kotlin
- Jetpack Compose
- Material 3
- Google Maps SDK / Maps Compose
- Google Maps 3D API
- DataStore
- Room
- Foreground Service
- Navigation Compose
- MVVM

### Backend (Cloudflare Worker)

- JavaScript (ES Modules)
- Cloudflare Workers — 엣지 런타임
- Cloudflare D1 — SQLite 기반 서버리스 DB
- Firebase Auth — JWT 기반 인증 (`crypto.subtle` 직접 검증)

## 프로젝트 구조 📁

```
openflight4and/
├── app/                    # Android 앱 소스 (Kotlin)
├── worker/
│   └── src/
│       └── index.js        # Cloudflare Worker 백엔드 API
├── tools/                  # 빌드/배포 보조 스크립트
├── build.gradle.kts
├── settings.gradle.kts
├── local.defaults.properties
└── README.md
```

## API 키 설정 🔑

`local.properties`에 Google Maps API 키를 넣어야 합니다.

```properties
MAPS_API_KEY=your_api_key_here
```

필요하면 Maps 3D 키도 별도로 넣을 수 있습니다. 비워 두면 `MAPS_API_KEY`를 같이 사용합니다.

```properties
MAPS3D_API_KEY=your_maps3d_api_key_here
```

## 최근 추가된 기능 🔐

- 잠금 단계 분리
  - 포커스 잠금 OFF: 앱 차단 없음
  - 기본 포커스 잠금: 차단 오버레이 표시, 허용 앱 실행 가능
  - 고급 잠금: 허용 앱 실행 차단, 더 강한 잠금 동작
- 긴급 잠금해제
  - 포커스 잠금 사용 중에만 표시
  - 하루 1회, 20분 동안 잠금 일시 해제
- 여정 포기 동작 보정
  - 포기 직후 비행기 위치가 출발지 쪽으로 되감기지 않도록 마지막 진행 위치를 유지

## 빌드 및 릴리스 정책 🚀

- 안정판과 베타 빌드를 분리해 배포합니다.
- 베타 빌드는 `V2.8.7.Beta.0002`처럼 표시되어 쉽게 구분할 수 있습니다.
- 베타 빌드는 GitHub `Pre-release`로 배포되며, 안정판과 분리되어 관리됩니다.

## 라이선스 📄

이 프로젝트는 **All Rights Reserved / View-Only** 정책을 따릅니다.  
자세한 내용은 [LICENSE](LICENSE)를 확인하세요.
