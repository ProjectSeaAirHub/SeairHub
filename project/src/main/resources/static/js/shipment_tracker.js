// HTML 문서 로딩이 완료되면 이 코드를 실행합니다.
document.addEventListener('DOMContentLoaded', () => {

    document.body.addEventListener('click', (e) => {
        if (!e.target.matches('.btn-track-shipment')) {
            return;
        }

        const button = e.target;
        const requestId = button.dataset.requestId;
        const imoNumber = button.dataset.imoNumber;
        const mapContainer = document.getElementById(`map-container-${requestId}`);
        const itemContainer = button.closest('.request-item-container');

        if (!itemContainer || !mapContainer || !imoNumber) {
            console.error("지도 표시를 위한 필수 요소나 IMO 번호가 없습니다.");
            return;
        }
        
        if (itemContainer.classList.contains('is-expanded')) {
            mapContainer.innerHTML = '';
            itemContainer.classList.remove('is-expanded');
            button.innerHTML = '운송조회 &#9660;';
            return;
        }
        
        document.querySelectorAll('.request-item-container.is-expanded').forEach(openItem => {
            openItem.classList.remove('is-expanded');
            const openMapContainer = openItem.querySelector('.map-container');
            if (openMapContainer) openMapContainer.innerHTML = '';
            
            const openBtn = openItem.querySelector('.btn-track-shipment');
            if(openBtn) openBtn.innerHTML = '운송조회 &#9660;';
        });

        // ⭐ 핵심 1: 원하는 지도의 세로 높이를 변수로 정의합니다. (이 숫자만 바꾸면 됩니다)
        const mapHeight = 800;

        mapContainer.innerHTML = '';
        const iframe = document.createElement('iframe');
        iframe.style.width = '100%';
        iframe.style.height = mapHeight + 'px'; // ⭐ 핵심 2: iframe의 높이를 변수로 설정
        iframe.style.border = 'none';
        mapContainer.appendChild(iframe);

        const iframeDoc = iframe.contentWindow.document;
        iframeDoc.open();
        iframeDoc.write(`
            <!DOCTYPE html>
            <html>
            <head>
                <style> body, html { margin: 0; padding: 0; height: 100%; width: 100%; overflow: hidden; } </style>
            </head>
            <body>
                <script type="text/javascript">
                    var width = "100%";
                    var height = "${mapHeight}"; /* ⭐ 핵심 3: Vesselfinder 스크립트에도 정확한 픽셀 높이를 전달 */
                    var imo = "${imoNumber}";
                    var show_track = true;
                    var names = true;
                <\/script>
                <script type="text/javascript" src="https://www.vesselfinder.com/aismap.js"><\/script>
            </body>
            </html>
        `);
        iframeDoc.close();

        itemContainer.classList.add('is-expanded');
        button.innerHTML = '지도 닫기 &#9650;';
    });
});