// 각 입력 항목의 유효성 검사 통과 여부를 저장하는 변수
let idCheck = false;
let emailCheck = false;
let pwdCheck = false;
let phoneCheck = false; // [추가] 전화번호 유효성 검사 상태 변수

$(function() {
    // 페이지 로드 시, 이메일 입력창이 읽기 전용(readonly)인지 확인
    if ($('#email').is('[readonly]')) {
        emailCheck = true;
        $('#confirmEmail').hide();
    }
    
    // 각 입력 필드에서 이벤트 발생 시 유효성 검사 함수 호출
    $('#userId').on('keyup', confirmId);
    $('#email').on('keyup', confirmEmail);
    $('#userPwd, #userPwdConfirm').on('keyup', confirmPassword);
    $('#userName').on('keyup', updateJoinButton);
    
    // [추가] 전화번호 입력 필드 이벤트 핸들러
    $('#phoneNum').on('input', function() {
        // 1. 입력된 값에서 숫자가 아닌 모든 문자를 제거
	    var sanitized = $(this).val().replace(/[^0-9]/g, '');
	    $(this).val(sanitized);

        // 2. 전화번호 유효성 검사 (예: 10자리 또는 11자리인지 확인)
        if (sanitized.length >= 10 && sanitized.length <= 11) {
            phoneCheck = true;
        } else {
            phoneCheck = false;
        }

        // 3. 버튼 상태 업데이트
        updateJoinButton();
	});
	
    // '회원가입' 버튼 클릭 시
    $('#joinBtn').on('click', function() {
        if (isAllCheck()) {
            $('#joinForm').submit();
        } else {
            alert('입력 정보를 다시 확인해주세요.');
        }
    });

    // 페이지가 처음 로드될 때 버튼 상태를 초기화
    updateJoinButton();
	
	
	// [추가] 커스텀 파일 첨부 UI를 위한 스크립트
	$('#businessLicenseFile').on('change', function() {
	       // 파일이 선택되었는지 확인
	       if (this.files.length > 0) {
	           // 선택된 파일의 이름을 가져옴
	           const fileName = this.files[0].name;
	           // 파일명을 .file-display 입력창에 표시
	           $('.file-display').val(fileName);
	       } else {
	           // 파일 선택이 취소된 경우
	           $('.file-display').val('선택된 파일 없음');
	       }
	   });
	
	
	
});


/**
 * 모든 필수 입력 및 검증 조건이 충족되었는지 확인하는 함수
 */
function isAllCheck() {
    const isNameFilled = $('#userName').val().trim() !== '';
    // [수정] phoneCheck 조건을 추가
    return idCheck && emailCheck && pwdCheck && phoneCheck && isNameFilled;
}


/**
 * 모든 조건의 충족 여부에 따라 '회원가입' 버튼의 활성화/비활성화 상태를 업데이트하는 함수
 */
function updateJoinButton() {
    if (isAllCheck()) {
        $('#joinBtn').prop('disabled', false).css({
            'background-color': 'var(--primary-color)',
            'cursor': 'pointer',
            'opacity': '1'
        });
    } else {
        $('#joinBtn').prop('disabled', true).css({
            'background-color': '#ccc',
            'cursor': 'not-allowed',
            'opacity': '0.6'
        });
    }
}


// 아이디 유효성 및 중복 검사
function confirmId() {
    // ... (기존 코드와 동일)
    let userId = $('#userId').val();
    
    if (userId.trim().length < 5 || userId.trim().length > 10) {
        $('#confirmId').css('color', 'red').text('아이디는 5~10자로 입력해주세요.');
        idCheck = false;
        updateJoinButton();
        return;
    }
    
    $.ajax({
        url: '/user/confirmId',
        method: 'POST',
        data: { "userId": userId },
        success: function(isAvailable) {
            if (isAvailable) {
                $('#confirmId').css('color', 'blue').text('사용 가능한 아이디입니다.');
                idCheck = true;
            } else {
                $('#confirmId').css('color', 'red').text('이미 사용 중인 아이디입니다.');
                idCheck = false;
            }
            updateJoinButton();
        },
        error: function() {
            $('#confirmId').css('color', 'red').text('오류가 발생했습니다.');
            idCheck = false;
            updateJoinButton();
        }
    });
}

// 이메일 유효성 및 중복 검사
function confirmEmail() {
    // ... (기존 코드와 동일)
    if ($('#email').is('[readonly]')) {
        return;
    }
	
    let email = $('#email').val();
    let emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    if (!emailRegex.test(email)) {
        $('#confirmEmail').css('color', 'red').text('올바른 이메일 형식이 아닙니다.');
        emailCheck = false;
        updateJoinButton();
        return;
    }
    
    $.ajax({
        url: '/user/confirmEmail',
        method: 'POST',
        data: { "email": email },
        success: function(isAvailable) {
            if (isAvailable) {
                $('#confirmEmail').css('color', 'blue').text('사용 가능한 이메일입니다.');
                emailCheck = true;
            } else {
                $('#confirmEmail').css('color', 'red').text('이미 사용 중인 이메일입니다.');
                emailCheck = false;
            }
            updateJoinButton();
        },
        error: function() {
            $('#confirmEmail').css('color', 'red').text('오류가 발생했습니다.');
            emailCheck = false;
            updateJoinButton();
        }
    });
}

// 비밀번호 일치 확인
function confirmPassword() {
    // ... (기존 코드와 동일)
    let userPwd = $('#userPwd').val();
    let userPwdConfirm = $('#userPwdConfirm').val();
    
    if (userPwd.length < 8) {
        $('#confirmPwd').css('color', 'red').text('비밀번호는 8자 이상이어야 합니다.');
        pwdCheck = false;
    } else if (userPwd === userPwdConfirm) {
        $('#confirmPwd').css('color', 'blue').text('비밀번호가 일치합니다.');
        pwdCheck = true;
    } else {
        $('#confirmPwd').css('color', 'red').text('비밀번호가 일치하지 않습니다.');
        pwdCheck = false;
    }
    updateJoinButton();
}