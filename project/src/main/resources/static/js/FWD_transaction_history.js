// [✅ FWD_transaction_history.js 파일 전체를 이 코드로 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {

    const filterGroup = document.getElementById('transaction-filter-group');
    const tableBody = document.getElementById('transaction-list-body');
    const searchForm = document.getElementById('search-form');
    const keywordInput = document.getElementById('keyword-input');
    const startDateInput = document.getElementById('start-date');
    const endDateInput = document.getElementById('end-date');
    const calcButton = document.getElementById('btn-calculate-summary');
    const summaryModal = document.getElementById('summary-modal');
    
    if (!filterGroup || !tableBody || !searchForm || !calcButton || !summaryModal) {
        console.error('거래내역 페이지의 필수 HTML 요소 중 일부를 찾을 수 없습니다. ID 값을 확인해주세요.');
        return;
    }
    
    const summaryModalTitle = document.getElementById('summary-modal-title');
    const summaryModalBody = document.getElementById('summary-modal-body');

	// [추가] 페이지 로드 시 기본 날짜(해당 월 1일 ~ 오늘)를 설정하는 함수
	const setDefaultDates = () => {
	    const today = new Date();
	    const firstDayOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);

	    // Date 객체를 'YYYY-MM-DD' 형식의 문자열로 변환하는 헬퍼 함수
	    const formatDate = (date) => {
	        const year = date.getFullYear();
	        const month = String(date.getMonth() + 1).padStart(2, '0');
	        const day = String(date.getDate()).padStart(2, '0');
	        return `${year}-${month}-${day}`;
	    };

	    // 각 날짜 입력창에 기본값 설정
	    startDateInput.value = formatDate(firstDayOfMonth);
	    endDateInput.value = formatDate(today);
	};
	
	
    async function fetchAndRenderTransactions() {
        const activeFilter = filterGroup.querySelector('.is-active');
        const filterType = activeFilter ? activeFilter.dataset.filter : 'all';
        const keyword = keywordInput.value;
        const startDate = startDateInput.value;
        const endDate = endDateInput.value;

        let url = '/api/fwd/transactions';
        if (filterType === 'sale') url += '/sales';
        if (filterType === 'purchase') url += '/purchases';

        const params = new URLSearchParams();
        if (startDate) params.append('startDate', startDate);
        if (endDate) params.append('endDate', endDate);
        if (keyword) params.append('keyword', keyword);
        
        const fullUrl = `${url}?${params.toString()}`;

        try {
            const response = await fetch(fullUrl);
            if (!response.ok) throw new Error('데이터를 불러오는 데 실패했습니다.');

            const transactions = await response.json();
            tableBody.innerHTML = '';

            if (transactions.length === 0) {
                tableBody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding: 40px 0; color: #6c757d;">거래 내역이 없습니다.</td></tr>';
                return;
            }

            transactions.forEach(tx => {
                const row = tableBody.insertRow();
                const transactionDate = new Date(tx.transactionDate).toLocaleDateString('ko-KR');
                const price = `${tx.price.toLocaleString()} ${tx.currency}`;
                const route = `${tx.departurePort} → ${tx.arrivalPort}`;

                row.innerHTML = `
                    <td>${transactionDate}</td>
                    <td>${tx.type}</td>
                    <td>${route}</td>
                    <td>${tx.itemName}</td>
                    <td>${tx.partnerName || 'N/A'}</td>
                    <td>${price}</td>
                    <td>${tx.status}</td>
                `;
            });
        } catch (error) {
            console.error(error);
            tableBody.innerHTML = `<tr><td colspan="7" style="text-align:center; color: red; padding: 40px 0;">${error.message}</td></tr>`;
        }
    }

    function showSummaryModal() {
        const rows = tableBody.querySelectorAll('tr');
        if (rows.length === 0 || (rows.length === 1 && rows[0].querySelector('td[colspan="7"]'))) {
            alert('계산할 데이터가 없습니다.');
            return;
        }

        const activeFilter = filterGroup.querySelector('.is-active').dataset.filter;
        let totalKRW = 0;
        let totalUSD = 0;
        let detailsHtml = '<table class="details-table" style="width:100%; border-collapse: collapse;"><thead><tr style="border-bottom: 1px solid #dee2e6;"><th style="padding: 8px; text-align: left;">품명</th><th style="padding: 8px; text-align: left;">경로</th><th style="padding: 8px; text-align: right;">금액</th></tr></thead><tbody>';

        rows.forEach(row => {
            const cells = row.querySelectorAll('td');
            const type = cells[1].textContent;
            const route = cells[2].textContent;
            const itemName = cells[3].textContent;
            const priceText = cells[5].textContent;
            
            const [amountStr, currency] = priceText.split(' ');
            const amount = parseFloat(amountStr.replace(/,/g, ''));
            let displayAmount = amount;
            
            if ((activeFilter === 'all' && type === '구매') || activeFilter === 'purchase') {
                displayAmount = -amount;
            }

            if (currency === 'KRW') totalKRW += displayAmount;
            if (currency === 'USD') totalUSD += displayAmount;
            
            detailsHtml += `<tr style="border-bottom: 1px solid #f1f3f5;"><td style="padding: 8px;">${itemName}</td><td style="padding: 8px;">${route}</td><td style="padding: 8px; text-align: right;">${displayAmount.toLocaleString()} ${currency}</td></tr>`;
        });

        detailsHtml += '</tbody></table>';

        const totalColorKRW = totalKRW >= 0 ? 'color: red;' : 'color: blue;';
        const totalColorUSD = totalUSD >= 0 ? 'color: red;' : 'color: blue;';
        
        let summaryHtml = '<hr style="margin: 20px 0;"><h4 style="text-align: right;">합계</h4>';
        if (Math.abs(totalKRW) > 0) summaryHtml += `<p style="text-align: right; font-size: 1.2em; font-weight: bold; ${totalColorKRW}">${totalKRW.toLocaleString()} KRW</p>`;
        if (Math.abs(totalUSD) > 0) summaryHtml += `<p style="text-align: right; font-size: 1.2em; font-weight: bold; ${totalColorUSD}">${totalUSD.toLocaleString()} USD</p>`;
        
        summaryModalTitle.textContent = `거래 합계 (${startDateInput.value} ~ ${endDateInput.value})`;
        summaryModalBody.innerHTML = detailsHtml + summaryHtml;
        summaryModal.style.display = 'flex';
    }

    // 4. 이벤트 리스너 설정
    filterGroup.addEventListener('click', (e) => {
        if (e.target.tagName !== 'BUTTON' || e.target.classList.contains('is-active')) return;
        filterGroup.querySelector('.is-active').classList.remove('is-active');
        e.target.classList.add('is-active');
        fetchAndRenderTransactions();
    });

    searchForm.addEventListener('submit', (e) => {
        e.preventDefault();
        fetchAndRenderTransactions();
    });
    
    // ★★★ 핵심 수정 1: 날짜 변경 시, 버튼 상태 변경 함수 호출을 제거하고 조회만 실행 ★★★
    startDateInput.addEventListener('change', fetchAndRenderTransactions);
    endDateInput.addEventListener('change', fetchAndRenderTransactions);

    // ★★★ 핵심 수정 2: '금액 계산' 버튼 클릭 시, 날짜 선택 여부를 확인하여 분기 처리 ★★★
    calcButton.addEventListener('click', () => {
        if (!startDateInput.value || !endDateInput.value) {
            alert('날짜를 지정한 뒤에 계산할 수 있습니다.');
            return; // 날짜가 없으면 여기서 함수 종료
        }
        // 날짜가 있으면 계산 함수 실행
        showSummaryModal();
    });

    summaryModal.querySelector('.btn-close').addEventListener('click', () => summaryModal.style.display = 'none');
    summaryModal.querySelector('.btn-cancel').addEventListener('click', () => summaryModal.style.display = 'none');

    // 5. 페이지 첫 로딩 시 실행 (불필요한 updateCalcButtonState 함수 호출 제거)
	// 5. 페이지 첫 로딩 시 실행
	setDefaultDates(); // [추가] 기본 날짜 설정 함수를 먼저 호출
	fetchAndRenderTransactions(); // 그 다음, 설정된 날짜로 데이터를 조회
});