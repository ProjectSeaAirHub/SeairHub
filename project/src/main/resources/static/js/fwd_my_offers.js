document.addEventListener('DOMContentLoaded', () => {
    const offerList = document.querySelector('.offer-list');
    const detailsTemplate = document.getElementById('offer-details-template');
    const resaleModal = document.getElementById('resale-confirm-modal');

    if (!offerList || !detailsTemplate || !resaleModal) {
        console.error('페이지에 필수 HTML 요소가 없습니다.');
        return;
    }

    // --- 재판매 모달 관련 로직 ---
    const modalText = document.getElementById('modal-resale-text');
    const offerIdInput = document.getElementById('modal-offer-id-to-resale');
    const confirmBtn = document.getElementById('btn-confirm-resale');

    const openResaleModal = (offerId, requestIdLabel) => {
        modalText.innerHTML = `정말로 <strong>${requestIdLabel}</strong> 제안을<br>재판매 목록에 등록하시겠습니까?`;
        offerIdInput.value = offerId;
        resaleModal.style.display = 'flex';
    };

    const closeResaleModal = () => {
        resaleModal.style.display = 'none';
        offerIdInput.value = '';
    };

    resaleModal.querySelector('.btn-close').addEventListener('click', closeResaleModal);
    resaleModal.querySelector('.btn-cancel').addEventListener('click', closeResaleModal);

    confirmBtn.addEventListener('click', async () => {
        const offerId = offerIdInput.value;
        if (!offerId) return;
        try {
            const response = await fetch(`/api/fwd/resale/${offerId}`, { method: 'POST' });
            const message = await response.text();
            alert(message);
            if (response.ok) {
                location.reload();
            } else {
                closeResaleModal();
            }
        } catch (error) {
            console.error('Resale error:', error);
            alert('재판매 요청 중 오류가 발생했습니다.');
            closeResaleModal();
        }
    });


    // --- 상세보기, 재판매, 제안취소, 제안수정 버튼 클릭 통합 처리 ---
    offerList.addEventListener('click', async (e) => {
        const target = e.target;
        const offerCard = target.closest('.offer-card');
        if (!offerCard) return;

        // 1. '상세보기' 버튼 클릭 시
        if (target.classList.contains('btn-details')) {
            const detailsContainer = offerCard.querySelector('.card-details-container');
            const offerId = target.dataset.offerId;

            if (offerCard.classList.contains('is-expanded')) {
                detailsContainer.innerHTML = '';
                offerCard.classList.remove('is-expanded');
                return;
            }

            document.querySelectorAll('.offer-card.is-expanded').forEach(card => {
                card.classList.remove('is-expanded');
                card.querySelector('.card-details-container').innerHTML = '';
            });

            detailsContainer.innerHTML = '<p class="loading-message">상세 정보를 불러오는 중...</p>';

            try {
                const response = await fetch(`/api/fwd/my-offers/${offerId}`);
                if (!response.ok) throw new Error('상세 정보를 불러오는데 실패했습니다.');
                
                const data = await response.json();
                const detailsClone = detailsTemplate.content.cloneNode(true);
                
				// ▼▼▼ 기존 상세보기 로직 try-catch 블록 안에 이 코드를 추가해주세요 ▼▼▼
				const statusBadge = offerCard.querySelector('.status-badge');
				const offerStatusText = statusBadge ? statusBadge.textContent.trim() : '';
				
				// ▼▼▼ '화주' 관련 로직을 아래 코드로 교체해주세요 ▼▼▼
				const requesterCompanyEl = detailsClone.querySelector('[data-field-container="requesterCompanyName"]');
				if (data.requesterCompanyName) {
				    requesterCompanyEl.querySelector('[data-field="requesterCompanyName"]').textContent = data.requesterCompanyName;
				} else {
				    // 데이터가 없으면 화주 정보 div 자체를 숨깁니다.
				    requesterCompanyEl.style.display = 'none';
				}
				// ▲▲▲ 여기까지 교체 ▲▲▲

				detailsClone.querySelector('[data-field="itemName"]').textContent = data.itemName; // [✅ 이 줄을 추가해주세요]
                detailsClone.querySelector('[data-field="requesterCompanyName"]').textContent = data.requesterCompanyName || 'N/A';
                detailsClone.querySelector('[data-field="deadline"]').textContent = data.deadline;
                detailsClone.querySelector('[data-field="cbm"]').textContent = `${data.cbm.toFixed(2)} CBM`;
                detailsClone.querySelector('[data-field="containerId"]').textContent = data.containerId;


                const priceItemContainer = detailsClone.querySelector('.detail-item-price');
                
                if (offerStatusText === '진행중') {
                    const editButton = priceItemContainer.querySelector('.btn-edit-price');
                    priceItemContainer.querySelector('[data-field="myOfferPrice"]').textContent = `${data.myOfferPrice.toLocaleString()} ${data.myOfferCurrency}`;
                    editButton.style.display = 'inline-block';
                } else {
                    priceItemContainer.querySelector('[data-field="myOfferPrice"]').textContent = `${data.myOfferPrice.toLocaleString()} ${data.myOfferCurrency}`;
                    priceItemContainer.querySelector('.offer-actions').style.display = 'none';
                }

				
				// '거절' 상태일 때만 최종 낙찰 정보 표시
				if (offerStatusText === '거절') {
				    const finalBidInfoEl = detailsClone.querySelector('.final-bid-info');
				    const finalBidResultEl = finalBidInfoEl.querySelector('[data-field="finalBidResult"]');
				    
				    if (data.closedWithoutWinner) {
				        finalBidResultEl.textContent = '낙찰 없이 마감되었습니다.';
				        finalBidResultEl.style.color = '#6c757d'; // 회색 텍스트
				    } else {
				        finalBidResultEl.innerHTML = `<strong>운임:</strong> ${data.finalPrice.toLocaleString()} ${data.finalCurrency}`;
				        finalBidResultEl.style.color = '#28a745'; // 녹색 텍스트
				    }
				    finalBidInfoEl.style.display = 'block';
				}
				// ▲▲▲ 여기까지 추가 ▲▲▲
                detailsContainer.innerHTML = '';
                detailsContainer.appendChild(detailsClone);
                offerCard.classList.add('is-expanded');

            } catch (error) {
                console.error('Detail fetch error:', error);
                detailsContainer.innerHTML = '<p class="error-message">정보를 불러올 수 없습니다.</p>';
            }
        }

        // 2. '재판매하기' 버튼 클릭 시
        if (target.classList.contains('btn-resale')) {
            const offerId = target.dataset.offerId;
            const cardInfo = target.closest('.card-summary').querySelector('.request-id-label');
            const requestIdLabel = cardInfo ? cardInfo.textContent : `ID: ${offerId}`;
            openResaleModal(offerId, requestIdLabel);
        }

        // 3. '제안취소' 버튼 클릭 시
        if (target.classList.contains('btn-cancel-offer')) {
            const offerId = target.dataset.offerId;
            if (confirm(`정말로 이 제안(ID: ${offerId})을 취소하시겠습니까?`)) {
                try {
                    const response = await fetch(`/api/fwd/my-offers/${offerId}`, { method: 'DELETE' });
                    const message = await response.text();
                    alert(message);
                    if (response.ok) {
                        location.reload();
                    }
                } catch (error) {
                    console.error('Offer cancellation error:', error);
                    alert('제안 취소 중 오류가 발생했습니다.');
                }
            }
        }
        
        // 4. '수정' 버튼 클릭 시
        if (target.classList.contains('btn-edit-price')) {
            const priceItem = target.closest('.detail-item-price');
            const dataContainer = target.closest('.offer-card');
            const offerId = dataContainer.querySelector('.btn-details').dataset.offerId;

            // API를 한번 더 호출해서 최신 가격정보를 가져옴 (동기화)
            const response = await fetch(`/api/fwd/my-offers/${offerId}`);
            const data = await response.json();

            priceItem.querySelector('.price-display').style.display = 'none';
            target.style.display = 'none';

            const priceEdit = priceItem.querySelector('.price-edit');
            priceEdit.querySelector('.bid-price').value = data.myOfferPrice;
            priceEdit.querySelector('.currency-select').value = data.myOfferCurrency;

            priceEdit.style.display = 'block';
            priceItem.querySelector('.btn-update-offer').style.display = 'inline-block';
        }

        // 5. '저장' 버튼 클릭 시
        if (target.classList.contains('btn-update-offer')) {
            const offerId = offerCard.querySelector('.btn-details').dataset.offerId;
            const detailsContainer = offerCard.querySelector('.card-details-container');
            const newPrice = detailsContainer.querySelector('.bid-price').value;
            const newCurrency = detailsContainer.querySelector('.currency-select').value;

            if (!newPrice || newPrice <= 0) {
                alert('올바른 가격을 입력하세요.');
                return;
            }

            if (confirm('제안 가격을 수정하시겠습니까?')) {
                try {
                    const response = await fetch(`/api/fwd/my-offers/${offerId}`, {
                        method: 'PATCH',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ price: newPrice, currency: newCurrency })
                    });
                    const message = await response.text();
                    alert(message);
                    if (response.ok) {
                        location.reload();
                    }
                } catch (error) {
                    alert('제안 수정 중 오류가 발생했습니다.');
                }
            }
        }
    });
});