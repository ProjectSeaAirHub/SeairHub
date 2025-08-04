// [✅ CUS_history.js 파일의 전체 내용]
document.addEventListener('DOMContentLoaded', () => {
    
    const searchForm = document.getElementById('search-form');
    const calcButton = document.getElementById('btn-calculate-summary');
    const summaryModal = document.getElementById('summary-modal');
	// [추가] 날짜 입력 필드를 변수로 가져옵니다.
	const startDateInput = document.getElementById('start-date');
	const endDateInput = document.getElementById('end-date');
	
	
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

    // --- 금액 계산 및 모달 로직 ---
    function showSummaryModal() {
        const tableBody = document.querySelector('.transaction-table tbody');
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
        
        summaryModal.querySelector('#summary-modal-title').textContent = `요청 합계 (${startDate} ~ ${endDate})`;
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
	// [추가] 페이지가 처음 로드될 때 기본 날짜 설정 함수를 호출합니다.
	setDefaultDates();
});