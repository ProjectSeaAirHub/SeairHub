document.addEventListener('DOMContentLoaded', () => {
    // 1. 대시보드 지표 데이터 불러오기
    fetch('/api/adm/dashboard-metrics')
        .then(response => response.json())
        .then(data => {
            document.getElementById('today-requests').textContent = data.todayRequests;
            document.getElementById('today-deals').textContent = data.todayDeals;
            document.getElementById('total-fwd-users').textContent = data.totalFwdUsers;
            document.getElementById('total-cus-users').textContent = data.totalCusUsers;
            document.getElementById('pending-users').textContent = data.pendingUsers;
            document.getElementById('no-bid-requests').textContent = data.noBidRequests;
        })
        .catch(error => console.error('Dashboard metrics fetch error:', error));

    // 2. 차트 데이터 불러오기 (기존 로직)
    fetch('/api/adm/volumes')
        .then(response => response.json())
        .then(data => {
            const availableCbmPositive = Math.abs(data.availableCbm);
            const ctx = document.getElementById('cbmChart').getContext('2d');
            
            new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: ['확정', '재판매중', '입찰중', '공차물량'],
                    datasets: [{
                        label: 'CBM',
                        data: [
                            data.confirmedCbm,
                            data.resaleCbm,
                            data.biddingCbm,
                            availableCbmPositive
                        ],
                        backgroundColor: [
                            '#20c997', 
                            '#ffc107', 
                            '#fd7e14', 
                            '#adb5bd'
                        ],
                        borderWidth: 1,
                        borderRadius: 5,
                        barThickness: 80,
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: {
                        y: {
                            min: 0,
                            title: { display: true, text: 'CBM (Cubic Meter)', font: { size: 15 } },
                            ticks: { font: { size: 14 } }
                        },
                        x: {
                            ticks: { font: { size: 18 } }
                        }
                    }
                }
            });
        })
        .catch(error => {
            console.error('Chart data fetch error:', error);
            const chartContainer = document.querySelector('.chart-container');
            chartContainer.innerHTML = `<p style="text-align:center; color:red;">차트 데이터를 불러오는 중 오류가 발생했습니다.</p>`;
        });
});