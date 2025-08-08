// [✅ 이 코드로 CUS_request.js 파일 전체를 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    
    // --- 1. 신규 요청 모달 관련 로직 ---
    const modal = document.getElementById('request-modal');
    const openModalBtn = document.getElementById('open-request-modal');
    
    if (modal && openModalBtn) {
        const closeModalBtn = modal.querySelector('.btn-close');
        const cancelBtn = modal.querySelector('.btn-cancel');
        const requestForm = document.getElementById('new-request-form');

        const openModal = () => modal.style.display = 'flex';
        const closeModal = () => {
            requestForm.reset();
            modal.style.display = 'none';
        };

        openModalBtn.addEventListener('click', openModal);
        closeModalBtn.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);

		// [✅ 수정] 출발/도착항 선택 연동 로직
		const departurePortSelect = document.getElementById('departurePort');
		const arrivalPortSelect = document.getElementById('arrivalPort');

        const portGroups = {
            '인천': 'KR', '부산': 'KR',
            '도쿄': 'JP', '오사카': 'JP',
            '상해': 'CN',
            '싱가포르': 'SG'
        };

		const updatePortOptions = () => {
            const selectedDeparture = departurePortSelect.value;
            const selectedArrival = arrivalPortSelect.value;
            const departureGroup = selectedDeparture ? portGroups[selectedDeparture] : null;
            const arrivalGroup = selectedArrival ? portGroups[selectedArrival] : null;

		    // 도착항의 모든 옵션을 순회하며 출발항에서 선택된 값을 비활성화
		    for (const option of arrivalPortSelect.options) {
                if (option.value) {
                    const optionGroup = portGroups[option.value];
                    option.disabled = (departureGroup && optionGroup === departureGroup);
                }
		    }

		    // 출발항의 모든 옵션을 순회하며 도착항에서 선택된 값을 비활성화
		    for (const option of departurePortSelect.options) {
                if (option.value) {
                    const optionGroup = portGroups[option.value];
                    option.disabled = (arrivalGroup && optionGroup === arrivalGroup);
                }
		    }
		};

		// 각 드롭다운 메뉴에 변경 이벤트가 발생할 때마다 함수를 호출
		departurePortSelect.addEventListener('change', updatePortOptions);
		arrivalPortSelect.addEventListener('change', updatePortOptions);
		
		
        requestForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            
            const formData = {
                itemName: document.getElementById('itemName').value,
                incoterms: document.getElementById('incoterms').value,
                totalCbm: parseFloat(document.getElementById('totalCbm').value),
                departurePort: document.getElementById('departurePort').value,
                arrivalPort: document.getElementById('arrivalPort').value,
                deadline: document.getElementById('deadline').value,
				desiredArrivalDate: document.getElementById('desiredArrivalDate').value,
                tradeType: document.getElementById('tradeType').value,
                transportType: document.getElementById('transportType').value,
            };

            try {
                const response = await fetch('/api/cus/requests', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(formData)
                });

                const message = await response.text();
                alert(message);

                if (response.ok) {
                    closeModal();
                    location.reload();
                }
            } catch (error) {
                console.error('Error:', error);
                alert('요청 중 오류가 발생했습니다.');
            }
        });
    }

	    // --- 2. [핵심] 제안 보기 및 확정 관련 로직 ---
	    const viewContainer = document.getElementById('view-container');
	    const offerTemplate = document.getElementById('offer-list-template');
	    
	    if (!viewContainer || !offerTemplate) {
	        console.error("필수 요소를 찾을 수 없습니다: #view-container 또는 #offer-list-template");
	        return;
	    }

	    // 이벤트 위임을 사용하여 목록 전체에 대한 클릭 이벤트를 한 번만 등록합니다.
	    viewContainer.addEventListener('click', async (e) => {
	        const target = e.target;

	        // '제안 보기' 버튼(.btn-details)을 클릭했을 때만 동작합니다.
	        if (target.classList.contains('btn-details')) {
	            const requestCard = target.closest('.request-card');
	            const itemContainer = requestCard.closest('.request-item-container');
	            const detailsContainer = itemContainer.querySelector('.offer-details-container');
	            const requestId = requestCard.dataset.requestId;

	            if (!itemContainer || !detailsContainer) return;

	            // 이미 열려있다면 닫습니다.
	            if (itemContainer.classList.contains('is-expanded')) {
	                detailsContainer.innerHTML = '';
	                itemContainer.classList.remove('is-expanded');
	                return;
	            }
	            
	            // 다른 열려있는 제안 목록이 있다면 모두 닫습니다.
	            document.querySelectorAll('.request-item-container.is-expanded').forEach(openItem => {
	                openItem.classList.remove('is-expanded');
	                const openDetails = openItem.querySelector('.offer-details-container');
	                if (openDetails) openDetails.innerHTML = '';
	            });

	            try {
	                detailsContainer.innerHTML = `<p class="loading-message" style="text-align:center; padding: 2rem;">제안 목록을 불러오는 중...</p>`;
	                itemContainer.classList.add('is-expanded');

	                // API를 호출하여 해당 요청에 대한 제안 목록을 가져옵니다.
	                const response = await fetch(`/api/cus/requests/${requestId}/offers`);
	                if (!response.ok) throw new Error('제안 정보를 불러오지 못했습니다.');
	                
	                const offers = await response.json();
	                const templateClone = offerTemplate.content.cloneNode(true);
	                const tableBody = templateClone.querySelector('tbody');

	                if (offers.length === 0) {
	                    tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;">도착한 제안이 없습니다.</td></tr>';
	                } else {
	                    offers.forEach(offer => {
	                        const row = tableBody.insertRow();
	                        row.innerHTML = `
	                            <td>${offer.forwarderCompanyName}</td>
	                            <td>${offer.containerId}</td>
	                            <td>${offer.price.toLocaleString()} ${offer.currency}</td>
	                            <td>${offer.etd}</td>
	                            <td>${offer.eta}</td>
	                            <td><button class="btn btn-sm btn-success btn-confirm-offer" data-offer-id="${offer.offerId}">확정</button></td>
	                        `;
	                    });
	                }
	                detailsContainer.innerHTML = '';
	                detailsContainer.appendChild(templateClone);

	            } catch (error) {
	                console.error("Error fetching offers:", error);
	                detailsContainer.innerHTML = `<p class="error-message" style="padding: 1rem; text-align: center; color: red;">${error.message}</p>`;
	            }
	        }

	        // '확정' 버튼 클릭 처리
	        if (target.classList.contains('btn-confirm-offer')) {
	            const requestCard = target.closest('.request-item-container').querySelector('.request-card');
	            const requestId = requestCard.dataset.requestId;
	            const winningOfferId = target.dataset.offerId;

	            if (confirm('이 제안을 최종 확정하시겠습니까?')) {
	                try {
	                    const response = await fetch(`/api/cus/requests/${requestId}/confirm`, {
	                        method: 'POST',
	                        headers: { 'Content-Type': 'application/json' },
	                        body: JSON.stringify({ winningOfferId: winningOfferId })
	                    });
	                    const message = await response.text();
	                    alert(message);
	                    if (response.ok) {
	                        window.location.reload();
	                    }
	                } catch (error) {
	                    alert("확정 처리 중 오류가 발생했습니다.");
	                }
	            }
	        }
	    });
	});