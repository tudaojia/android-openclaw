# 테스트 플랜 — Claw on Android

## 1. 앱 설치 및 초기 설정

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 1.1 | APK 빌드 | `./gradlew assembleDebug` 성공, 에러 없음 | ✅ PASS |
| 1.2 | 웹 UI 빌드 | `npm run build` 성공, dist/ 생성 | ✅ PASS |
| 1.3 | 앱 이름 확인 | 런처에 "Claw on Android" 표시 | ✅ PASS (strings.xml 확인) |
| 1.4 | 앱 설치 | APK 설치 성공 | 📱 기기 테스트 필요 |
| 1.5 | 최초 실행 시 Setup 화면 | 부트스트랩 미설치 시 Setup 화면 표시 | 📱 기기 테스트 필요 |

## 2. Setup 플로우 (openclaw onboard)

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 2.1 | 플랫폼 선택 단계 | Stepper 표시, OpenClaw 카드 표시 및 선택 가능 | 📱 기기 테스트 필요 |
| 2.2 | 도구 선택 단계 | 7개 선택도구 표시, 토글 동작, "아무것도 설치하지 않음" 가능 | 📱 기기 테스트 필요 |
| 2.3 | 설치 진행 | 프로그레스 바, 진행 메시지, 팁 카드 표시 | 📱 기기 테스트 필요 |
| 2.4 | 설치 완료 | "You're all set!" 표시, "Open Terminal" 버튼 동작 | 📱 기기 테스트 필요 |
| 2.5 | 터미널에서 openclaw onboard | LLM: Gemini, 키: AIzaSyBWxFwDi_EVCALxjhjesQBcLyxzpVttMlU | 📱 기기 테스트 필요 |
| 2.6 | openclaw gateway 실행 | 터미널에서 게이트웨이 실행 성공 | 📱 기기 테스트 필요 |

## 3. 탭 네비게이션

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 3.1 | Terminal 탭 클릭 | TerminalView 표시, 소프트 키보드 자동 표시 | 📱 기기 테스트 필요 |
| 3.2 | Dashboard 탭 클릭 | Dashboard 화면 표시, WebView 전환 | ✅ PASS (Playwright) |
| 3.3 | Settings 탭 클릭 | Settings 메뉴 표시 | ✅ PASS (Playwright) |
| 3.4 | 활성 탭 하이라이트 | 현재 탭에 active 스타일 표시 | ✅ PASS (Playwright) |
| 3.5 | Terminal ↔ WebView 전환 | 뒤로가기 시 WebView로 복귀 | 📱 기기 테스트 필요 |

## 4. Dashboard

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 4.1 | 플랫폼 헤더 | 플랫폼 이름, 게이트웨이 상태 (Running/Not running) 표시 | 📱 기기 테스트 필요 |
| 4.2 | 게이트웨이 미실행 시 | "Gateway is not running" 메시지, "Open Terminal" 버튼 | ✅ PASS (Playwright) |
| 4.3 | 게이트웨이 실행 시 | Gateway URL 표시, Copy 버튼, Quick Actions (Restart/Stop) | 📱 기기 테스트 필요 |
| 4.4 | Runtime 정보 | Node.js, git, openclaw 버전 표시 | 📱 기기 테스트 필요 |
| 4.5 | Status 카드 | 클릭 시 터미널에서 상태 확인 명령 실행 (oa --status 대응) | 📱 기기 테스트 필요 |
| 4.6 | Update 카드 | 클릭 시 터미널에서 업데이트 명령 실행 (oa --update 대응) | 📱 기기 테스트 필요 |
| 4.7 | Install Tools 카드 | 클릭 시 Settings > Tools로 이동 (oa --install 대응) | ✅ PASS (Playwright) |
| 4.8 | 15초 주기 자동 새로고침 | setInterval(refreshStatus, 15000) 동작 | 코드 확인 완료 |

## 5. Settings

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 5.1 | 메뉴 항목 표시 | Platforms, Updates, Additional Tools, Keep Alive, Storage, About | ✅ PASS (Playwright) |
| 5.2 | 각 메뉴 네비게이션 | 클릭 시 해당 서브페이지로 이동, 뒤로가기 동작 | ✅ PASS (Playwright) |

## 6. Settings > Additional Tools (oa --install 대응)

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 6.1 | 도구 목록 완전성 | 11개 도구 모두 표시: tmux, code-server, OpenCode, Claude Code, Gemini CLI, Codex CLI, SSH Server, ttyd, dufs, Android Tools, Chromium | ✅ PASS (코드 확인) |
| 6.2 | 카테고리 분류 | Terminal Tools, AI Tools, Network & Access, System 4개 카테고리 | ✅ PASS (코드 확인) |
| 6.3 | 설치 상태 표시 | 설치된 도구는 "Installed ✓", 미설치는 "Install" 버튼 | 📱 기기 테스트 필요 |
| 6.4 | 설치 프로그레스 | 설치 중 프로그레스 바 표시, 설치 완료 후 상태 갱신 | 📱 기기 테스트 필요 |
| 6.5 | npm 도구 설치 | claude-code, gemini-cli, codex-cli, opencode npm install 명령 정확 | ✅ PASS (JsBridge 코드 확인) |
| 6.6 | pkg 도구 설치 | tmux, ttyd, dufs, android-tools, chromium apt-get install 명령 정확 | ✅ PASS (JsBridge 코드 확인) |
| 6.7 | 도구 삭제 | 설치된 도구의 삭제 동작 (npm uninstall / apt-get remove) | ✅ PASS (JsBridge 코드 확인) |

