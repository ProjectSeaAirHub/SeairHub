document.addEventListener('DOMContentLoaded', () => {
    const tableContainer = document.querySelector('.table-container');

    tableContainer.addEventListener('click', async (e) => {
        if (!e.target.classList.contains('btn-action')) {
            return;
        }

        const button = e.target;
        const action = button.dataset.action;
        const userSeq = button.closest('.action-buttons').dataset.userSeq;
        
        let confirmMessage = `사용자(ID: ${userSeq})의 상태를 '${action}'(으)로 변경하시겠습니까?`;
        
        if (confirm(confirmMessage)) {
            try {
                const response = await fetch(`/api/adm/forwarders/${userSeq}/status`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ status: action })
                });

                const message = await response.text();
                alert(message);

                if (response.ok) {
                    window.location.reload();
                }
            } catch (error) {
                console.error('Error:', error);
                alert('상태 변경 중 오류가 발생했습니다.');
            }
        }
    });
});