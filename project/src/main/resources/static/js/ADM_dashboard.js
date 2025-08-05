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
			
			// [✅ 아래 1줄을 추가해주세요]
			document.getElementById('missed-confirmation-rate').textContent = data.missedConfirmationRate.toFixed(2) + '%';
			
			    // [추가] SCFI 신호등 UI 업데이트
			    const percentageEl = document.getElementById('scfi-percentage');
			    const messageEl = document.getElementById('scfi-message');
			    const lightEl = document.getElementById('scfi-traffic-light');

			    if (data.scfiChangePercentage !== null) {
			        const percentage = data.scfiChangePercentage.toFixed(2);
			        percentageEl.textContent = `${percentage > 0 ? '+' : ''}${percentage}%`;
			        
			        if (data.scfiStatus === 'GREEN') {
			            lightEl.className = 'traffic-light green';
			            messageEl.textContent = '공급 대비 수요 급등';
			        } else if (data.scfiStatus === 'RED') {
			            lightEl.className = 'traffic-light red';
			            messageEl.textContent = '공급 대비 수요 급락';
			        } else {
			            lightEl.className = 'traffic-light';
			            messageEl.textContent = '안정 상태';
			        }
			    }
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
		
		
		
		    // --- [추가] 3. SCFI 데이터 저장 폼 이벤트 리스너 ---
		    const scfiForm = document.getElementById('scfi-form');
		    scfiForm.addEventListener('submit', async (e) => {
		        e.preventDefault();
		        const recordDate = document.getElementById('scfi-date').value;
		        const indexValue = document.getElementById('scfi-value').value;

		        if (!recordDate || !indexValue) {
		            alert('날짜와 지수 값을 모두 입력해주세요.');
		            return;
		        }

		        try {
		            const response = await fetch('/api/adm/scfi-data', {
		                method: 'POST',
		                headers: { 'Content-Type': 'application/json' },
		                body: JSON.stringify({ recordDate, indexValue })
		            });
		            const message = await response.text();
		            alert(message);
		            if (response.ok) {
		                window.location.reload(); // 성공 시 페이지 새로고침
		            }
		        } catch (error) {
		            alert('데이터 저장 중 오류가 발생했습니다.');
		        }
		    });
		});