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
            if (dropdown.style.display === 'block') {
                showNoNotificationMessage();
            }
        }
    };

    /**
     * [✅ 핵심 수정 1] 알림 항목을 목록의 맨 위에 추가(prepend)하는 함수입니다.
     */
    const prependNotificationToList = (noti) => {
        const noNotiMsg = listElement.querySelector('.no-notifications');
        if (noNotiMsg) noNotiMsg.remove(); // '알림 없음' 메시지가 있다면 제거
        
        const li = document.createElement('li');
        li.dataset.id = noti.id;
        li.dataset.url = noti.url;
        li.innerHTML = `<div class="message">${noti.message}</div><div class="timestamp">${noti.createdAt}</div>`;
        
        listElement.prepend(li); // 새 알림을 목록의 맨 위에 추가
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
            
            const notifications = await response.json(); // 서버로부터 최신순으로 정렬된 목록을 받음
            
            listElement.innerHTML = '';
            if (notifications.length === 0) {
                showNoNotificationMessage();
            } else {
                /**
                 * [✅ 핵심 수정 2] 서버에서 받은 최신순 목록을 그대로 화면에 표시합니다.
                 * forEach와 appendChild를 사용하여 배열 순서 그대로 (최신순으로) li 요소를 추가합니다.
                 */
                notifications.forEach(noti => {
                    const li = document.createElement('li');
                    li.dataset.id = noti.id;
                    li.dataset.url = noti.url;
                    li.innerHTML = `<div class="message">${noti.message}</div><div class="timestamp">${noti.createdAt}</div>`;
                    listElement.appendChild(li); // 목록의 맨 뒤에 추가
                });
            }
            updateCountUI(notifications.length);

        } catch (error) {
            console.error(error);
            listElement.innerHTML = '<li class="no-notifications">알림을 불러올 수 없습니다.</li>';
        }

        const eventSource = new EventSource('/api/notifications/subscribe');
		
		let heartbeatTimer = null;
		const resetHeartbeatTimer = () => {
		    clearTimeout(heartbeatTimer);
		    // 40초 동안 아무 신호가 없으면 연결 문제를 경고하고 새로고침 유도
		    heartbeatTimer = setTimeout(() => {
		        console.error("SSE Heartbeat timeout. Connection may be lost.");
		        alert("서버와의 연결이 끊겼습니다. 페이지를 새로고침합니다.");
		        window.location.reload();
		        eventSource.close();
		    }, 40000); // 40초
		};

        eventSource.onopen = () => {
            console.log("SSE connection established. Resetting retry count.");
            retryCount = 0;
			resetHeartbeatTimer();
        };
		
		// 서버로부터 어떤 메시지든 받으면 타이머를 리셋
		eventSource.onmessage = (event) => {
		    resetHeartbeatTimer();
		};

        eventSource.addEventListener('unreadCount', (event) => {
            updateCountUI(event.data);
        });

        /**
         * [✅ 핵심 수정 3] 새로운 'notification' 이벤트 수신 시, 전체 목록을 다시 불러오는 대신
         * 전달받은 새 알림 데이터만 목록의 맨 위에 바로 추가하여 훨씬 효율적으로 동작합니다.
         */
        eventSource.addEventListener('notification', (event) => {
             try {
                const newNotification = JSON.parse(event.data);
                prependNotificationToList(newNotification); // 새 알림을 맨 위에 추가하는 함수 호출
             } catch (error) {
                 console.error("새 알림 처리 중 오류 발생:", error);
             }
        });

        // --- 이하 다른 SSE 이벤트 리스너들은 기존과 동일 ---
        eventSource.addEventListener('unreadChat', (event) => {
            document.dispatchEvent(new CustomEvent('sse:unreadChat', { detail: event.data }));
        });

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
		
		eventSource.addEventListener('offer_status_update', (event) => {
		    const { offerId, status, statusText } = JSON.parse(event.data);
		    const detailsButton = document.querySelector(`.btn-details[data-offer-id='${offerId}']`);
		    if (!detailsButton) return;

		    const offerCard = detailsButton.closest('.offer-card');
		    if (!offerCard) return;

		    const statusBadge = offerCard.querySelector('.status-badge');
		    if (statusBadge) {
		        statusBadge.textContent = statusText;
		        statusBadge.className = 'status-badge';
		        statusBadge.classList.add(status.toLowerCase());
		    }

		    const actionsContainer = offerCard.querySelector('.actions');
		    if (actionsContainer) {
		        const cancelButton = actionsContainer.querySelector('.btn-cancel-offer');
		        if (cancelButton) cancelButton.remove();

		        if (status === 'ACCEPTED') {
		            const resellButtonHTML = `<button class="btn btn-sm btn-resale" data-offer-id="${offerId}">재판매하기</button>`;
		            if (statusBadge) {
		                statusBadge.insertAdjacentHTML('afterend', resellButtonHTML);
		            }
		        } 
		        else if (status === 'REJECTED') {
		            const resellButton = actionsContainer.querySelector('.btn-resale');
		            if (resellButton) resellButton.remove();
		        }
		    }
		});
		
		eventSource.addEventListener('bid_count_update', (event) => {
		    const { requestId, bidderCount } = JSON.parse(event.data);
		    const articleElement = document.querySelector(`article[data-request-id='${requestId}']`);
		    if (!articleElement) return;

		    const bidderCountElement = articleElement.querySelector('.bidder-count');
		    if (bidderCountElement) {
		        bidderCountElement.textContent = `제안 ${bidderCount}건 도착`;
		        bidderCountElement.style.transition = 'transform 0.2s ease';
		        bidderCountElement.style.transform = 'scale(1.2)';
		        setTimeout(() => {
		            bidderCountElement.style.transform = 'scale(1)';
		        }, 200);
		    }
		});
		
		eventSource.addEventListener('dashboard_update', (event) => {
		    const dashboardCard = document.querySelector('.dashboard-grid');
		    if (!dashboardCard) return;

		    const metrics = JSON.parse(event.data);
		    
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
    
	const throttledInit = throttle(initializeNotifications, 2000);
	throttledInit();
	// initializeNotifications();
});