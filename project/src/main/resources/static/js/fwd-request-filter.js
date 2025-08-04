// [✅ fwd-request-filter.js 파일 전체를 이 코드로 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    const portModal = document.getElementById('port-modal');
    if (!portModal) return;

    const openModalBtn = document.getElementById('btn-open-port-modal');
    const closeModalBtn = portModal.querySelector('.btn-close');
    const applyBtn = document.getElementById('btn-apply-port-filter');
    const resetBtn = document.getElementById('btn-reset-port-filter');
    const departurePorts = document.querySelectorAll('input[name="departure-port"]');
    const arrivalPorts = document.querySelectorAll('input[name="arrival-port"]');

    // ★★★ 핵심 로직: 항구 옵션 상태를 업데이트하는 함수 ★★★
    function updatePortOptions() {
        const selectedDeparture = document.querySelector('input[name="departure-port"]:checked')?.value;
        const selectedArrival = document.querySelector('input[name="arrival-port"]:checked')?.value;

        // 도착항 목록을 순회하며, 선택된 출발항과 같은 값을 가진 옵션을 비활성화
        arrivalPorts.forEach(input => {
            if (selectedDeparture && input.value === selectedDeparture) {
                input.disabled = true;
            } else {
                input.disabled = false;
            }
        });

        // 출발항 목록을 순회하며, 선택된 도착항과 같은 값을 가진 옵션을 비활성화
        departurePorts.forEach(input => {
            if (selectedArrival && input.value === selectedArrival) {
                input.disabled = true;
            } else {
                input.disabled = false;
            }
        });
    }

    // 현재 URL의 모든 파라미터를 가져오는 헬퍼 함수
    const getCurrentParams = () => {
        return new URLSearchParams(window.location.search);
    };

    // 모달 열기/닫기 로직 (기존과 동일)
    openModalBtn.addEventListener('click', () => portModal.style.display = 'flex');
    const closeModal = () => portModal.style.display = 'none';
    closeModalBtn.addEventListener('click', closeModal);
    portModal.addEventListener('click', (e) => {
        if (e.target === portModal) closeModal();
    });

    // 초기화 버튼
    resetBtn.addEventListener('click', () => {
        const params = getCurrentParams();
        params.delete('departurePort');
        params.delete('arrivalPort');
        window.location.search = params.toString();
    });

    // 적용 버튼
    applyBtn.addEventListener('click', () => {
        const params = getCurrentParams();
        const selectedDeparture = document.querySelector('input[name="departure-port"]:checked');
        const selectedArrival = document.querySelector('input[name="arrival-port"]:checked');

        if (selectedDeparture) {
            params.set('departurePort', selectedDeparture.value);
        } else {
            params.delete('departurePort');
        }

        if (selectedArrival) {
            params.set('arrivalPort', selectedArrival.value);
        } else {
            params.delete('arrivalPort');
        }
        params.set('page', '0');
        window.location.search = params.toString();
    });
    
    // ★★★ 핵심 로직: 모든 라디오 버튼에 'change' 이벤트 리스너 추가 ★★★
    document.querySelectorAll('input[name="departure-port"], input[name="arrival-port"]').forEach(radio => {
        radio.addEventListener('change', updatePortOptions);
    });

    // ★★★ 핵심 로직: 페이지 로드 시, 현재 선택된 값 기준으로 초기 상태 설정 ★★★
    updatePortOptions();
});