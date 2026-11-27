# 운동 플래너 앱 — 인수인계 메모 (2026-11-27)

다른 컴퓨터에서 이어서 만들 때 이 파일부터 읽기. 클로드에게도 "workout-app/HANDOFF.md 읽고 이어서 하자"라고 시작하면 됨.

## 1. 무엇을 만드는 앱
- 날씨·운동 기록·일정을 보고 주간 운동 계획(헬스·러닝·수영)을 짜 주는 안드로이드 앱
- AI 없이 규칙 엔진(제약조건 배치)으로 폰 안에서 계산. 서버 없음
- 규칙은 코드에 넣지 않고 설정 데이터(JSON)로 둠 → 규칙을 고치면 자동으로 다시 짬
- 운동 기록은 Health Connect(구글)로 삼성헬스 데이터를 읽을 예정. 삼성 자체 SDK 기능(수면·체성분 코칭 등)은 보류
- 화면 시안: `박대식/ax/운동계획앱_시안.html` (같은 저장소)

## 2. 구조
| 모듈 | 내용 |
|---|---|
| `:planner` | 순수 Kotlin(JVM) 규칙 엔진. `Model.kt`(규칙·운동 모델), `RuleLogic.kt`(시간 창·시설 휴관), `Planner.kt`(배치), `Checker.kt`(규칙 확인표), `Reasons.kt`(배치 이유 문장), `PlanIo.kt`(JSON 입출력) |
| `:app` | Jetpack Compose 앱. `ui/Screens.kt`(화면 5개), `ui/Celebration.kt`(목표 달성 축하 모션), `reminder/Reminders.kt`(운동 30분 전 알림), `data/Store.kt`(DataStore 규칙 저장), `data/DefaultRules.kt`(처음 설치 시 **예시** 규칙) |

- compileSdk/targetSdk 37, minSdk 28, Java 17, Gradle 9.9.2 (wrapper 포함)
- 패키지: `app.workoutplanner`

## 3. 진행 상황
- [x] 1단계 규칙 엔진 — `:planner` 테스트 11개 통과 (`PlannerTest`, `PersonalRulesTest`)
- [x] 2단계 화면·알림 — 화면 5개, 알림, 규칙 저장. `:app:assembleDebug` 빌드 성공
- [ ] 2단계 실제 화면 확인 — 에뮬레이터 가속 실패(AEHD)로 아직 못 봄
- [ ] 3단계 — 폰 연결, Health Connect로 실제 기록 읽기 → 계획과 비교 → 재계획

## 4. 다른 컴퓨터에서 시작하기
1. Android Studio 설치 (SDK Platform 37, build-tools 36 이상)
2. 이 저장소 clone → Android Studio에서 **Open** → `workout-app` 폴더 선택 → Gradle Sync
   - `local.properties`(SDK 경로)는 컴퓨터마다 달라서 저장소에 없음. Sync할 때 자동 생성
3. 터미널: `.\gradlew :planner:test` → 통과 확인
4. 에뮬레이터: Windows 기능에서 **Windows 하이퍼바이저 플랫폼** 켜고 재부팅 → Device Manager에서 가상 기기 만들기 → 실행

## 5. 개인 규칙·데이터 (저장소에 없음)
- 실제 개인 규칙·일정·삼성헬스 기록은 공개 저장소에 안 올림. `data/`는 gitignore
- `PersonalRulesTest`는 `../data/workout/planner/` 파일이 있을 때만 돌고, 없으면 건너뜀 (실패 아님)
- 다른 컴퓨터에서도 개인 규칙으로 테스트하려면 이 컴퓨터의 `data/workout/` 폴더를 USB 등으로 직접 옮겨서 저장소 루트의 `data/workout/`에 넣기
  - `conditions.md` = 규칙 원본 (클로드에게 먼저 읽게 하기)
  - `planner/` = 개인 규칙 테스트 파일

## 6. 다음에 할 일
1. 에뮬레이터 띄워서 2단계 화면 5개·알림·축하 모션 확인, 고칠 점 정리
2. 3단계: Health Connect 권한 → 운동 기록 읽기 (헬스·러닝·수영 / 걷기 제외) → 계획과 비교(기록 시작이 10~15분 늦어도 같은 운동) → 못 한 운동 재계획
3. 배포는 나중에 (구글 플레이 등록비 1회, Health Connect 권한 신고, 개인정보처리방침 필요)
