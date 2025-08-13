// [✅ /static/js/fwd-request-timer.js 파일 전체를 이 코드로 교체해주세요]

// 타이머 업데이트 로직을 전역에서 접근 가능한 함수로 정의합니다.
window.updateAllTimers = () => {
    const timerButtons = document.querySelectorAll('.btn-timer');
    const now = new Date();
    
    timerButtons.forEach(button => {
        const deadlineString = button.dataset.deadlineDatetime;
        if (!deadlineString) return;

        const deadline = new Date(deadlineString);
        const diff = deadline - now;

        const card = button.closest('.request-card');
        const quoteBtn = card ? card.querySelector('.btn-quote') : null;

        if (diff <= 0) {
            button.textContent = '마감';
            button.classList.remove('btn-danger'); // 빨간색 배경 제거
            button.classList.add('is-expired');    // 비활성 스타일 적용
            if (quoteBtn) {
                quoteBtn.disabled = true;
            }
        } else {
            const days = Math.floor(diff / (1000 * 60 * 60 * 24));
            const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
            const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
            const seconds = Math.floor((diff % (1000 * 60)) / 1000);

            if (days > 0) {
                button.textContent = `${days}일 ${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
            } else {
                button.textContent = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
            }

            if (quoteBtn) {
                quoteBtn.disabled = false;
            }
        }
    });
};

document.addEventListener('DOMContentLoaded', () => {
    // 페이지 로드 시, 최초 한 번 즉시 실행하여 타이머를 바로 표시
    if (typeof window.updateAllTimers === 'function') {
        window.updateAllTimers();
    }
    
    // 그 후 1초마다 주기적으로 모든 타이머를 업데이트
    setInterval(window.updateAllTimers, 1000);
});