## 7. Settings > Platforms

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 7.1 | 플랫폼 목록 | 사용 가능한 플랫폼 표시, 현재 활성 플랫폼 표시 | 📱 기기 테스트 필요 |
| 7.2 | 플랫폼 설치 | "Install & Switch" 버튼 동작, 프로그레스 표시 | 📱 기기 테스트 필요 |

## 8. Settings > Updates (oa --update 대응)

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 8.1 | 업데이트 확인 | "Checking for updates..." → 결과 표시 | 📱 기기 테스트 필요 |
| 8.2 | 최신 상태 | "Everything is up to date." 표시 | 📱 기기 테스트 필요 |
| 8.3 | 업데이트 가능 | 컴포넌트별 현재/새 버전, "Update" 버튼 | 📱 기기 테스트 필요 |

## 9. Settings > Keep Alive

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 9.1 | 배터리 최적화 | 상태 표시 (✓ Excluded / Request Exclusion 버튼) | 📱 기기 테스트 필요 |
| 9.2 | 개발자 옵션 안내 | "Stay Awake" 활성화 안내, 설정 열기 버튼 | ✅ PASS (코드 확인) |
| 9.3 | Phantom Process Killer | ADB 명령 표시, Copy 버튼 동작 | ✅ PASS (Playwright) |

## 10. Settings > Storage

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 10.1 | 저장 공간 정보 | Bootstrap, Web UI, Free Space 크기 표시 | 📱 기기 테스트 필요 |
| 10.2 | 캐시 삭제 | "Clear Cache" 버튼 동작 | 📱 기기 테스트 필요 |

## 11. Settings > About

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 11.1 | 앱 이름 표시 | "Claw on Android" 표시 | ✅ PASS (코드 확인) |
| 11.2 | 버전 정보 | APK 버전, 패키지 이름 표시 | 📱 기기 테스트 필요 |
| 11.3 | Runtime 정보 | Node.js, git 버전 표시 | 📱 기기 테스트 필요 |
| 11.4 | App Info 버튼 | 클릭 시 Android 앱 정보 화면 열기 | 📱 기기 테스트 필요 |

## 12. oa CLI ↔ UI 기능 매핑

| oa 명령 | UI 대응 | 결과 |
|---------|---------|------|
| `oa --update` | Dashboard > Update 카드 + Settings > Updates | ✅ PASS |
| `oa --install` | Dashboard > Install Tools + Settings > Additional Tools (11개 도구) | ✅ PASS |
| `oa --uninstall` | UI 미제공 (의도적 — 앱 자체에서 삭제는 Android 설정에서 처리) | ✅ N/A |
| `oa --status` | Dashboard > Status 카드 + Dashboard Runtime 정보 | ✅ PASS |
| `oa --version` | Settings > About (APK 버전 + Runtime 버전) | ✅ PASS |
| `oa --help` | UI는 자체적으로 직관적 (도움말 불필요) | ✅ N/A |

## 13. 터미널 기능

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 13.1 | 터미널 세션 생성 | "+" 버튼으로 새 세션 생성 | 📱 기기 테스트 필요 |
| 13.2 | 세션 탭 표시 | 활성 세션 하이라이트, 세션 이름 표시 | 📱 기기 테스트 필요 |
| 13.3 | 세션 전환 | 탭 클릭으로 세션 전환 | 📱 기기 테스트 필요 |
| 13.4 | 세션 종료 | "×" 버튼으로 세션 닫기 | 📱 기기 테스트 필요 |
| 13.5 | Extra Keys | Esc, Tab, Home, End, 방향키, Ctrl, Alt, -, | 동작 | 📱 기기 테스트 필요 |
| 13.6 | 텍스트 크기 조절 | 핀치 줌으로 크기 조절 | 📱 기기 테스트 필요 |

## 14. 코드 품질

| # | 테스트 항목 | 확인 내용 | 결과 |
|---|------------|----------|------|
| 14.1 | TypeScript 빌드 | `tsc -b` 타입 에러 없음 | ✅ PASS |
| 14.2 | Vite 빌드 | `vite build` 에러 없음 | ✅ PASS |
| 14.3 | Kotlin 빌드 | `./gradlew assembleDebug` 에러 없음 (deprecated 경고 1건: versionCode) | ✅ PASS |
| 14.4 | Setup.tsx useState 수정 | useState 대신 useEffect 사용으로 React 규칙 준수 | ✅ PASS |
| 14.5 | JsBridge 도구 핸들러 | 11개 도구 모두 install/uninstall/detect 정상 | ✅ PASS |

## 요약

| 구분 | 전체 | PASS | 기기 테스트 필요 | N/A |
|------|------|------|----------------|-----|
| 앱 설치 (1) | 5 | 3 | 2 | 0 |
| Setup (2) | 6 | 0 | 6 | 0 |
| 탭 네비게이션 (3) | 5 | 3 | 2 | 0 |
| Dashboard (4) | 8 | 3 | 5 | 0 |
| Settings (5) | 2 | 2 | 0 | 0 |
| Tools (6) | 7 | 5 | 2 | 0 |
| Platforms (7) | 2 | 0 | 2 | 0 |
| Updates (8) | 3 | 0 | 3 | 0 |
| Keep Alive (9) | 3 | 2 | 1 | 0 |
| Storage (10) | 2 | 0 | 2 | 0 |
| About (11) | 4 | 1 | 3 | 0 |
| oa 매핑 (12) | 6 | 4 | 0 | 2 |
| 터미널 (13) | 6 | 0 | 6 | 0 |
| 코드 품질 (14) | 5 | 5 | 0 | 0 |
| **합계** | **64** | **28** | **34** | **2** |
