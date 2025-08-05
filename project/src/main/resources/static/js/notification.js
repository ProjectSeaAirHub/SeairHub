document.addEventListener('DOMContentLoaded', () => {
    const notificationArea = document.getElementById('notification-area');
    if (!notificationArea) return;

    const countElement = document.getElementById('notification-count');
    const listElement = document.getElementById('notification-list');
    const dropdown = document.getElementById('notification-dropdown');
    const notiBtn = notificationArea.querySelector('.notification-btn');
    const markAllReadBtn = document.getElementById('mark-all-read-btn');

    let unreadCount = 0;

    const updateCountUI = () => {
        countElement.textContent = unreadCount;
        countElement.style.display = unreadCount > 0 ? 'flex' : 'none';
    };

    const addNotificationToList = (noti, isNew) => {
        const noNotiMsg = listElement.querySelector('.no-notifications');
        if (noNotiMsg) noNotiMsg.remove();
        const li = document.createElement('li');
        li.dataset.id = noti.id;
        li.dataset.url = noti.url;
        li.classList.add(noti.isRead ? 'read' : 'unread');
        li.innerHTML = `<div class="message">${noti.message}</div><div class="timestamp">${noti.createdAt}</div>`;
        if (isNew) listElement.prepend(li);
        else listElement.appendChild(li);
    };
    
    const updateNotificationUI = (notifications) => {
        unreadCount = notifications.filter(n => !n.isRead).length;
        updateCountUI();
        listElement.innerHTML = '';
        if (notifications.length === 0) {
            listElement.innerHTML = '<li class="no-notifications">새로운 알림이 없습니다.</li>';
            return;
        }
        notifications.forEach(noti => addNotificationToList(noti, false));
    };

    const initializeNotifications = async () => {
        try {
            const response = await fetch('/api/notifications');
            if (!response.ok) throw new Error('알림 목록 로딩 실패');
            const notifications = await response.json();
            updateNotificationUI(notifications);
        } catch (error) {
            console.error(error);
            listElement.innerHTML = '<li class="no-notifications">알림을 불러올 수 없습니다.</li>';
        }

        console.log('SSE: 연결을 시작합니다...');
        const eventSource = new EventSource('/api/notifications/subscribe');

        eventSource.onopen = () => {
            console.log('SSE: 연결이 성공적으로 열렸습니다.');
        };
        
        eventSource.addEventListener('connected', (event) => {
            console.log('SSE: 서버로부터 연결 확인 메시지 수신:', event.data);
        });
        
        eventSource.addEventListener('unreadCount', (event) => {
            console.log('SSE: 읽지 않은 알림 개수 수신:', event.data);
            unreadCount = parseInt(event.data, 10);
            updateCountUI();
        });

        eventSource.addEventListener('notification', (event) => {
            console.log('SSE: 새로운 알림 수신!', event.data);
            const newNoti = JSON.parse(event.data);
            unreadCount++;
            updateCountUI();
            addNotificationToList(newNoti, true);
        });

        eventSource.onerror = (error) => {
            console.error('SSE: 오류 발생!', error);
            // eventSource.close(); // 에러 발생 시 자동으로 재연결을 시도하므로, 닫지 않는 것이 더 안정적일 수 있습니다.
        };
    };

    // --- 이벤트 핸들러들 (이하 동일) ---
    notiBtn.addEventListener('click', (e) => { e.stopPropagation(); dropdown.style.display = dropdown.style.display === 'block' ? 'none' : 'block'; });
    listElement.addEventListener('click', async (e) => {
        const li = e.target.closest('li[data-id]');
        if (!li) return;
        const id = li.dataset.id;
        const url = li.dataset.url;
        try {
            if (li.classList.contains('unread')) {
                await fetch(`/api/notifications/${id}/read`, { method: 'POST' });
                li.classList.remove('unread');
                li.classList.add('read');
                unreadCount = Math.max(0, unreadCount - 1);
                updateCountUI();
            }
        } catch (error) { console.error("알림 읽음 처리 실패:", error); }
        finally { if (url && url !== 'null') window.location.href = url; }
    });
    markAllReadBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        try {
            await fetch('/api/notifications/read/all', { method: 'POST' });
            unreadCount = 0;
            updateCountUI();
            listElement.querySelectorAll('.unread').forEach(li => { li.classList.remove('unread'); li.classList.add('read'); });
        } catch (error) { console.error("모두 읽음 처리 실패:", error); }
    });
    document.addEventListener('click', (e) => { if (!notificationArea.contains(e.target)) dropdown.style.display = 'none'; });

    initializeNotifications();
});