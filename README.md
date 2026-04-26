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
