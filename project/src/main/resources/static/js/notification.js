// [✅ /static/js/notification.js 파일 전체를 이 최종 코드로 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    const notificationArea = document.getElementById('notification-area');
    if (!notificationArea) return;

    const countElement = document.getElementById('notification-count');
    const listElement = document.getElementById('notification-list');
    const dropdown = document.getElementById('notification-dropdown');
    const notiBtn = notificationArea.querySelector('.notification-btn');
    const markAllReadBtn = document.getElementById('mark-all-read-btn');

    let retryCount = 0;
    const maxRetries = 5;
    let throttleTimer = null;

    // 쓰로틀링 함수: 과도한 호출을 방지합니다.
    const throttle = (callback, delay) => {
        return function () {
            if (!throttleTimer) {
                throttleTimer = setTimeout(() => {
                    callback();
                    throttleTimer = null;
                }, delay);
            }
        };
    };

    const updateCountUI = (count) => {
        const numericCount = parseInt(count, 10);
        countElement.textContent = numericCount;
        if (numericCount > 0) {
            countElement.style.display = 'flex';
        } else {
            countElement.style.display = 'none';
            // 알림 목록이 열려있을 때만 메시지 표시 함수 호출
            if (dropdown.style.display === 'block') {
                showNoNotificationMessage();
            }
        }
    };

    const addNotificationToList = (noti) => {
        const noNotiMsg = listElement.querySelector('.no-notifications');
        if (noNotiMsg) noNotiMsg.remove();
        const li = document.createElement('li');
        li.dataset.id = noti.id;
        li.dataset.url = noti.url;
        li.innerHTML = `<div class="message">${noti.message}</div><div class="timestamp">${noti.createdAt}</div>`;
        listElement.prepend(li);
    };

    const showNoNotificationMessage = () => {
        listElement.innerHTML = '<li class="no-notifications">새로운 알림이 없습니다.</li>';
    };

    const initializeNotifications = async () => {
        if (window.sseConnected) {
            console.log("SSE is already connected.");
            return;
        }
        window.sseConnected = true;

        try {
            const response = await fetch('/api/notifications');
            if (!response.ok) throw new Error('알림 목록 로딩 실패');
            const notifications = await response.json();
            
            listElement.innerHTML = '';
            if (notifications.length === 0) {
                showNoNotificationMessage();
            } else {
                notifications.forEach(addNotificationToList);
            }
            updateCountUI(notifications.length);

        } catch (error) {
            console.error(error);
            listElement.innerHTML = '<li class="no-notifications">알림을 불러올 수 없습니다.</li>';
        }

        const eventSource = new EventSource('/api/notifications/subscribe');

        eventSource.onopen = () => {
            console.log("SSE connection established. Resetting retry count.");
            retryCount = 0;
        };

        // --- SSE 이벤트 중앙 처리 ---

        // 1. 읽지 않은 알림 개수 업데이트
        eventSource.addEventListener('unreadCount', (event) => {
            updateCountUI(event.data);
        });

        // 2. 새로운 텍스트 알림 수신
        eventSource.addEventListener('notification', async (event) => {
             try {
                const response = await fetch('/api/notifications');
                const notifications = await response.json();
                listElement.innerHTML = '';
                if (notifications.length > 0) {
                    notifications.forEach(addNotificationToList);
                } else {
                    showNoNotificationMessage();
                }
             } catch (error) {
                 console.error("새 알림 수신 후 목록 갱신 실패:", error);
             }
        });

        // 3. 읽지 않은 채팅 메시지 수신
        eventSource.addEventListener('unreadChat', (event) => {
            document.dispatchEvent(new CustomEvent('sse:unreadChat', { detail: event.data }));
        });

        // 4. 신규 견적 요청 실시간 업데이트
        eventSource.addEventListener('new_request', (event) => {
            const requestList = document.querySelector('.request-list');
            if (!requestList) return;

            const noResultMessage = requestList.querySelector('.no-results-message');
            if (noResultMessage) noResultMessage.remove();
            
            const newRequest = JSON.parse(event.data);
            const newCardHtml = `
                <article class="card request-card" data-request-id="${newRequest.id}" data-request-cbm="${newRequest.cbm}" data-requester-id="${newRequest.requesterId}" data-has-my-offer="${newRequest.hasMyOffer}" data-deadline-datetime="${newRequest.deadlineDateTime}" data-desired-arrival-date="${newRequest.desiredArrivalDateAsLocalDate}">
                    <div class="info"><span class="id-label">${newRequest.idLabel}</span><h3 class="item-name">${newRequest.itemName}</h3><div class="details"><span class="incoterms">${newRequest.incoterms}</span> <span class="port departure">${newRequest.departurePort}</span> <span class="arrow">→</span> <span class="port arrival">${newRequest.arrivalPort}</span> <span class="desired-arrival" style="font-weight: 500; color: #007bff; margin-left: 12px;"> 도착희망: ${newRequest.desiredArrivalDate}</span> <span class="date-info" style="margin-left: 8px;">등록: ${newRequest.registrationDate}</span> <span class="deadline" style="margin-left: 8px;">  마감: ${newRequest.deadline}</span></div></div>
                    <div class="meta"><div class="type"><p class="trade-type">${newRequest.tradeType}</p><p class="transport-type">${newRequest.transportType}</p></div><div class="cbm">${newRequest.cbm.toFixed(2)} CBM</div></div>
                    <div class="actions"><button class="btn btn-timer btn-danger" data-deadline-datetime="${newRequest.deadlineDateTime}"></button><button class="btn btn-quote btn-primary">견적제안</button></div>
                </article>`;
            requestList.insertAdjacentHTML('afterbegin', newCardHtml);
            
            if (typeof window.updateAllTimers === 'function') {
                window.updateAllTimers();
            }
        });

        // 5. 운송 상태 실시간 업데이트
        eventSource.addEventListener('shipment_update', (event) => {
            const { requestId, detailedStatus } = JSON.parse(event.data);
            const articleElement = document.querySelector(`article[data-request-id='${requestId}']`);
            if (!articleElement) return;

            const progressTracker = articleElement.querySelector('.progress-tracker-large');
            if (!progressTracker) return;

            const statusMap = {
                'ACCEPTED': ['낙찰'], 'CONFIRMED': ['낙찰', '컨테이너 확정'], 'SHIPPED': ['낙찰', '컨테이너 확정', '선적완료'],
                'COMPLETED': ['낙찰', '컨테이너 확정', '선적완료', '운송완료'], 'RESOLD': ['낙찰']
            };
            const stepsToComplete = statusMap[detailedStatus] || [];
            progressTracker.querySelectorAll('.step').forEach(step => {
                const label = step.querySelector('.label').textContent.trim();
                if (stepsToComplete.includes(label)) {
                    step.classList.add('is-complete');
                }
            });
        });
		
		// [✅ 여기부터 새로운 기능 추가]
		// 6. 포워더 화면: 실시간 제안 상태(낙찰/유찰) 업데이트
		eventSource.addEventListener('offer_status_update', (event) => {
		    const { offerId, status, statusText } = JSON.parse(event.data);
		    
		    // '나의제안조회' 페이지에 해당 offerId를 가진 요소가 있는지 확인
		    const detailsButton = document.querySelector(`.btn-details[data-offer-id='${offerId}']`);
		    if (!detailsButton) return;

		    const offerCard = detailsButton.closest('.offer-card');
		    if (!offerCard) return;

		    // 상태 배지(Status Badge) 요소 찾기
		    const statusBadge = offerCard.querySelector('.status-badge');
		    if (statusBadge) {
		        // 텍스트 변경
		        statusBadge.textContent = statusText;
		        
		        // CSS 클래스 변경 (기존 상태 클래스는 모두 지우고 새 클래스 추가)
		        statusBadge.className = 'status-badge'; // 기본 클래스만 남기고 초기화
		        statusBadge.classList.add(status.toLowerCase()); // 새 상태 클래스 추가 (예: 'accepted')
		    }

		    // '재판매하기' 또는 '제안취소' 버튼 등 액션 버튼 영역 처리
		    const actionsContainer = offerCard.querySelector('.actions');
		    if (actionsContainer) {
		        // '수락' 상태가 되면 '재판매하기' 버튼을 보여주고, '제안취소' 버튼은 숨김
		        if (status === 'ACCEPTED') {
		            const resellButton = `<button class="btn btn-sm btn-resale" data-offer-id="${offerId}">재판매하기</button>`;
		            actionsContainer.innerHTML = resellButton + actionsContainer.innerHTML; // 상세보기 버튼 앞에 추가
		            
		            const cancelButton = actionsContainer.querySelector('.btn-cancel-offer');
		            if (cancelButton) cancelButton.remove();
		        } 
		        // '거절' 상태가 되면 모든 액션 버튼을 제거
		        else if (status === 'REJECTED') {
		            const resellButton = actionsContainer.querySelector('.btn-resale');
		            const cancelButton = actionsContainer.querySelector('.btn-cancel-offer');
		            if (resellButton) resellButton.remove();
		            if (cancelButton) cancelButton.remove();
		        }
		    }
		});
		
		// [✅ 여기부터 새로운 기능 추가]
		// 7. 화주 화면: 실시간 제안 건수 업데이트
		eventSource.addEventListener('bid_count_update', (event) => {
		    const { requestId, bidderCount } = JSON.parse(event.data);
		    
		    // 현재 페이지에 해당 requestId를 가진 요소가 있는지 확인
		    const articleElement = document.querySelector(`article[data-request-id='${requestId}']`);
		    if (!articleElement) return; // 없으면 아무것도 안 함

		    // 제안 건수를 표시하는 span 요소를 찾음
		    const bidderCountElement = articleElement.querySelector('.bidder-count');
		    if (bidderCountElement) {
		        // 텍스트를 새로운 건수로 변경
		        bidderCountElement.textContent = `제안 ${bidderCount}건 도착`;
		        
		        // (선택사항) 시각적 효과 추가
		        bidderCountElement.style.transition = 'transform 0.2s ease';
		        bidderCountElement.style.transform = 'scale(1.2)';
		        setTimeout(() => {
		            bidderCountElement.style.transform = 'scale(1)';
		        }, 200);
		    }
		});
		
		// 8. 관리자 대시보드: 실시간 지표 업데이트
		eventSource.addEventListener('dashboard_update', (event) => {
		    // 현재 페이지가 대시보드인지 확인 (고유한 ID를 가진 요소로 판단)
		    const dashboardCard = document.querySelector('.dashboard-grid');
		    if (!dashboardCard) return;

		    const metrics = JSON.parse(event.data);
		    
		    // 각 ID에 맞는 요소를 찾아 텍스트를 업데이트
		    document.getElementById('today-requests').textContent = metrics.todayRequests;
		    document.getElementById('today-deals').textContent = metrics.todayDeals;
		    document.getElementById('total-fwd-users').textContent = metrics.totalFwdUsers;
		    document.getElementById('total-cus-users').textContent = metrics.totalCusUsers;
		    document.getElementById('pending-users').textContent = metrics.pendingUsers;
		    document.getElementById('no-bid-requests').textContent = metrics.noBidRequests;
		    document.getElementById('missed-confirmation-rate').textContent = metrics.missedConfirmationRate.toFixed(2) + '%';
		});

        eventSource.onerror = (error) => {
            console.error('SSE Error Occurred:', error);
            retryCount++;
            if (retryCount >= maxRetries) {
                console.error('SSE maximum retry limit reached. Closing connection.');
                eventSource.close();
                window.sseConnected = false;
            }
        };
    };

    // --- 이하 UI 이벤트 리스너 (기존과 동일) ---
    notiBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        dropdown.style.display = dropdown.style.display === 'block' ? 'none' : 'block';
    });

    listElement.addEventListener('click', async (e) => {
        const li = e.target.closest('li[data-id]');
        if (!li) return;
        const id = li.dataset.id;
        const url = li.dataset.url;
        
        try {
            await fetch(`/api/notifications/${id}/read`, { method: 'POST' });
            if (url && url !== 'null') {
                window.location.href = url;
            }
        } catch (error) { 
            console.error("알림 읽음 처리 실패:", error);
        }
    });

    markAllReadBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        try {
            await fetch('/api/notifications/read/all', { method: 'POST' });
        } catch (error) { 
            console.error("모두 읽음 처리 실패:", error); 
        }
    });

    document.addEventListener('click', (e) => {
        if (!notificationArea.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });
    
    const throttledInit = throttle(initializeNotifications, 3000);
    throttledInit();
});