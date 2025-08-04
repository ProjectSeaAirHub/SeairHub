document.addEventListener('DOMContentLoaded', () => {
    const ctx = document.getElementById('scfiChart');
    if (!ctx) return;

    // 서버에 SCFI 데이터 요청
    fetch('/api/scfi-data')
        .then(response => response.json())
        .then(data => {
            // Chart.js에 필요한 형식으로 데이터 가공
            const labels = data.map(item => item.recordDate);
            const values = data.map(item => item.indexValue);

            // 선 그래프 생성
            new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'SCFI 종합지수',
                        data: values,
                        borderColor: '#FC512C', // 선 색상
                        backgroundColor: 'rgba(252, 81, 44, 0.1)', // 선 아래 영역 색상
                        fill: true,
                        tension: 0.1 // 선의 굴곡을 부드럽게
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: false, // 0부터 시작하지 않아도 됨
                            ticks: {
                                callback: function(value, index, values) {
                                    return value.toLocaleString(); // 숫자에 콤마 추가
                                }
                            }
                        }
                    },
                    plugins: {
                        tooltip: {
                            callbacks: {
                                label: function(context) {
                                    return ` ${context.dataset.label}: ${context.raw.toLocaleString()}`;
                                }
                            }
                        }
                    }
                }
            });
        })
        .catch(error => {
            console.error('SCFI 데이터를 불러오는 데 실패했습니다.', error);
        });
});