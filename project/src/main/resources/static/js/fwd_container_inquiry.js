document.addEventListener('DOMContentLoaded', () => {
    // 페이지의 주요 요소들을 미리 찾아둡니다.
    const containerList = document.querySelector('.container-list');
    const registerDocModal = document.getElementById('register-doc-modal');
    const addContainerModal = document.getElementById('add-container-modal');
    const openAddModalBtn = document.getElementById('btn-open-add-modal');
    const detailsTableTemplate = document.getElementById('details-table-template');

    if (!containerList || !registerDocModal || !addContainerModal || !openAddModalBtn || !detailsTableTemplate) {
        console.error('페이지에 필수 HTML 요소 중 일부가 없습니다.');
        return;
    }

    // --- 1. 컨테이너 추가 모달 로직 ---
    const addForm = document.getElementById('add-container-form');
    const openAddModal = () => addContainerModal.style.display = 'flex';
    const closeAddModal = () => {
        addForm.reset();
        addContainerModal.style.display = 'none';
    };

    openAddModalBtn.addEventListener('click', openAddModal);
    addContainerModal.querySelector('.btn-close').addEventListener('click', closeAddModal);
    addContainerModal.querySelector('.btn-cancel').addEventListener('click', closeAddModal);

    addForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const data = {
            departurePort: document.getElementById('departure-port').value,
            arrivalPort: document.getElementById('arrival-port').value,
            etd: document.getElementById('etd').value,
            eta: document.getElementById('eta').value,
            size: document.getElementById('container-size').value
        };
        try {
            const response = await fetch('/api/fwd/containers', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            const message = await response.text();
            alert(message);
            if (response.ok) location.reload();
        } catch (error) {
            alert('컨테이너 등록 중 오류가 발생했습니다.');
        }
    });

    // --- 2. 서류 등록 모달 로직 ---
    const registerForm = document.getElementById('register-doc-form');
    const openRegisterModal = (cardElement) => {
        const containerId = cardElement.querySelector('.info-group:first-child .value').textContent;
        const route = cardElement.querySelector('.route .value').textContent;
        registerDocModal.querySelector('#modal-container-id').value = containerId;
        registerDocModal.querySelector('#modal-container-info').textContent = containerId;
        registerDocModal.querySelector('#modal-container-route').textContent = route;
        registerDocModal.style.display = 'flex';
    };
    const closeRegisterModal = () => {
        registerForm.reset();
        registerDocModal.style.display = 'none';
    };
    registerDocModal.querySelector('.btn-close').addEventListener('click', closeRegisterModal);
    registerDocModal.querySelector('.btn-cancel').addEventListener('click', closeRegisterModal);
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const data = {
            containerId: registerDocModal.querySelector('#modal-container-id').value,
            cargoName: registerDocModal.querySelector('#modal-item-name').value,
            cbm: parseFloat(registerDocModal.querySelector('#modal-cbm').value),
            price: registerDocModal.querySelector('#modal-price').value,
            currency: registerDocModal.querySelector('#modal-currency').value
        };
        try {
            const response = await fetch('/api/fwd/external-cargo', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data)
            });
            const message = await response.text();
            alert(message);
            if (response.ok) location.reload();
        } catch (error) {
            alert('외부 화물 등록 중 오류가 발생했습니다.');
        }
    });

    // --- 3. 컨테이너 카드 내부의 모든 버튼 클릭 처리 (이벤트 위임) ---
    containerList.addEventListener('click', async (e) => {
        const target = e.target;
        const card = target.closest('.container-card');
        if (!card) return;

        const containerId = card.querySelector('.info-group:first-child .value')?.textContent;

        const fetchApi = async (url, method, actionName) => {
            try {
                const response = await fetch(url, { method });
                const message = await response.text();
                alert(message);
                if (response.ok) location.reload();
            } catch (error) {
                alert(`${actionName} 처리 중 오류가 발생했습니다.`);
            }
        };

        // '서류 등록' 버튼
        if (target.matches('.btn-register-docs')) {
            openRegisterModal(card);
        }
        // '추가입찰' 버튼
        else if (target.matches('.cbm-item.available .btn-primary')) {
            const routeValue = card.querySelector('.route .value').textContent;
            if (routeValue) {
                const [departure, arrival] = routeValue.split(' → ');
                window.location.href = `/fwd/fwdRequest?departure=${encodeURIComponent(departure.trim())}&arrival=${encodeURIComponent(arrival.trim())}`;
            }
        }
        // '컨테이너 삭제' 버튼
        else if (target.matches('.btn-delete-container')) {
            if (confirm(`컨테이너(${containerId})를 정말로 삭제하시겠습니까?`)) {
                fetchApi(`/api/fwd/containers/${containerId}`, 'DELETE', '삭제');
            }
        }
        // '컨테이너 확정' 버튼
        else if (target.matches('.btn-confirm-container')) {
            if (confirm(`컨테이너(${containerId})를 확정하시겠습니까?`)) {
                fetchApi(`/api/fwd/containers/${containerId}/confirm`, 'POST', '확정');
            }
        }
        // '선적완료' 버튼
        else if (target.matches('.btn-ship-container')) {
            if (confirm(`컨테이너(${containerId})를 '선적완료' 상태로 변경하시겠습니까?`)) {
                fetchApi(`/api/fwd/containers/${containerId}/ship`, 'POST', '선적완료');
            }
        }
        // '운송완료' 버튼
        else if (target.matches('.btn-complete-shipment')) {
            if (confirm(`컨테이너(${containerId})를 '운송완료' 상태로 변경하시겠습니까?`)) {
                fetchApi(`/api/fwd/containers/${containerId}/complete`, 'POST', '운송완료');
            }
        }
        // '정산' 버튼 (상세보기 내부)
		else if (target.matches('.btn-settle-container')) {
		    if (confirm(`컨테이너(${containerId})를 '정산완료' 처리하시겠습니까?\n이후 목록에서 사라지며 거래내역에서 확인할 수 있습니다.`)) {
		        fetchApi(`/api/fwd/containers/${containerId}/settle`, 'POST', '정산완료');
		    }
        }
		
		// 외부서류삭제버튼
		else if (target.matches('.btn-delete-external')) {
		    const cargoId = target.dataset.cargoId;
		    if (confirm(`외부 등록 화물(ID: ${cargoId})을 정말로 삭제하시겠습니까?`)) {
		        fetchApi(`/api/fwd/external-cargo/${cargoId}`, 'DELETE', '삭제');
		    }
		}
		
		// [✅ 추가] '마켓에 올리기' 버튼 (상세보기 내부)
		else if (target.matches('.btn-resale-from-details')) {
		    const offerId = target.dataset.offerId;
		    if (confirm(`제안(ID: ${offerId})을 재판매 목록에 등록하시겠습니까?`)) {
		        fetchApi(`/api/fwd/resale/${offerId}`, 'POST', '재판매 등록');
		    }
		}
		// [✅ 추가] '재판매 취소' 버튼 (상세보기 내부)
		else if (target.matches('.btn-cancel-resale')) {
		    const requestId = target.dataset.requestId;
		    if (confirm(`이 재판매 요청(ID: ${requestId})을 취소하시겠습니까?`)) {
		        fetchApi(`/api/fwd/resale-request/${requestId}`, 'DELETE', '재판매 취소');
		    }
		}
        // [✅ 핵심 수정] '상세보기' 버튼 로직을 else if로 묶어 중복 실행 방지
        else if (target.matches('.cbm-item .btn-secondary')) {
            const detailsButton = target;
            const tableContainer = card.querySelector('.details-table-container');
            const title = detailsButton.closest('.cbm-item').querySelector('.title').textContent;
            let status = '';
            if (title.includes('확정')) status = 'ACCEPTED';
            else if (title.includes('재판매')) status = 'FOR_SALE';
            else if (title.includes('입찰중')) status = 'PENDING';
            else return;

            if (detailsButton.classList.contains('is-active')) {
                tableContainer.innerHTML = '';
                detailsButton.classList.remove('is-active');
                return;
            }

            card.querySelectorAll('.btn-secondary.is-active').forEach(btn => btn.classList.remove('is-active'));
            tableContainer.innerHTML = '<p class="loading-message">상세 내역을 불러오는 중...</p>';

            try {
                const response = await fetch(`/api/fwd/details/${containerId}/${status}`);
                if (!response.ok) throw new Error(`서버 오류: ${response.statusText}`);
                const detailsData = await response.json();
                
                tableContainer.innerHTML = '';
                if (detailsData.length === 0) {
                    tableContainer.innerHTML = '<p class="no-details">표시할 상세 내역이 없습니다.</p>';
                } else {
                    const tableClone = detailsTableTemplate.content.cloneNode(true);
                    const tableBody = tableClone.querySelector('tbody');

                    detailsData.forEach(item => {
                        const row = tableBody.insertRow();
                        const freightText = item.freightCost ? `${item.freightCost.toLocaleString()} ${item.freightCurrency}` : '-';
                        const deadlineText = item.deadline ? new Date(item.deadline).toLocaleString('ko-KR') : '-';
                        let manageButtonHtml = '';
                        if (item.external) {
						    manageButtonHtml = `<button class="btn btn-xs btn-danger-outline btn-delete-external" data-cargo-id="${item.offerId}">삭제</button>`;
						} else if (item.status === 'ACCEPTED') {
						    const isResalePossible = item.deadline && new Date() < new Date(item.deadline);
						    manageButtonHtml = isResalePossible 
						        ? `<button class="btn btn-xs btn-outline btn-resale-from-details" data-offer-id="${item.offerId}">마켓에 올리기</button>` 
						        : `<button class="btn btn-xs" disabled>재판매 불가</button>`;
						} else if (item.status === 'FOR_SALE') {
						    manageButtonHtml = `<button class="btn btn-xs btn-danger-outline btn-cancel-resale" data-request-id="${item.resaleRequestId}">재판매 취소</button>`;
						}
                        const cbmText = (item.cbm !== null && item.cbm !== undefined) ? item.cbm.toFixed(2) : 'N/A';
						row.innerHTML = `
						    <td>${item.external ? '외부-' : ''}${item.offerId}</td>
						    <td>${item.itemName}</td>
						    <td>${cbmText}</td>
						    <td>${freightText}</td>
						    <td>${deadlineText}</td>
						    <td>${manageButtonHtml}</td>
						`;
                    });
                    tableContainer.appendChild(tableClone);
                }
                detailsButton.classList.add('is-active');
            } catch (error) {
                console.error('상세보기 데이터 로드 실패:', error);
                tableContainer.innerHTML = `<p class="no-details" style="color: red;">데이터를 불러오는 중 오류가 발생했습니다.</p>`;
            }
        }
		
		// 확정/운송완료 카드에 있는 '상세보기' 버튼 클릭 시
		 else if (target.matches('.btn-view-finalized-details')) {
		     const detailsButton = target;
		     const containerId = detailsButton.dataset.containerId;
		     const tableContainer = card.querySelector('.details-table-container');

		     // 이미 열려있으면 닫기
		     if (detailsButton.classList.contains('is-active')) {
		         tableContainer.innerHTML = '';
		         detailsButton.classList.remove('is-active');
		         return;
		     }

		     // 다른 열려있는 상세보기가 있다면 모두 닫기
		     document.querySelectorAll('.btn-secondary.is-active, .btn-view-finalized-details.is-active').forEach(btn => {
		         btn.classList.remove('is-active');
		         const otherCard = btn.closest('.container-card');
		         if (otherCard) {
		             otherCard.querySelector('.details-table-container').innerHTML = '';
		         }
		     });

		     tableContainer.innerHTML = '<p class="loading-message">상세 내역을 불러오는 중...</p>';

		     try {
		         // 확정된 모든 화물 정보를 가져오기 위해 'CONFIRMED' 상태로 API 호출
				 // [수정] 컨테이너의 실제 상태(CONFIRMED, SHIPPED 등)를 가져와 API를 호출
				 const containerStatus = card.querySelector('.header-actions .btn')?.textContent.trim() === '선적완료' ? 'SHIPPED'
				                       : card.querySelector('.header-actions .btn')?.textContent.trim() === '운송완료' ? 'COMPLETED'
				                       : 'CONFIRMED';
				 const response = await fetch(`/api/fwd/details/${containerId}/${containerStatus}`);
		         if (!response.ok) throw new Error(`서버 오류: ${response.statusText}`);
		         
		         const detailsData = await response.json();
		         
		         tableContainer.innerHTML = '';
		         if (detailsData.length === 0) {
		             tableContainer.innerHTML = '<p class="no-details">표시할 상세 내역이 없습니다.</p>';
		         } else {
		             const tableClone = detailsTableTemplate.content.cloneNode(true);
		             const tableBody = tableClone.querySelector('tbody');

		             detailsData.forEach(item => {
		                 const row = tableBody.insertRow();
		                 const freightText = item.freightCost ? `${item.freightCost.toLocaleString()} ${item.freightCurrency}` : '-';
		                 const deadlineText = item.deadline ? new Date(item.deadline).toLocaleString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-';
		                 const idText = item.external ? `외부-${item.offerId}` : item.offerId;

		                 row.innerHTML = `
		                     <td>${idText}</td>
		                     <td>${item.itemName}</td>
		                     <td>${item.cbm.toFixed(2)}</td>
		                     <td>${freightText}</td>
		                     <td>${deadlineText}</td>
		                     <td>${item.external ? '<button class="btn btn-xs btn-danger-outline btn-delete-external" data-cargo-id="' + item.offerId + '">삭제</button>' : '-'}</td>
		                 `;
		             });
		             tableContainer.appendChild(tableClone);
		         }
		         detailsButton.classList.add('is-active');

		     } catch (error) {
		         console.error('상세보기 데이터 로드 실패:', error);
		         tableContainer.innerHTML = `<p class="no-details" style="color: red;">데이터를 불러오는 중 오류가 발생했습니다.</p>`;
		     }
			 }
    });
});