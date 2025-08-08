document.addEventListener('DOMContentLoaded', () => {
    const container = document.querySelector('.container');
    const currentUserSeq = container.dataset.userSeq;
    const currentUserName = container.dataset.userName;

    let stompClient = null;
    let selectedChatRoomId = null;
    let subscriptions = new Map();

    const chatListContainer = document.getElementById('chat-list-container');
    const chatRoomView = document.getElementById('chat-room-view');
    const noChatSelectedView = document.getElementById('no-chat-selected');
    const chatRoomTitle = document.getElementById('chat-room-title');
    const messageListContainer = document.getElementById('message-list-container');
    const messageInput = document.getElementById('chat-message-input');
    const sendMessageBtn = document.getElementById('send-message-btn');

    // 1. 채팅방 목록을 서버에서 불러와 렌더링하는 함수
    async function loadChatRooms() {
        try {
            const response = await fetch('/api/chat/rooms');
            if (!response.ok) throw new Error('채팅방 목록을 불러오는데 실패했습니다.');
            const chatRooms = await response.json();
            
            chatListContainer.innerHTML = '';
            if (chatRooms.length === 0) {
                chatListContainer.innerHTML = '<p style="text-align:center; padding: 20px; color: #868e96;">진행중인 채팅이 없습니다.</p>';
                return;
            }

            chatRooms.forEach(room => {
                const roomItem = document.createElement('div');
                roomItem.className = 'chat-room-item';
                roomItem.dataset.roomId = room.chatRoomId;
                roomItem.innerHTML = `
                    <div class="room-name">${room.roomName}</div>
                    <div class="last-message">${room.lastMessage || ''}</div>
                `;
                chatListContainer.appendChild(roomItem);
            });
        } catch (error) {
            console.error(error);
            chatListContainer.innerHTML = `<p style="text-align:center; padding: 20px; color: red;">${error.message}</p>`;
        }
    }

    // 2. 특정 채팅방 선택 시 메시지 불러오기 및 UI 변경
    async function selectChatRoom(roomId) {
        if (selectedChatRoomId === roomId) return;

        // 기존 구독 해지
        if (selectedChatRoomId && subscriptions.has(selectedChatRoomId)) {
            subscriptions.get(selectedChatRoomId).unsubscribe();
            subscriptions.delete(selectedChatRoomId);
        }

        selectedChatRoomId = roomId;

        // UI 업데이트
        document.querySelectorAll('.chat-room-item.active').forEach(item => item.classList.remove('active'));
        document.querySelector(`.chat-room-item[data-room-id='${roomId}']`).classList.add('active');
        
        const roomName = document.querySelector(`.chat-room-item[data-room-id='${roomId}'] .room-name`).textContent;
        chatRoomTitle.textContent = roomName;
        
        noChatSelectedView.style.display = 'none';
        chatRoomView.style.display = 'flex';
        messageInput.disabled = false;
        sendMessageBtn.disabled = false;
        messageListContainer.innerHTML = '';

        // 메시지 이력 불러오기
        const response = await fetch(`/api/chat/rooms/${roomId}/messages`);
        const messages = await response.json();
        messages.forEach(showMessage);

        // 새로운 채팅방 구독
        const subscription = stompClient.subscribe('/topic/chatroom/' + roomId, (message) => {
            showMessage(JSON.parse(message.body));
        });
        subscriptions.set(roomId, subscription);
    }

    // 3. 메시지를 화면에 표시하는 함수
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

    // 4. 메시지 전송 함수
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

    // 5. WebSocket 연결 설정
    function connect() {
        const socket = new SockJS('/ws-chat');
        stompClient = Stomp.over(socket);
        stompClient.connect({}, (frame) => {
            console.log('Connected: ' + frame);
            loadChatRooms(); // 연결 성공 후 채팅방 목록 로드
        });
    }

    // --- 이벤트 리스너 ---
    sendMessageBtn.addEventListener('click', sendMessage);
    messageInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') sendMessage();
    });

    chatListContainer.addEventListener('click', (e) => {
        const roomItem = e.target.closest('.chat-room-item');
        if (roomItem) {
            selectChatRoom(roomItem.dataset.roomId);
        }
    });

	
	// --- 이벤트 리스너 --- 섹션에 아래 이벤트 리스너를 추가합니다.
	chatRoomTitle.addEventListener('click', () => {
	    if (!selectedChatRoomId) return; // 채팅방이 선택되지 않았으면 무시

	    const currentName = chatRoomTitle.textContent;
	    const newName = prompt("새로운 채팅방 이름을 입력하세요:", currentName);

	    if (newName && newName.trim() !== '' && newName !== currentName) {
	        updateChatRoomName(selectedChatRoomId, newName.trim());
	    }
	});
	
	// chat.js 파일의 맨 아래에 아래 함수를 새로 추가합니다.
	async function updateChatRoomName(roomId, name) {
	    try {
	        const response = await fetch(`/api/chat/rooms/${roomId}/name`, {
	            method: 'PUT',
	            headers: { 'Content-Type': 'application/json' },
	            body: JSON.stringify({ name: name })
	        });

	        if (!response.ok) {
	            throw new Error('이름 변경에 실패했습니다.');
	        }

	        // 화면에 즉시 반영
	        chatRoomTitle.textContent = name;
	        document.querySelector(`.chat-room-item[data-room-id='${roomId}'] .room-name`).textContent = name;
	        alert('채팅방 이름이 변경되었습니다.');
	    } catch (error) {
	        console.error(error);
	        alert(error.message);
	    }
	}
	
	
    // --- 초기화 ---
    connect();
});