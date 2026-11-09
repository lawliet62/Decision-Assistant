import Link from "next/link";

export default function PrivacyPage() {
  return <main className="mx-auto w-full max-w-3xl space-y-6 px-6 py-12">
    <Link href="/" className="text-sm text-emerald-700">홈으로</Link>
    <h1 className="text-3xl font-semibold">데이터 이용 안내</h1>
    <p>Decision Assistant는 초대받은 사용자가 제품을 비교하고 선택·회고를 기록하는 포트폴리오 서비스입니다. 민감한 개인정보, 금융·건강 정보, 비공개 업무 정보는 입력하지 마세요.</p>
    <section className="space-y-2"><h2 className="text-xl font-semibold">저장하는 정보</h2>
      <p>로그인을 위한 계정 식별자·이메일·표시 이름과 사용자가 입력한 결정, 후보, 비교 조건, 선택, 회고 및 성향 기억을 저장합니다. 자신의 결정은 다른 사용자에게 공개되지 않습니다.</p>
      <p>Auth0가 로그인을 처리하며 선택한 Google 계정 또는 이메일 로그인 제공자의 정책도 적용됩니다. 세션 쿠키와 요청 위조 방지 쿠키를 사용합니다.</p>
    </section>
    <section className="space-y-2"><h2 className="text-xl font-semibold">결정 삭제</h2>
      <p>내 결정 목록의 삭제 기능은 서버 DB에서도 결정을 영구 삭제합니다. 후보·평가와 AI 기록·회고·예약 알림·이 결정에서 생성된 성향 기억이 함께 삭제되며 앱에서 복구할 수 없습니다.</p>
      <p>다른 결정에 이미 적용된 값, 공유 제품 카탈로그와 계정은 유지합니다. 이미 전송된 메일과 외부 서비스 요청은 되돌릴 수 없습니다.</p>
      <p>운영 백업은 매일 만들고 7일 보존 후 만료 대상으로 처리합니다. 저장소의 만료 삭제는 비동기이므로 즉시 물리 파기를 뜻하지 않습니다. 이전 백업을 복원할 때는 삭제 내역을 다시 적용한 뒤 서비스를 재개합니다.</p>
    </section>
    <section className="space-y-2"><h2 className="text-xl font-semibold">외부 서비스와 운영 기록</h2>
      <p>기본 등록 카탈로그 추천과 직접 비교는 구매 조건을 AI·검색·수집 서비스에 보내지 않습니다. 관련 기능을 활성화한 경우에는 실행 화면에서 제공자와 전송 항목을 안내하고 동의를 받습니다. 외부 제품 이미지와 구매 링크를 열면 해당 서버에 접속 정보가 전달될 수 있습니다.</p>
      <p>장애·삭제 확인용 운영 로그는 14일 보존합니다. 결정 제목·구매 메모·인증 비밀값은 로그에 넣지 않습니다. 삭제 기록은 백업 복원 시 재삭제를 위한 결정 식별자와 시각을 포함합니다.</p>
      <p>계정 처리, 보존 정보와 데이터 문의는 체험을 초대한 운영자에게 요청해 주세요. 이 앱은 계정 삭제 화면을 제공하지 않습니다.</p>
    </section>
  </main>;
}
