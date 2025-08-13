// [✅ notification.js 파일 전체를 이 최종 코드로 교체해주세요]
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
            showNoNotificationMessage();
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

        eventSource.addEventListener('unreadCount', (event) => {
            updateCountUI(event.data);
        });

        eventSource.addEventListener('notification', async (event) => {
             // 새 알림이 오면 목록 전체를 다시 불러와서 정확도를 높입니다.
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

        eventSource.addEventListener('unreadChat', (event) => {
            document.dispatchEvent(new CustomEvent('sse:unreadChat', { detail: event.data }));
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
            // 서버에 읽음 처리 요청만 보냅니다. UI 변경은 서버가 보내줄 SSE 이벤트를 통해 이루어집니다.
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
            // 서버에 '모두 읽음' 처리 요청만 보냅니다. UI 변경은 서버가 보내줄 SSE 이벤트를 통해 이루어집니다.
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

    // 3초 쓰로틀링을 적용하여 함수를 호출합니다.
    const throttledInit = throttle(initializeNotifications, 3000);
    throttledInit();
});