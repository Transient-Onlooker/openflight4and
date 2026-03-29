# ✈️ OpenFlight4And

> **비행하는 시간만큼 집중하는 생산성 앱**

FocusFlight 를 벤치마킹하여 개발한 안드로이드 집중 타이머 앱입니다. 실제 비행 시간을 체험하며 생산적인 시간을 만들어보세요.

---

## 📱 소개

OpenFlight4And 는 **비행 시간을 포커스 타이머로 활용**하는 독특한 컨셉의 생산성 앱입니다.

- ✈️ 실제 공항 간 거리를 기반으로 **비행 시간 계산**
- 🗺️ **실시간 지도**에서 비행기의 이동 경로 추적
- ⏱️ 비행이 완료될 때까지 집중 모드 유지
- 📊 비행 기록 및 통계 관리

---

## 🎯 주요 기능

### 비행 시작
- 출발지와 목적지 공항 선택
- 대권 (Great Circle) 거리 계산을 통한 **현실적 비행 시간** 자동 설정
- 현재 위치 기반 자동 출발지 설정

### 탑승권 발급
- 항공편 정보와 탑승권 형태의 UI
- 출발/도착 정보, 좌석 번호 표시

### 좌석 선택
- 집중 카테고리 선택 (업무, 학습, 휴식 등)
- 나만의 좌석 지정

### 비행 중 (In-Flight)
- **Google Maps** 기반 실시간 지도 추적
- 비행기 마커가 대권 경로를 따라 이동
- 남은 시간 카운트다운
- **백그라운드 서비스** 지원 (앱 종료 후에도 계속 실행)
- 알림창에서 진행 상황 확인

### 비행 완료
- 자동 기록 저장
- 도착지가 다음 비행의 출발지로 자동 설정
- 총 비행 횟수 업데이트

### 비행 기록 (History)
- 과거 비행 세션 조회
- 완료된 비행 목록 확인

### 통계 및 추세 (Trend)
- 비행 통계 대시보드
- **샌드박스 모드**: 시간 배율 조절 기능 (테스트용)

### 설정
- 지도 스타일 변경 (일반 / 다크)
- 단위 시스템 (km/mi)
- 비행기 모드 확인 옵션

---

## 🏗️ 기술 스택

| 분야 | 기술 |
|------|------|
| **언어** | Kotlin |
| **UI** | Jetpack Compose, Material 3 |
| **지도** | Google Maps SDK, Maps Compose |
| **아키텍처** | MVVM, Clean Architecture |
| **비동기** | Kotlin Coroutines, Flow |
| **데이터** | DataStore, Room |
| **서비스** | Foreground Service, WorkManager |
| **네비게이션** | Navigation Compose |
| **DI** | Manual DI |

---

## 🚀 주요 구현 내용

### 대권 보간 (Great Circle Interpolation)
```kotlin
// 지구 곡면을 따른 두 지점 사이의 최단 거리 경로 보간
fun interpolateGreatCircle(start: LatLng, end: LatLng, fraction: Double): LatLng
```

### 백그라운드 비행 서비스
- `FlightService`: 포그라운드 서비스로 비행 타이머 관리
- 알림창을 통한 실시간 진행 상황 표시
- 앱 종료 후에도 비행 계속 유지

### 자동 위치 업데이트
- 비행 완료 시 도착지가 다음 출발지로 자동 설정
- DataStore 를 통한 상태 지속성 보장

---

## 🔧 설정

### API 키 설정
Google Maps API 키가 필요합니다. `local.properties` 에 추가하세요:
```properties
MAPS_API_KEY=your_api_key_here
```

---

## 🤝 기여

이슈 및 PR 환영합니다!

---

## 📄 라이선스

이 프로젝트는 **All Rights Reserved / View-Only** 라이선스 정책을 따릅니다.

자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

---

## 🙏 감사의 글

이 프로젝트는 **FocusFlight** 앱에서 영감을 받아 개발되었습니다.

---
