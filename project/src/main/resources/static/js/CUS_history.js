// [✅ CUS_history.js 파일 전체를 이 코드로 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    
    const searchForm = document.getElementById('search-form');
    const calcButton = document.getElementById('btn-calculate-summary');
    const summaryModal = document.getElementById('summary-modal');
	// [추가] 날짜 입력 필드를 변수로 가져옵니다.
	const startDateInput = document.getElementById('start-date');
	const endDateInput = document.getElementById('end-date');
    // [추가] 평점 모달과 테이블 body를 변수로 가져옵니다.
    const ratingModal = document.getElementById('rating-modal');
    const tableBody = document.querySelector('.transaction-table tbody');
	
	
    if (!searchForm || !calcButton || !summaryModal) {
        console.error("필수 요소를 찾을 수 없습니다.");
        return;
    }
	
	// [추가] 페이지 로드 시 기본 날짜(해당 월 1일 ~ 오늘)를 설정하는 함수
	const setDefaultDates = () => {
	    const today = new Date();
	    const firstDayOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);

	    const formatDate = (date) => {
	        const year = date.getFullYear();
	        const month = String(date.getMonth() + 1).padStart(2, '0');
	        const day = String(date.getDate()).padStart(2, '0');
	        return `${year}-${month}-${day}`;
	    };

	    // URL에 이미 날짜 파라미터가 없는 경우에만 기본값을 설정합니다.
	    const params = new URLSearchParams(window.location.search);
	    if (!params.has('startDate') && !params.has('endDate')) {
	        startDateInput.value = formatDate(firstDayOfMonth);
	        endDateInput.value = formatDate(today);
	    }
	};

    // --- 검색 기능 ---
    searchForm.addEventListener('submit', (e) => {
        // form의 기본 제출 이벤트를 막지 않고, 그대로 페이지를 새로고침하며 검색합니다.
    });

    // --- 금액 계산 및 모달 로직 (기존과 동일) ---
    function showSummaryModal() {
        const rows = tableBody.querySelectorAll('tr');

        if (rows.length === 0 || (rows.length === 1 && rows[0].querySelector('td[colspan="6"]'))) {
            alert('계산할 데이터가 없습니다.');
            return;
        }

        let totalKRW = 0;
        let totalUSD = 0;
        let detailsHtml = '<table class="details-table" style="width:100%; border-collapse: collapse;"><thead><tr style="border-bottom: 1px solid #dee2e6;"><th style="padding: 8px; text-align: left;">품명</th><th style="padding: 8px; text-align: right;">확정 운임</th></tr></thead><tbody>';

        rows.forEach(row => {
            const cells = row.querySelectorAll('td');
            const itemName = cells[1].textContent;
            const priceText = cells[3].textContent;
            
            const [amountStr, currency] = priceText.split(' ');
            const amount = parseFloat(amountStr.replace(/,/g, ''));
            
            if (currency === 'KRW') totalKRW += amount;
            if (currency === 'USD') totalUSD += amount;
            
            detailsHtml += `<tr style="border-bottom: 1px solid #f1f3f5;"><td style="padding: 8px;">${itemName}</td><td style="padding: 8px; text-align: right;">${amount.toLocaleString()} ${currency}</td></tr>`;
        });

        detailsHtml += '</tbody></table>';
        
        let summaryHtml = '<hr style="margin: 20px 0;"><h4 style="text-align: right;">총 지출 합계</h4>';
        if (totalKRW > 0) summaryHtml += `<p style="text-align: right; font-size: 1.2em; font-weight: bold; color: black;">${totalKRW.toLocaleString()} KRW</p>`;
        if (totalUSD > 0) summaryHtml += `<p style="text-align: right; font-size: 1.2em; font-weight: bold; color: black;">${totalUSD.toLocaleString()} USD</p>`;
        
        const startDate = document.getElementById('start-date').value;
        const endDate = document.getElementById('end-date').value;
        
        summaryModal.querySelector('#summary-modal-title').textContent = `지출 합계 (${startDate} ~ ${endDate})`;
        summaryModal.querySelector('#summary-modal-body').innerHTML = detailsHtml + summaryHtml;
        summaryModal.style.display = 'flex';
    }

    calcButton.addEventListener('click', () => {
        const startDate = document.getElementById('start-date').value;
        const endDate = document.getElementById('end-date').value;
        if (!startDate || !endDate) {
            alert('금액을 계산하려면 시작일과 종료일을 모두 선택해야 합니다.');
            return;
        }
        showSummaryModal();
    });

    summaryModal.querySelector('.btn-close').addEventListener('click', () => summaryModal.style.display = 'none');
    summaryModal.querySelector('.btn-cancel').addEventListener('click', () => summaryModal.style.display = 'none');
	setDefaultDates();

    // --- 평점 주기 모달 로직 (새로 추가되는 부분) ---
	if (ratingModal && tableBody) {
	    const forwarderNameEl = document.getElementById('rating-forwarder-name');
	    const itemNameEl = document.getElementById('rating-item-name');
	    const submitRatingBtn = document.getElementById('btn-submit-rating');
	    let currentRatingButton = null;

	    const closeRatingModal = () => ratingModal.style.display = 'none';
	    ratingModal.querySelector('.btn-close').addEventListener('click', closeRatingModal);
	    ratingModal.querySelector('.btn-cancel').addEventListener('click', closeRatingModal);

	    tableBody.addEventListener('click', (e) => {
	        const target = e.target;
	        if (target.classList.contains('btn-rate-forwarder') && !target.disabled) {
	            currentRatingButton = target;
	            const forwarderName = target.dataset.forwarderName;
	            const itemName = target.dataset.itemName;

	            forwarderNameEl.textContent = forwarderName;
	            itemNameEl.textContent = `'${itemName}'`;

	            // 모든 별점 그룹 초기화
	            const ratingGroups = ratingModal.querySelectorAll('.star-rating input');
	            ratingGroups.forEach(star => star.checked = false);

	            ratingModal.style.display = 'flex';
	        }
	    });

	    submitRatingBtn.addEventListener('click', () => {
	        // 각 항목별로 선택된 평점 확인
	        const overallRating = ratingModal.querySelector('input[name="overall_rating"]:checked');
	        const priceRating = ratingModal.querySelector('input[name="price_rating"]:checked');
	        const speedRating = ratingModal.querySelector('input[name="speed_rating"]:checked');
	        const stabilityRating = ratingModal.querySelector('input[name="stability_rating"]:checked');

	        // 모든 항목이 선택되었는지 검증
	        if (!overallRating || !priceRating || !speedRating || !stabilityRating) {
	            alert('모든 항목의 평점을 선택해주세요.');
	            return;
	        }

	        // 제출된 평점 정보를 종합하여 메시지 생성
	        const message = `평가가 제출되었습니다. 소중한 의견 감사합니다!`;
	        alert(message);
	        
	        if (currentRatingButton) {
	            currentRatingButton.textContent = '평가 완료';
	            currentRatingButton.disabled = true;
	            currentRatingButton.classList.remove('btn-outline');
	            currentRatingButton.classList.add('btn-rated');
	        }
	        
	        closeRatingModal();
	    });
	}
});