// [✅ fwd-request-timer.js 파일 전체를 이 코드로 교체해주세요]

document.addEventListener('DOMContentLoaded', () => {
    const timerButtons = document.querySelectorAll('.btn-timer');

    const updateAllTimers = () => {
        const now = new Date();
        timerButtons.forEach(button => {
            const deadlineString = button.dataset.deadlineDatetime;
            if (!deadlineString) return;

            const deadline = new Date(deadlineString);
            const diff = deadline - now;

            const card = button.closest('.request-card');
            const quoteBtn = card ? card.querySelector('.btn-quote') : null;

            if (diff <= 0) {
                // 마감 시간이 지났을 경우
                button.textContent = '마감';
                button.classList.add('is-expired');
                if (quoteBtn) {
                    quoteBtn.disabled = true;
                }
            } else {
                // ★★★ 핵심 수정: 남은 시간을 일/시/분/초로 계산하여 표시 ★★★
                const days = Math.floor(diff / (1000 * 60 * 60 * 24));
                const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
                const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
                const seconds = Math.floor((diff % (1000 * 60)) / 1000);

                if (days > 0) {
                    // 남은 시간이 하루 이상일 경우 "N일 HH:MM" 형식으로 표시
                    button.textContent = `${days}일 ${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
                } else {
                    // 남은 시간이 하루 미만일 경우 "HH:MM:SS" 형식으로 표시
                    button.textContent = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
                }

                if (quoteBtn) {
                    quoteBtn.disabled = false;
                }
            }
        });
    };

    updateAllTimers();
    setInterval(updateAllTimers, 1000);
});