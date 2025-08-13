// /static/js/FWD_my_posted_requests.js

// [✅ 이 파일의 모든 내용을 아래 코드로 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    const viewContainer = document.getElementById('view-container');
    const biddersListTemplate = document.getElementById('bidders-list-template');

    if (!viewContainer || !biddersListTemplate) {
        console.error("페이지에 필수 HTML 요소가 없습니다: #view-container 또는 #bidders-list-template");
        return;
    }

    // 입찰 확정 API 호출 함수
	// FWD_my_posted_requests.js
	async function confirmBid(requestId, offerId) {
	    if (confirm(`이 제안을 최종 확정하시겠습니까?\n다른 모든 제안은 거절 처리됩니다.`)) {
	        try {
	            const response = await fetch(`/api/fwd/my-posted-requests/${requestId}/confirm`, {
	                method: 'POST',
	                headers: { 'Content-Type': 'application/json' },
	                // 여기를 수정합니다.
	                body: JSON.stringify({ winningOfferId: offerId })
	            });
	            const message = await response.text();
	            alert(message);
	            if (response.ok) {
	                window.location.reload();
	            }
	        } catch (error) {
	            console.error("Error confirming bid:", error);
	            alert("낙찰 처리 중 오류가 발생했습니다.");
	        }
	    }
	}

    // 이벤트 위임을 사용하여 전체 목록의 클릭 이벤트 처리
	viewContainer.addEventListener('click', async (e) => {
	    const target = e.target;

	    // '입찰 현황 보기' 버튼 클릭 처리
	    if (target.matches('.btn-details')) {
	        const card = target.closest('.request-card');
	        const itemContainer = card.closest('.request-item-container');
	        const detailsContainer = itemContainer.querySelector('.offer-details-container');
	        const requestId = card.dataset.requestId;

	        if (itemContainer.classList.contains('is-expanded')) {
	            detailsContainer.innerHTML = '';
	            itemContainer.classList.remove('is-expanded');
	            return;
	        }

	        document.querySelectorAll('.request-item-container.is-expanded').forEach(openItem => {
	            openItem.classList.remove('is-expanded');
	            openItem.querySelector('.offer-details-container').innerHTML = '';
	        });

	        try {
	            detailsContainer.innerHTML = '<p style="text-align:center; padding: 2rem;">입찰 현황을 불러오는 중...</p>';
	            itemContainer.classList.add('is-expanded');

	            const response = await fetch(`/api/fwd/my-posted-requests/${requestId}/bidders`);
	            if (!response.ok) throw new Error('입찰자 정보를 불러오지 못했습니다.');

	            const bidders = await response.json();
	            const listClone = biddersListTemplate.content.cloneNode(true);
	            const tableBody = listClone.querySelector('tbody');

	            if (bidders.length === 0) {
	                tableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;">아직 입찰자가 없습니다.</td></tr>';
	            } else {
	                bidders.forEach(bid => {
	                    const row = tableBody.insertRow();
	                    // [✅ 핵심 수정] bid.bidderCompanyName 대신 경로 정보를 표시합니다.
	                    row.innerHTML = `
	                        <td>${bid.departurePort} → ${bid.arrivalPort}</td>
	                        <td>${bid.containerId}</td>
	                        <td>${bid.price.toLocaleString()} ${bid.currency}</td>
	                        <td>${bid.etd}</td>
	                        <td>${bid.eta}</td>
	                        <td><button class="btn btn-sm btn-primary btn-confirm-bid" data-offer-id="${bid.offerId}">확정</button></td>
	                    `;
	                });
	            }
	            
	            detailsContainer.innerHTML = '';
	            detailsContainer.appendChild(listClone);

	        } catch (error) {
	            console.error("Error fetching bidders:", error);
	            detailsContainer.innerHTML = `<p class="error-message" style="text-align:center; color:red;">${error.message}</p>`;
	        }
	    }

	    // 동적으로 생성된 '확정' 버튼 클릭 처리 (기존과 동일)
	    if (target.matches('.btn-confirm-bid')) {
	        const requestId = target.closest('.request-item-container').querySelector('.request-card').dataset.requestId;
	        const offerId = target.dataset.offerId;
	        confirmBid(requestId, offerId);
	    }
	});
});