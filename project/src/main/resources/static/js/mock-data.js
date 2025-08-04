// ==================================================================
// The 채움+ 프로젝트 Mock Data (F2F 모델 기준)
// 최종 수정: 2025-07-21
// ==================================================================


// ------------------------------------------------------------------
// [영역 1: 견적요청조회 페이지 데이터]
// 다른 포워더들이 판매하기 위해 내놓은 '운송 계약' 목록
// ------------------------------------------------------------------
const mockRequests = [
    {
        id: "request-001",
        itemName: "LG OLED TV",
        incoterms: "FOB",
        tradeType: "수출",
        transportType: "해상",
        departurePort: "부산",
        arrivalPort: "LA",
        cbm: 15.0,
        deadlineDate: "2025-08-15",
        deadlineTime: "18:00:00"
    },
    {
        id: "request-002",
        itemName: "Samsung Foldables",
        incoterms: "CIF",
        tradeType: "수출",
        transportType: "항공",
        departurePort: "인천",
        arrivalPort: "도쿄",
        cbm: 8.5,
        deadlineDate: "2025-08-12",
        deadlineTime: "23:59:59"
    },
    // ... 기타 데이터 10개 이상
];


// ------------------------------------------------------------------
// [영역 2: 나의 제안 조회 페이지 데이터]
// '나'의 입찰 활동 내역: 내가 다른 포워더의 요청에 제안한 목록
// ------------------------------------------------------------------
const myOffers = [
    {
        id: "my-offer-001",
        requestId: "20250722003", // 내가 입찰한 원본 요청 ID
        itemName: "Apple",
        incoterms: "CIF",
        departurePort: "인천",
        arrivalPort: "도쿄",
        requestDate: "2025-07-30",
        cbm: 1.90,
        status: "pending", // 나의 제안 상태: pending, accepted, rejected, completed
        myBidId: "bid-apple-2", // 나의 제안 ID
        acceptedBidId: null // 최종 수락된 제안 ID (경쟁사 포함)
    },
    {
        id: "my-offer-002",
        requestId: "20250722002",
        itemName: "Samsung Foldables",
        incoterms: "CIF",
        departurePort: "인천",
        arrivalPort: "오사카",
        requestDate: "2025-07-26",
        cbm: 5.5,
        status: "rejected",
        myBidId: "bid-samsung-2",
        acceptedBidId: "bid-samsung-1"
    },
    // ... 기타 데이터
];
const bidsOnRequests = { // 각 요청에 대한 모든 입찰자 목록 (경쟁 포함)
    "my-offer-001": [
        { bidId: "bid-apple-1", companyName: "안전한물류", price: 180, currency: "USD" },
        { bidId: "bid-apple-2", companyName: "내회사(SeAirHub)", price: 165, currency: "USD", isMyBid: true }
    ],
    "my-offer-002": [
        { bidId: "bid-samsung-1", companyName: "월드와이드로지스틱스", price: 210, currency: "USD" },
        { bidId: "bid-samsung-2", companyName: "내회사(SeAirHub)", price: 225, currency: "USD", isMyBid: true }
    ],
};


// ------------------------------------------------------------------
// [영역 3: 나의 요청 조회 페이지 데이터]
// '나'의 판매 활동 내역: 내가 판매하기 위해 플랫폼에 내놓은 '운송 계약' 목록
// ------------------------------------------------------------------
const myPostedRequests = [
    {
        id: "my-posted-req-001",
        requestId: "SHIPPER-REQ-A01",
        itemName: "Hyundai Car Parts",
        incoterms: "FOB",
        departurePort: "부산",
        arrivalPort: "LA",
        requestDate: "2025-08-20",
        cbm: 13.4,
        tradeType: '수출',
        transportType: '해상',
        deadlineDate: "2025-08-10",
        deadlineTime: "23:59:59",
        status: "awaiting_shipment" // 나의 요청 상태: pending, awaiting_shipment, in_transit, closed
    },
    {
        id: "my-posted-req-002",
        requestId: "SHIPPER-REQ-B02",
        itemName: "Cosmetics Set",
        incoterms: "CIF",
        departurePort: "인천",
        arrivalPort: "상하이",
        requestDate: "2025-08-15",
        cbm: 7.6,
        tradeType: '수출',
        transportType: '해상',
        deadlineDate: "2025-08-05",
        deadlineTime: "12:00:00",
        status: "in_transit"
    },
    // ... 기타 데이터
];
const bidsForMyRequests = { // 나의 요청에 대한 다른 포워더들의 입찰 목록
    "my-posted-req-001": [
        { bidId: "bid-formp-1", company: "빠른물류", price: 1800, currency: "USD", rating: 4.5, status: 'accepted' },
        { bidId: "bid-formp-2", company: "안전한물류", price: 1850, currency: "USD", rating: 4.8, status: 'rejected' },
    ],
    "my-posted-req-002": [
        { bidId: "bid-formp-3", company: "월드와이드로지스틱스", price: 900, currency: "USD", rating: 4.2, status: 'accepted' },
    ],
};


// ------------------------------------------------------------------
// [영역 4: 컨테이너 조회 페이지 데이터]
// '나'의 컨테이너 자산 현황
// ------------------------------------------------------------------
const myContainersData = [
    {
        id: 'container-001',
        containerNumber: 'SEAU-123456-7',
        departurePort: '부산',
        arrivalPort: 'LA',
        // --- 공간 데이터 ---
        totalCapacity: 67, // 40ft
        confirmedCBM: 33.5, // 50% 내가 직접 운송
        registeringCBM: 13.4, // 20% 판매용으로 내놓음 (myPostedRequests의 req-001과 일치)
        biddingCBM: 8.5, // 12.6% 내가 다른 곳에 입찰함 (mockRequests의 req-002와 일치)
        // --- 기본 정보 ---
        size: '40ft', type: '일반', statusInitial: 'A',
        departureTime: '2025.08.10. AM 10:00', arrivalTime: '2025.08.25. PM 17:00',
        totalPrice: 1200000, pricePerCBM: 18000
    },
    {
        id: 'container-002',
        containerNumber: 'SEAU-987654-3',
        departurePort: '인천',
        arrivalPort: '도쿄',
        totalCapacity: 33, // 20ft
        confirmedCBM: 16.5, // 50%
        registeringCBM: 0, // 판매용 없음
        biddingCBM: 1.9, // 5.7% (myOffers의 offer-001과 일치)
        size: '20ft', type: '일반', statusInitial: 'K',
        departureTime: '2025.08.01. PM 19:00', arrivalTime: '2025.08.02. PM 18:00',
        totalPrice: 480000, pricePerCBM: 20000
    }
];