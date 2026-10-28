# OpenFlight4And

> 출발지와 목적지를 선택하고 가상 비행을 완주하는 Android 집중 앱

일반적인 카운트다운 대신 **공항 선택 → 노선·예상 비행시간 계산 → 탑승·비행 → 도착 기록**으로 집중 세션을 구성했습니다. 지도, 보딩패스, 비행권, 집중 잠금 기능을 Android 네이티브 UI와 결합한 개인 개발 프로젝트입니다.

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

단순 타이머 화면이 아니라 **노선 선택·보딩패스·좌석 선택·비행 진행·도착 기록**을 하나의 집중 흐름으로 설계했습니다. 지도 표시를 2D / 2.5D / 3D로 전환할 수 있으며, 비행권·보상과 집중 잠금을 별도 사용자 경험으로 연결했습니다.

집중 잠금은 Android의 사용 정보 접근 및 다른 앱 위에 표시 권한을 활용합니다. 권한과 기기 제조사별 동작 차이가 있으므로 실제 차단 동작은 해당 기기에서 확인해야 합니다.

## 아키텍처 🏗️

Android 클라이언트와 Firebase 인증 기반의 서버리스 백엔드로 구성되어 있습니다.  
비행권 등 서버 연동이 필요한 기능은 클라우드 백엔드를 통해 처리됩니다.

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

### Backend

- Cloudflare Workers
- Cloudflare D1
- Firebase Auth

## 설치와 코드 구조 🧪

Android Studio에서 루트 프로젝트를 열고 Gradle 동기화 후 `app` 모듈을 빌드합니다. Firebase 인증이나 서버 연동 기능은 해당 환경 설정을 별도로 준비해야 정상 동작할 수 있습니다. GitHub Actions 빌드 설정은 [워크플로](.github/workflows/android.yml)를 참고하세요.

- 앱 진입점: `app/src/main/java/com/example/openflight4and/MainActivity.kt`
- 비행 서비스: `app/src/main/java/com/example/openflight4and/service/FlightService.kt`
- 로컬 데이터: `app/src/main/java/com/example/openflight4and/data/`
- 집중 잠금: `app/src/main/java/com/example/openflight4and/focus/`
- Android 테스트: `app/src/androidTest/` (이 문서 갱신 작업에서 실행 결과는 확인하지 않았습니다)

## API 키 설정 🔑

`local.properties`에 Google Maps API 키를 넣어야 합니다.

```properties
MAPS_API_KEY=your_api_key_here
```

필요하면 Maps 3D 키도 별도로 넣을 수 있습니다. 비워 두면 `MAPS_API_KEY`를 같이 사용합니다.

```properties
MAPS3D_API_KEY=your_maps3d_api_key_here
```

Google 로그인과 Firebase 인증을 사용하려면 다음 값도 `local.properties` 또는 같은 이름의 환경 변수로 설정합니다.

```properties
FIREBASE_API_KEY=your_firebase_api_key
FIREBASE_APP_ID=your_firebase_app_id
FIREBASE_PROJECT_ID=your_firebase_project_id
GOOGLE_SIGN_IN_SERVER_CLIENT_ID=your_google_web_client_id
```

값이 없으면 로그인할 수 없습니다. `local.properties`와 실제 설정값은 저장소에 올리지 마세요.

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

- Android CI는 디버그 APK를 빌드하지만 단위 테스트나 기기 계측 테스트를 실행하지 않습니다.

- 안정판과 베타 빌드를 분리해 배포합니다.
- 베타 빌드는 `V2.8.7.Beta.0002`처럼 표시되어 쉽게 구분할 수 있습니다.
- 베타 빌드는 GitHub `Pre-release`로 배포되며, 안정판과 분리되어 관리됩니다.

## 프로젝트 상태

코드에는 릴리스·베타 빌드 구분과 사용자 기능을 위한 구현이 포함되어 있습니다. 다만 저장소의 마지막 코드 업데이트는 2026년 4월이며, 최신 Android/Google Maps SDK 환경에서 앱 전체를 재검증한 것은 아닙니다.

## 라이선스 📄

이 프로젝트는 **All Rights Reserved / View-Only** 정책을 따릅니다.  
자세한 내용은 [LICENSE](LICENSE)를 확인하세요.
