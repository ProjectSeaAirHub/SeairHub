document.addEventListener('DOMContentLoaded', () => {
    // [수정] F2F 모델에 맞는 데이터 소스 사용
    const allRequests = mockRequests;
    const myAvailableContainers = myContainersData; // 나의 컨테이너 데이터

    const requestListContainer = document.querySelector('.request-list');
    const paginationContainer = document.querySelector('.pagination-container');
    const activeFiltersContainer = document.querySelector('.active-filters-container');
    const cardTemplate = document.getElementById('request-card-template');
    const offerFormTemplate = document.getElementById('offer-form-template');
    const sortButtons = document.querySelectorAll('.btn-sort');
    const filterButtons = document.querySelectorAll('.btn-filter');
    const dateSearchBtn = document.getElementById('btn-date-search');
    const excludeClosedBtn = document.getElementById('btn-exclude-closed');
    const portModal = document.getElementById('port-modal');
    const openModalBtn = document.getElementById('btn-open-port-modal');
    const closeModalBtn = document.getElementById('btn-close-port-modal');
    const applyPortFilterBtn = document.getElementById('btn-apply-port-filter');
    const resetPortFilterBtn = document.getElementById('btn-reset-port-filter');

    let currentPage = 1;
    const itemsPerPage = 10;
    let currentSort = { key: 'id', direction: 'asc' };
    let currentFilters = {
        tradeType: null, transportType: null, startDate: null, endDate: null,
        departurePorts: [], arrivalPorts: [], status: null
    };

    /** URL 파라미터를 읽어 필터를 적용하는 함수 */
    function applyFiltersFromURL() {
        const params = new URLSearchParams(window.location.search);
        const departure = params.get('departure');
        const arrival = params.get('arrival');

        if (departure) {
            const decodedDeparture = decodeURIComponent(departure);
            if (!currentFilters.departurePorts.includes(decodedDeparture)) {
                currentFilters.departurePorts.push(decodedDeparture);
            }
            const depCheckbox = document.querySelector(`input[name="departure-port"][value="${decodedDeparture}"]`);
            if (depCheckbox) depCheckbox.checked = true;
        }
        if (arrival) {
            const decodedArrival = decodeURIComponent(arrival);
            if (!currentFilters.arrivalPorts.includes(decodedArrival)) {
                currentFilters.arrivalPorts.push(decodedArrival);
            }
            const arrCheckbox = document.querySelector(`input[name="arrival-port"][value="${decodedArrival}"]`);
            if (arrCheckbox) arrCheckbox.checked = true;
        }
    }

    function closeOpenOfferForm() {
        const openForm = document.querySelector('.offer-form-expand');
        if (openForm) {
            const card = openForm.previousElementSibling;
            if (card && card.classList.contains('is-expanded')) {
                card.classList.remove('is-expanded');
            }
            openForm.remove();
        }
    }

    /** [수정] 입찰 폼 로직 */
    function handleQuoteButtonClick(e) {
        const clickedButton = e.target.closest('.btn-quote');
        if (!clickedButton || clickedButton.classList.contains('is-disabled')) return;

        const card = clickedButton.closest('.request-card');
        const requestId = card.dataset.requestId;
        const currentRequest = allRequests.find(req => req.id === requestId);

        if (card.classList.contains('is-expanded')) {
            closeOpenOfferForm();
            return;
        }

        closeOpenOfferForm();

        const formClone = offerFormTemplate.content.cloneNode(true);
        card.after(formClone);
        card.classList.add('is-expanded');

        const formWrapper = card.nextElementSibling;
        const containerSelect = formWrapper.querySelector('.container-select');
        const capacityInput = formWrapper.querySelector('.available-capacity');
        const priceInput = formWrapper.querySelector('.bid-price');
        const submitBtn = formWrapper.querySelector('.btn-submit-bid');
        const cancelBtn = formWrapper.querySelector('.btn-cancel');
        const statusText = formWrapper.querySelector('.form-status');
        let selectedContainer = null;

        // [수정] 나의 컨테이너 목록 필터링
        myAvailableContainers.forEach(container => {
            const availableCBM = container.totalCapacity - container.confirmedCBM - container.registeringCBM - container.biddingCBM;
            // 경로가 일치하고, 남은 공간이 있는 컨테이너만 옵션에 추가
            if (container.departurePort === currentRequest.departurePort && container.arrivalPort === currentRequest.arrivalPort && availableCBM > 0) {
                const option = document.createElement('option');
                option.value = container.id;
                option.textContent = `${container.containerNumber} (${container.size})`;
                containerSelect.appendChild(option);
            }
        });

        function checkBidAvailabilityInForm() {
            if (!selectedContainer) {
                submitBtn.disabled = true;
                statusText.innerHTML = `<div>견적을 제안할 나의 컨테이너를 선택하세요.</div>`;
                return;
            }
            const availableCBM = selectedContainer.totalCapacity - selectedContainer.confirmedCBM - selectedContainer.registeringCBM - selectedContainer.biddingCBM;
            const hasEnoughCapacity = availableCBM >= currentRequest.cbm;

            if (hasEnoughCapacity) {
                submitBtn.disabled = false;
                statusText.innerHTML = `<strong class="possible">견적제안 가능</strong>합니다.`;
            } else {
                submitBtn.disabled = true;
                statusText.innerHTML = `<strong class="impossible">견적제안 불가</strong> (선택한 컨테이너의 잔여 용량 부족)`;
            }
        }
        checkBidAvailabilityInForm();

        containerSelect.addEventListener('change', () => {
            const selectedId = containerSelect.value;
            selectedContainer = myAvailableContainers.find(c => c.id === selectedId) || null;
            if (selectedContainer) {
                const availableCBM = selectedContainer.totalCapacity - selectedContainer.confirmedCBM - selectedContainer.registeringCBM - selectedContainer.biddingCBM;
                capacityInput.value = `${availableCBM.toFixed(2)} CBM`;
            } else {
                capacityInput.value = "컨테이너 선택시 자동으로 채워짐";
            }
            checkBidAvailabilityInForm();
        });

        cancelBtn.addEventListener('click', closeOpenOfferForm);

        submitBtn.addEventListener('click', () => {
            if (!priceInput.value || priceInput.value <= 0) {
                alert('입찰가를 정확히 입력하세요.');
                return;
            }
            console.log(`입찰 제출: 요청ID ${requestId}, 컨테이너ID ${selectedContainer.id}, 가격 ${priceInput.value}`);
            alert('입찰이 완료되었습니다!');
            closeOpenOfferForm();
        });
    }

    function renderRequestList(requests) {
        closeOpenOfferForm();
        requestListContainer.innerHTML = '';
        if (requests.length === 0) {
            requestListContainer.innerHTML = `<p class="no-results-message" style="text-align: center; padding: 40px; color: #6c757d;">조회된 요청이 없습니다.</p>`;
            return;
        }
        requests.forEach(req => {
            const cardClone = cardTemplate.content.cloneNode(true);
            const cardElement = cardClone.querySelector('.request-card');
            cardElement.dataset.requestId = req.id;
            cardElement.querySelector('.id-label').textContent = `${req.deadlineDate.replaceAll('-', '')} No.${req.id.slice(-3)}`;
            cardElement.querySelector('.item-name').textContent = req.itemName;
            cardElement.querySelector('.incoterms').textContent = req.incoterms;
            cardElement.querySelector('.departure').textContent = req.departurePort;
            cardElement.querySelector('.arrival').textContent = req.arrivalPort;
            cardElement.querySelector('.deadline').textContent = req.deadlineDate.replaceAll('-', '.');
            cardElement.querySelector('.trade-type').textContent = req.tradeType;
            cardElement.querySelector('.transport-type').textContent = req.transportType;
            cardElement.querySelector('.cbm').textContent = `${req.cbm.toFixed(2)} CBM`;
            const timerBtn = cardElement.querySelector('.btn-timer');
            timerBtn.dataset.deadline = `${req.deadlineDate}T${req.deadlineTime}`;
            requestListContainer.appendChild(cardElement);
        });
    }

    function renderPagination(totalItems) {
        paginationContainer.innerHTML = '';
        const totalPages = Math.ceil(totalItems / itemsPerPage);
        if (totalPages <= 1) return;
        const ul = document.createElement('ul');
        ul.className = 'pagination';
        function createPageItem(page, text, isDisabled = false, isActive = false) {
            const li = document.createElement('li');
            li.className = 'pagination-item';
            if (isDisabled) li.classList.add('is-disabled');
            if (isActive) li.classList.add('is-active');
            const a = document.createElement('a');
            a.href = '#'; a.dataset.page = page; a.textContent = text;
            li.appendChild(a); return li;
        }
        ul.appendChild(createPageItem(1, '<<', currentPage === 1));
        ul.appendChild(createPageItem(currentPage - 1, '<', currentPage === 1));
        const pageGroupSize = 5;
        const currentGroup = Math.floor((currentPage - 1) / pageGroupSize);
        let startPage = currentGroup * pageGroupSize + 1;
        let endPage = Math.min(startPage + pageGroupSize - 1, totalPages);
        for (let i = startPage; i <= endPage; i++) {
            ul.appendChild(createPageItem(i, i, false, i === currentPage));
        }
        ul.appendChild(createPageItem(currentPage + 1, '>', currentPage === totalPages));
        ul.appendChild(createPageItem(totalPages, '>>', currentPage === totalPages));
        paginationContainer.appendChild(ul);
    }

    function renderActiveFilters() {
        activeFiltersContainer.innerHTML = '';
        if (currentFilters.departurePorts.length > 0) {
            const tag = document.createElement('div');
            tag.className = 'filter-tag';
            tag.innerHTML = `<span>출발: ${currentFilters.departurePorts.join(', ')}</span><button class="remove-filter" data-filter-type="departure">×</button>`;
            activeFiltersContainer.appendChild(tag);
        }
        if (currentFilters.arrivalPorts.length > 0) {
            const tag = document.createElement('div');
            tag.className = 'filter-tag';
            tag.innerHTML = `<span>도착: ${currentFilters.arrivalPorts.join(', ')}</span><button class="remove-filter" data-filter-type="arrival">×</button>`;
            activeFiltersContainer.appendChild(tag);
        }
    }

    function updateAllTimers() {
        const timerButtons = document.querySelectorAll('.btn-timer');
        const now = new Date();
        timerButtons.forEach(button => {
            const quoteBtn = button.closest('.request-card').querySelector('.btn-quote');
            const deadline = new Date(button.dataset.deadline);
            const diff = deadline - now;
            if (diff <= 0) {
                button.textContent = '마감';
                button.classList.add('is-expired');
                if (quoteBtn) quoteBtn.classList.add('is-disabled');
            } else {
                const hours = Math.floor(diff / (3600 * 1000));
                const minutes = Math.floor((diff % (3600 * 1000)) / (60 * 1000));
                const seconds = Math.floor((diff % (60 * 1000)) / 1000);
                button.textContent = `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
                if (quoteBtn) quoteBtn.classList.remove('is-disabled');
            }
        });
    }

    function applyAndRender() {
        let processedData = allRequests.filter(item => {
            const tradeMatch = !currentFilters.tradeType || item.tradeType === currentFilters.tradeType;
            const transportMatch = !currentFilters.transportType || item.transportType === currentFilters.transportType;
            const itemDate = new Date(item.deadlineDate);
            const startMatch = !currentFilters.startDate || itemDate >= new Date(currentFilters.startDate);
            const endMatch = !currentFilters.endDate || itemDate <= new Date(currentFilters.endDate);
            const departureMatch = currentFilters.departurePorts.length === 0 || currentFilters.departurePorts.includes(item.departurePort);
            const arrivalMatch = currentFilters.arrivalPorts.length === 0 || currentFilters.arrivalPorts.includes(item.arrivalPort);
            const deadline = new Date(`${item.deadlineDate}T${item.deadlineTime}`);
            const isClosed = deadline <= new Date();
            const statusMatch = !(currentFilters.status === 'exclude_closed' && isClosed);
            return tradeMatch && transportMatch && startMatch && endMatch && departureMatch && arrivalMatch && statusMatch;
        });
        processedData.sort((a, b) => {
            const direction = currentSort.direction === 'asc' ? 1 : -1; const key = currentSort.key;
            if (key === 'port') { const routeA = a.departurePort + a.arrivalPort; const routeB = b.departurePort + b.arrivalPort; return routeA.localeCompare(routeB) * direction; }
            else { const valA = a[key]; const valB = b[key]; if (valA < valB) return -1 * direction; if (valA > valB) return 1 * direction; return 0; }
        });
        renderActiveFilters(); renderPagination(processedData.length);
        const startIndex = (currentPage - 1) * itemsPerPage; const paginatedData = processedData.slice(startIndex, startIndex + itemsPerPage);
        renderRequestList(paginatedData); updateAllTimers();
    }

    function init() {
        applyFiltersFromURL();
        applyAndRender();
        setInterval(updateAllTimers, 1000);

        requestListContainer.addEventListener('click', handleQuoteButtonClick);

        sortButtons.forEach(button => {
            button.addEventListener('click', () => {
                const sortKey = button.dataset.sortKey;
                if (currentSort.key === sortKey) {
                    currentSort.direction = currentSort.direction === 'asc' ? 'desc' : 'asc';
                } else {
                    currentSort.key = sortKey;
                    currentSort.direction = 'asc';
                }
                sortButtons.forEach(btn => {
                    const span = btn.querySelector('span');
                    if (btn.dataset.sortKey === currentSort.key) {
                        btn.classList.add('is-active');
                        span.textContent = currentSort.direction === 'asc' ? '▲' : '▼';
                    } else {
                        btn.classList.remove('is-active');
                        span.textContent = '▼';
                    }
                });
                currentPage = 1;
                applyAndRender();
            });
        });

        filterButtons.forEach(button => {
            button.addEventListener('click', () => {
                const group = button.dataset.filterGroup;
                const value = button.dataset.filterValue;
                if (button.classList.contains('is-active')) {
                    currentFilters[group] = null;
                    button.classList.remove('is-active');
                } else {
                    document.querySelectorAll(`.btn-filter[data-filter-group="${group}"]`).forEach(btn => btn.classList.remove('is-active'));
                    currentFilters[group] = value;
                    button.classList.add('is-active');
                }
                currentPage = 1;
                applyAndRender();
            });
        });

        excludeClosedBtn.addEventListener('click', () => {
            excludeClosedBtn.classList.toggle('is-active');
            currentFilters.status = excludeClosedBtn.classList.contains('is-active') ? 'exclude_closed' : null;
            currentPage = 1;
            applyAndRender();
        });

        dateSearchBtn.addEventListener('click', () => {
            currentFilters.startDate = document.getElementById('start-date').value;
            currentFilters.endDate = document.getElementById('end-date').value;
            currentPage = 1;
            applyAndRender();
        });

        paginationContainer.addEventListener('click', (e) => {
            e.preventDefault();
            const target = e.target.closest('a');
            if (!target || target.parentElement.classList.contains('is-disabled')) return;
            const page = parseInt(target.dataset.page, 10);
            if (page && page !== currentPage) {
                currentPage = page;
                applyAndRender();
            }
        });

        openModalBtn.addEventListener('click', () => portModal.style.display = 'flex');
        closeModalBtn.addEventListener('click', () => portModal.style.display = 'none');
        portModal.addEventListener('click', (e) => { if (e.target === portModal) portModal.style.display = 'none'; });

        applyPortFilterBtn.addEventListener('click', () => {
            currentFilters.departurePorts = Array.from(document.querySelectorAll('input[name="departure-port"]:checked')).map(cb => cb.value);
            currentFilters.arrivalPorts = Array.from(document.querySelectorAll('input[name="arrival-port"]:checked')).map(cb => cb.value);
            portModal.style.display = 'none';
            currentPage = 1;
            applyAndRender();
        });

        resetPortFilterBtn.addEventListener('click', () => {
            document.querySelectorAll('#port-modal input[type="checkbox"]').forEach(cb => cb.checked = false);
        });

        activeFiltersContainer.addEventListener('click', (e) => {
            if (e.target.classList.contains('remove-filter')) {
                const filterType = e.target.dataset.filterType;
                if (filterType === 'departure') {
                    currentFilters.departurePorts = [];
                    document.querySelectorAll('input[name="departure-port"]').forEach(cb => cb.checked = false);
                } else if (filterType === 'arrival') {
                    currentFilters.arrivalPorts = [];
                    document.querySelectorAll('input[name="arrival-port"]').forEach(cb => cb.checked = false);
                }
                currentPage = 1;
                applyAndRender();
            }
        });
    }

    init();
});