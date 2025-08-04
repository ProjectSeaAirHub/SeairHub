// [✅ 이 코드로 fwd-request-proposal.js 파일 전체를 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    const requestList = document.querySelector('.request-list');
    const offerFormTemplate = document.getElementById('offer-form-template');
    const currentUserId = document.querySelector('.container')?.dataset.currentUserId;

    if (!requestList || !offerFormTemplate) {
        console.error("필수 요소를 찾을 수 없습니다: .request-list 또는 #offer-form-template");
        return;
    }

    const closeOpenOfferForm = () => {
        const openForm = document.querySelector('.offer-form-expand');
        if (openForm) {
            const card = openForm.previousElementSibling;
            if (card?.classList.contains('is-expanded')) {
                card.classList.remove('is-expanded');
            }
            openForm.remove();
        }
    };

    requestList.addEventListener('click', async (e) => {
        const quoteButton = e.target.closest('.btn-quote');
        
        if (!quoteButton || quoteButton.disabled) {
            return;
        }

        const card = quoteButton.closest('.request-card');
        if (!card) return;
        
        const hasMyOffer = card.dataset.hasMyOffer === 'true';
        if (hasMyOffer) {
            return;
        }

        if (card.classList.contains('is-expanded')) {
            closeOpenOfferForm();
            return;
        }
        
        closeOpenOfferForm();
        
        const requestId = card.dataset.requestId;
        const requestCbm = parseFloat(card.dataset.requestCbm);
        const desiredArrivalDateString = card.dataset.desiredArrivalDate;

        try {
            card.classList.add('is-expanded');
            
            const response = await fetch(`/api/fwd/available-containers?requestId=${requestId}`);
            if (!response.ok) throw new Error('서버에서 컨테이너 목록을 가져오는데 실패했습니다.');
            
            const availableContainers = await response.json();
            const formClone = offerFormTemplate.content.cloneNode(true);
            const formWrapper = formClone.querySelector('.offer-form-expand');
            
            card.after(formWrapper);

            const containerSelect = formWrapper.querySelector('.container-select');
            const capacityInput = formWrapper.querySelector('.available-capacity');
            const priceInput = formWrapper.querySelector('.bid-price');
            const currencySelect = formWrapper.querySelector('.currency-select');
            const statusText = formWrapper.querySelector('.form-status');
            const submitBtn = formWrapper.querySelector('.btn-submit-bid');
            const cancelBtn = formWrapper.querySelector('.btn-cancel');

            if (availableContainers.length > 0) {
                availableContainers.forEach(c => {
                    const option = document.createElement('option');
                    option.value = c.containerId;
                    option.textContent = c.containerDisplayName;
                    option.dataset.availableCbm = c.availableCbm;
                    option.dataset.eta = c.eta;
                    containerSelect.appendChild(option);
                });
            } else {
                statusText.innerHTML = `<strong class="impossible">제안 가능한 컨테이너 없음</strong>`;
            }

            containerSelect.addEventListener('change', () => {
                const selectedOption = containerSelect.options[containerSelect.selectedIndex];
                if (!selectedOption.value) {
                    capacityInput.value = '컨테이너 선택 시 자동 입력';
                    statusText.textContent = '';
                    submitBtn.disabled = true;
                    return;
                }
                
                const availableCbm = parseFloat(selectedOption.dataset.availableCbm);
                const containerEtaString = selectedOption.dataset.eta;
                capacityInput.value = `${availableCbm.toFixed(2)} CBM`;

                const isCbmOk = availableCbm >= requestCbm;
                
                // ★★★ 핵심 수정: 날짜 차이를 계산하는 로직 ★★★
                let isDateOk = false;
                let reason = "도착 희망일이 지정되지 않은 요청입니다.";

                if (desiredArrivalDateString && desiredArrivalDateString !== 'null') {
                    const desiredArrival = new Date(desiredArrivalDateString);
                    const containerEta = new Date(containerEtaString);
                    
                    desiredArrival.setHours(0, 0, 0, 0);
                    containerEta.setHours(0, 0, 0, 0);

                    const timeDiff = containerEta.getTime() - desiredArrival.getTime();
                    const dayDiff = timeDiff / (1000 * 3600 * 24);

                    isDateOk = dayDiff <= 3;
                    reason = !isDateOk ? '도착 희망일보다 3일 이상 늦습니다.' : '';
                }

                if (isCbmOk && isDateOk) {
                    statusText.innerHTML = `<strong class="possible">견적제안 가능</strong>합니다.`;
                    submitBtn.disabled = false;
                } else {
                    let finalReason = !isCbmOk ? '잔여 용량이 부족합니다.' : reason;
                    statusText.innerHTML = `<strong class="impossible">제안 불가</strong> (${finalReason})`;
                    submitBtn.disabled = true;
                }
            });

            cancelBtn.addEventListener('click', closeOpenOfferForm);

            submitBtn.addEventListener('click', async () => {
                if (!priceInput.value || priceInput.value <= 0 || !containerSelect.value) {
                    alert('컨테이너 선택 및 입찰가를 정확히 입력해주세요.');
                    return;
                }
                const offerData = {
                    requestId: requestId,
                    containerId: containerSelect.value,
                    price: priceInput.value,
                    currency: currencySelect.value
                };
                try {
                    const submitResponse = await fetch('/api/fwd/offers', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(offerData)
                    });
                    const message = await submitResponse.text();
                    alert(message);
                    if (submitResponse.ok) {
                        window.location.href = '/fwd/my-offers';
                    }
                } catch (error) {
                    console.error('Submit error:', error);
                    alert('제안 제출 중 오류가 발생했습니다.');
                }
            });

        } catch (error) {
            console.error('Fetch error:', error);
            alert(error.message);
            if(card) card.classList.remove('is-expanded');
        }
    });
});