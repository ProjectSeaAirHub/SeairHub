// [✅ chat.js 파일 전체를 이 최종 코드로 교체해주세요]
document.addEventListener('DOMContentLoaded', () => {
    const container = document.querySelector('body'); 
    const currentUserSeq = container.dataset.userSeq;
    const currentUserName = container.dataset.userName;

    let stompClient = null;
    let selectedChatRoomId = null;
    let subscriptions = new Map();

    let isChatWidgetVisible = false;

    const chatListContainer = document.getElementById('chat-list-container');
    const chatRoomView = document.getElementById('chat-room-view');
    const noChatSelectedView = document.getElementById('no-chat-selected');
    const chatRoomTitle = document.getElementById('chat-room-title');
    const messageListContainer = document.getElementById('message-list-container');
    const messageInput = document.getElementById('chat-message-input');
    const sendMessageBtn = document.getElementById('send-message-btn');
    const addGroupBtn = document.getElementById('add-group-btn');
    const inputModal = document.getElementById('input-modal');
    const inputModalTitle = document.getElementById('input-modal-title');
    const inputModalField = document.getElementById('input-modal-field');
    const inputModalConfirm = document.getElementById('input-modal-confirm');
    const inputModalCancel = document.getElementById('input-modal-cancel');
    const inputModalClose = document.getElementById('input-modal-close');
    const CHAT_GROUPS_KEY = `chatGroups_${currentUserSeq}`;

    // [✅ 핵심 수정 1] 로딩되자마자 부모 창에 준비되었음을 알립니다.
    if (window.parent) {
        window.parent.postMessage({ type: 'chatReady' }, '*');
    }
    
    // [✅ 핵심 수정 2] 부모 창의 메시지를 수신하여 자신의 '보임/숨김' 상태를 업데이트합니다.
    window.addEventListener('message', (event) => {
        // 보안을 위해 부모 창에서 온 메시지만 처리합니다.
        if (event.source !== window.parent) {
            return;
        }
        if (event.data && event.data.type === 'chatVisibility') {
            isChatWidgetVisible = event.data.visible;
            if (isChatWidgetVisible && selectedChatRoomId) {
                fetch(`/api/chat/rooms/${selectedChatRoomId}/read`, { method: 'POST' });
            }
        }
    });

    async function loadChatRoomsAndGroups() {
        try {
            const response = await fetch('/api/chat/rooms');
            if (!response.ok) throw new Error('채팅방 목록을 불러오는데 실패했습니다.');
            const chatRooms = await response.json();
            
            chatListContainer.innerHTML = '';
            
            let groups = JSON.parse(localStorage.getItem(CHAT_GROUPS_KEY)) || {};
            
            Object.keys(groups).forEach(groupId => {
                createGroupElement(groupId, groups[groupId].name);
            });

            createGroupElement('unassigned', '미분류');

            chatRooms.forEach(room => {
                const roomElement = createChatRoomElement(room);
                const groupId = findGroupIdForRoom(room.chatRoomId, groups) || 'unassigned';
                const groupContent = chatListContainer.querySelector(`.chat-group[data-group-id='${groupId}'] .chat-group-content`);
                if (groupContent) {
                    groupContent.appendChild(roomElement);
                }
            });

        } catch (error) {
            console.error(error);
            chatListContainer.innerHTML = `<p style="text-align:center; padding: 20px; color: red;">${error.message}</p>`;
        }
    }

    window.refreshChatList = loadChatRoomsAndGroups;

    function createGroupElement(id, name) {
        const groupDiv = document.createElement('div');
        groupDiv.className = 'chat-group';
        groupDiv.dataset.groupId = id;
        groupDiv.innerHTML = `
            <div class="chat-group-header">
                <span>${name}</span>
                ${id !== 'unassigned' ? '<span class="delete-group-btn">×</span>' : ''}
            </div>
            <div class="chat-group-content"></div>
        `;
        chatListContainer.appendChild(groupDiv);
    }
    
    function createChatRoomElement(room) {
        const roomItem = document.createElement('div');
        roomItem.className = 'chat-room-item';
        roomItem.dataset.roomId = room.chatRoomId;
        roomItem.draggable = true;
        roomItem.innerHTML = `
            <div class="room-name">${room.roomName}</div>
            <div class="last-message">${room.lastMessage || ''}</div>
            <span class="unread-count">${room.unreadCount}</span>
        `;
        const unreadBadge = roomItem.querySelector('.unread-count');
        if (room.unreadCount > 0) {
            unreadBadge.style.display = 'flex';
        }
        return roomItem;
    }

    function findGroupIdForRoom(roomId, groups) {
        for (const groupId in groups) {
            if (groups[groupId].rooms.includes(roomId.toString())) {
                return groupId;
            }
        }
        return null;
    }

    async function selectChatRoom(roomId) {
        if (selectedChatRoomId === roomId) return;

        try {
            await fetch(`/api/chat/rooms/${roomId}/read`, { method: 'POST' });
            const badge = document.querySelector(`.chat-room-item[data-room-id='${roomId}'] .unread-count`);
            if (badge) {
                badge.textContent = '0';
                badge.style.display = 'none';
            }
        } catch (error) {
            console.error('메시지 읽음 처리 실패:', error);
        }

        if (selectedChatRoomId && subscriptions.has(selectedChatRoomId)) {
            subscriptions.get(selectedChatRoomId).unsubscribe();
            subscriptions.delete(selectedChatRoomId);
        }
        selectedChatRoomId = roomId;
        document.querySelectorAll('.chat-room-item.active').forEach(item => item.classList.remove('active'));
        document.querySelector(`.chat-room-item[data-room-id='${roomId}']`).classList.add('active');
        const roomName = document.querySelector(`.chat-room-item[data-room-id='${roomId}'] .room-name`).textContent;
        chatRoomTitle.textContent = roomName;
        noChatSelectedView.style.display = 'none';
        chatRoomView.style.display = 'flex';
        messageInput.disabled = false;
        sendMessageBtn.disabled = false;
        messageListContainer.innerHTML = '';
        const response = await fetch(`/api/chat/rooms/${roomId}/messages`);
        const messages = await response.json();
        messages.forEach(showMessage);
        
        const subscription = stompClient.subscribe('/topic/chatroom/' + roomId, (message) => {
            showMessage(JSON.parse(message.body));
            
            if (isChatWidgetVisible) {
                fetch(`/api/chat/rooms/${roomId}/read`, { method: 'POST' });
            }
        });
        subscriptions.set(roomId, subscription);
    }
    
    function showMessage(message) {
        const messageItem = document.createElement('div');
        messageItem.classList.add('message-item');
        const messageBubble = document.createElement('div');
        messageBubble.classList.add('message-bubble');
        messageBubble.textContent = message.messageContent;
        if (message.senderSeq == currentUserSeq) {
            messageItem.classList.add('my-message');
        } else {
            messageItem.classList.add('other-message');
            const senderName = document.createElement('div');
            senderName.classList.add('sender-name');
            senderName.textContent = message.senderName;
            messageItem.appendChild(senderName);
        }
        messageItem.appendChild(messageBubble);
        messageListContainer.appendChild(messageItem);
        messageListContainer.scrollTop = messageListContainer.scrollHeight;
    }

    function sendMessage() {
        const messageContent = messageInput.value.trim();
        if (messageContent && stompClient && selectedChatRoomId) {
            const chatMessage = {
                chatRoomId: selectedChatRoomId,
                senderSeq: parseInt(currentUserSeq),
                senderName: currentUserName,
                messageContent: messageContent
            };
            stompClient.send("/app/chat/sendMessage", {}, JSON.stringify(chatMessage));
            messageInput.value = '';
        }
    }

    function connect() {
        const socket = new SockJS('/ws-chat');
        stompClient = Stomp.over(socket);
        stompClient.connect({}, (frame) => {
            console.log('Connected: ' + frame);
            loadChatRoomsAndGroups();
        });
    }

    let onConfirmCallback = null;
    const openInputModal = (title, value, placeholder, onConfirm) => {
        inputModalTitle.textContent = title;
        inputModalField.value = value;
        inputModalField.placeholder = placeholder;
        inputModal.style.display = 'flex';
        inputModalField.focus();
        onConfirmCallback = onConfirm;
    };
    const closeInputModal = () => {
        inputModal.style.display = 'none';
        inputModalField.value = '';
        onConfirmCallback = null;
    };
    inputModalConfirm.addEventListener('click', () => { if (onConfirmCallback) { onConfirmCallback(inputModalField.value); } });
    inputModalField.addEventListener('keypress', (e) => { if (e.key === 'Enter') { inputModalConfirm.click(); } });
    inputModalCancel.addEventListener('click', closeInputModal);
    inputModalClose.addEventListener('click', closeInputModal);
    let draggedItem = null;
    chatListContainer.addEventListener('dragstart', (e) => { if (e.target.classList.contains('chat-room-item')) { draggedItem = e.target; setTimeout(() => e.target.style.display = 'none', 0); } });
    chatListContainer.addEventListener('dragend', (e) => { if (draggedItem) { setTimeout(() => { draggedItem.style.display = ''; draggedItem = null; }, 0); } });
    chatListContainer.addEventListener('dragover', (e) => { e.preventDefault(); const groupContent = e.target.closest('.chat-group-content'); if (groupContent) { groupContent.classList.add('drag-over'); } });
    chatListContainer.addEventListener('dragleave', (e) => { const groupContent = e.target.closest('.chat-group-content'); if (groupContent) { groupContent.classList.remove('drag-over'); } });
    chatListContainer.addEventListener('drop', (e) => { e.preventDefault(); const groupContent = e.target.closest('.chat-group-content'); if (groupContent && draggedItem) { groupContent.appendChild(draggedItem); groupContent.classList.remove('drag-over'); const roomId = draggedItem.dataset.roomId; const newGroupId = groupContent.parentElement.dataset.groupId; let groups = JSON.parse(localStorage.getItem(CHAT_GROUPS_KEY)) || {}; Object.keys(groups).forEach(gId => { groups[gId].rooms = groups[gId].rooms.filter(rId => rId !== roomId); }); if (newGroupId !== 'unassigned') { if (!groups[newGroupId]) { groups[newGroupId] = { name: 'Unknown', rooms: [] }; } groups[newGroupId].rooms.push(roomId); } localStorage.setItem(CHAT_GROUPS_KEY, JSON.stringify(groups)); } });
    sendMessageBtn.addEventListener('click', sendMessage);
    messageInput.addEventListener('keypress', (e) => { if (e.key === 'Enter') sendMessage(); });
    chatListContainer.addEventListener('click', (e) => { const roomItem = e.target.closest('.chat-room-item'); if (roomItem) { selectChatRoom(roomItem.dataset.roomId); } if (e.target.classList.contains('delete-group-btn')) { const group = e.target.closest('.chat-group'); const groupId = group.dataset.groupId; const groupName = group.querySelector('.chat-group-header span').textContent; if (confirm(`'${groupName}' 그룹을 삭제하시겠습니까?\n(내부의 채팅방은 '미분류'로 이동됩니다.)`)) { let groups = JSON.parse(localStorage.getItem(CHAT_GROUPS_KEY)) || {}; const unassignedGroup = chatListContainer.querySelector('.chat-group[data-group-id="unassigned"] .chat-group-content'); Array.from(group.querySelectorAll('.chat-room-item')).forEach(roomEl => { unassignedGroup.appendChild(roomEl); }); delete groups[groupId]; localStorage.setItem(CHAT_GROUPS_KEY, JSON.stringify(groups)); group.remove(); } } });
	addGroupBtn.addEventListener('click', () => { openInputModal('새 그룹 추가', '', '그룹 이름을 입력하세요', (name) => { if (name && name.trim()) { let groups = JSON.parse(localStorage.getItem(CHAT_GROUPS_KEY)) || {}; const newGroupId = `group_${Date.now()}`; groups[newGroupId] = { name: name.trim(), rooms: [] }; localStorage.setItem(CHAT_GROUPS_KEY, JSON.stringify(groups)); createGroupElement(newGroupId, name.trim()); closeInputModal(); } }); });
	chatRoomTitle.addEventListener('click', () => { if (!selectedChatRoomId) return; const currentName = chatRoomTitle.textContent; openInputModal('채팅방 이름 변경', currentName, '', (newName) => { if (newName && newName.trim() !== '' && newName !== currentName) { updateChatRoomName(selectedChatRoomId, newName.trim()); } closeInputModal(); }); });
	async function updateChatRoomName(roomId, name) { try { const response = await fetch(`/api/chat/rooms/${roomId}/name`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name }) }); if (!response.ok) { throw new Error('이름 변경에 실패했습니다.'); } chatRoomTitle.textContent = name; document.querySelector(`.chat-room-item[data-room-id='${roomId}'] .room-name`).textContent = name; } catch (error) { console.error(error); alert(error.message); } }
    connect();
});