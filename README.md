# 캠핑 노트 (Android)

지역별로 캠핑장을 기록하고, 가격·시설·메모·카카오맵 좌표를 보관하는 개인 관리 앱입니다.

## 실행 전 설정

1. `local.properties.example`을 복사하여 프로젝트 루트에 `local.properties`를 만듭니다.
2. 카카오 디벨로퍼스의 **네이티브 앱 키**를 `kakaoMapKey`에 넣습니다.
3. 카카오 디벨로퍼스 콘솔에서 이 앱의 패키지명(`com.campinginfo`)과 서명 키 해시를 Android 플랫폼에 등록합니다.
4. Android Studio에서 프로젝트를 열어 Gradle 동기화 후 실행합니다.

키는 `local.properties`로만 읽으며 Git에 포함되지 않습니다. 카카오 지도 SDK는 앱 시작 시 초기화되고, 위치 선택 화면에서 지도를 눌러 위도/경도를 저장합니다.
